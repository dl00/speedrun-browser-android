import * as _ from 'lodash';
import * as moment from 'moment';

let Indexer: any = require('@13013/indexer');

import * as ioredis from 'ioredis';

import { Config } from '../lib/config';
import { generate_unique_id } from '../lib/util';
import * as redis from '../lib/redis';

import * as DB from './db';

export let rdb: ioredis.Redis|null = null;
export let storedb: ioredis.Redis|null = null;
export let indexer: any|null = null;
export let config: any = null;

interface Task {
    name: string
    module: string
    exec: string
    repeat?: moment.Duration
    options?: any
}

interface Call {
    runid: string
    module: string
    exec: string
    skip_wait?: boolean
    retry?: number
    options?: any
}

const BASE_TASKS: Task[] = [
    {
        name: 'load_gamelist',
        module: 'gamelist',
        exec: 'list_all_games',
        repeat: moment.duration(7, 'days')
    }
];

export function join_runid(parts: string[]) {
    // prevent injection by remapping ':' character in case it somehow ends up in separator
    return parts.map(v => v.replace(':', '_')).join('/');
}

const TEMPL_REPLACE = /{{\s*(.+?)\s*}}/g;

export function template_call(call: Call, obj: any) {

    let rpl = (v: string) => {
        let m = v.match(/^{{\s*(.+?)\s*}}$/);
        return _.get(obj, m![1]);
    }

    // do a fun regex function replace of the fields of the call
    call.runid = _.replace(call.runid, TEMPL_REPLACE, rpl);
    
    for(let option in call.options) {
        if(typeof call.options[option] == 'string')
            call.options[option] = _.replace(call.options[option], TEMPL_REPLACE, rpl);
    }

    // this is technically unnecessary because this function works in place
    return call;
}

export async function push_call(call: Call, priority: number) {
    console.log('[PUSHC]', call.module, call.exec, call.runid);

    await rdb!.rpush(`${DB.locs.callqueue}:${priority}`, DB.join([
        call.runid,
        call.module,
        call.exec,
        JSON.stringify(call.options) || '{}'
    ]));
}

async function do_call(call: Call) {
    let loader = require(`./loaders/${call.module}`);

    try {
        return await loader[call.exec](call.runid, call.options);
    }
    catch(err) {
        // error handling is supposed to happen in the loaders, but if it is rethrown with a trigger string the task can be rescheduled.
        if(err == 'reschedule') {
            call.retry = call.retry ? call.retry + 1 : 1;

            if(call.retry > config.scraper.maxRetries) {
                console.error('[DROPC] Giving up (too many retries):', call);
                return;
            }

            await push_call(call, 15);
        }
    }
}


// arguments for later passing to the iblpushrpop command
const LISTEN_CMD: string[] = [];
for(let i = 0;i < 20;i++) {
    LISTEN_CMD.push(DB.join([DB.locs.callqueue, i.toString()]))
}

// timeout: do not block for more than a second!
LISTEN_CMD.push('1');

async function pop_call() {
    let rawc = null;

    // using custom command for redis stored in middleware/redis/iblpoprpush.lua
    // it does as the name implies.

    // there is technically a small chance that a job could be dropped here. but its unlikely
    // and we cannot use a script to go around this just yet. Could be something todo later!
    rawc = await rdb!.blpop.apply(rdb, LISTEN_CMD);

    if(!rawc)
        return null;
    
    let raw_running_task = DB.join([Date.now(), rawc[1]]);
    
    await rdb!.rpush(DB.locs.running_tasks, raw_running_task);

    let d = rawc[1].split(':');

    let call: Call = {
        runid: d[0],
        module: d[1],
        exec: d[2],
        options: JSON.parse(d.slice(3).join(':'))
    };
    console.log('[POP C]', call.module, call.exec, call.runid);

    let ret = await do_call(call);

    // when the call returns we can safely remove from running tasks
    rdb!.lrem(DB.locs.running_tasks, 1, raw_running_task);

    if(call.skip_wait)
        setTimeout(pop_call, 1);

    return ret;
}

export function spawn_task(task: Task) {
    // scan currently enqueued tasks to ensure previous runid still does not exist
    let runid = generate_unique_id(DB.ID_LENGTH);

    let loader = require(`./loaders/${task.module}`);

    console.log('[SPAWN]', task.name);
    loader[task.exec](runid, task.options);

    return runid;
}

async function init_task(task: Task) {

    let runid = generate_unique_id(DB.ID_LENGTH)

    await push_call({
        runid: runid,
        module: task.module,
        exec: task.exec,
        options: task.options
    }, 0);

    await rdb!.hset(DB.locs.pending_tasks, task.name, DB.join([
        runid,
        moment().add(task.repeat).toISOString()
    ]));
    
    if(task.repeat) {
        setInterval(async () => {
            init_task(task);
        }, task.repeat.asMilliseconds());
    }
}

// checks the running tasks array. if the task has been running too long, it gets popped back in as a task to retry later.
async function wipe_running_tasks() {

    try {
        let running_tasks = await rdb!.lrange(DB.locs.running_tasks, 0, -1);

        console.log('[WIPET]', running_tasks.length, 'running tasks found');

        for(let t of running_tasks) {
            let spl = t.split(':');
            let time = spl.shift();

            if(time < Date.now() - config.scraper.runningTaskTimeout * 1000) {
                console.log('[WIPET]', t);
                await rdb!.multi()
                        .lrem(DB.locs.running_tasks, 1, t)
                        .lpush(DB.join([DB.locs.callqueue, '1']), spl.join(':'))
                        .exec();
            }
        }
    } catch(err) {
        console.error('Could not wipe:', err);
    }
}

export async function connect(conf: Config) {
    config = conf;
    rdb = new ioredis(_.defaults(config.scraper.redis, config.redis));
    storedb = new ioredis(config.redis);

    indexer = new Indexer('games', config.indexer.config, config.indexer.redis);

    redis.defineCommands(rdb);
}

export async function run(config: Config) {

    console.log('Start scraper');

    await connect(config);

    // spawn the base tasks
    for(let task of BASE_TASKS) {
        let ptask = <string>(await rdb!.hget(DB.locs.pending_tasks, task.name));

        if(ptask) {
            let ptaskParts = ptask.split(':');
            let rtime = moment(ptaskParts[1]);


            if(rtime.isAfter(moment())) {
                // schedule for the time difference
                console.log('[SCHED]', task.name, rtime.diff(moment()));
                setTimeout(() => {
                    init_task(task);
                }, rtime.diff(moment()));
                continue;
            }
        }

        // if we make it this far, spawn right now. schedule for later
        init_task(task);
    }

    // keep pulling calls at the given rate
    setInterval(pop_call, 1000.0 / config.scraper.rate);

    wipe_running_tasks();
    setInterval(wipe_running_tasks, 60 * 1000);
}