import * as _ from 'lodash';
import * as moment from 'moment';

import * as ioredis from 'ioredis';

import { Config, load_scraper_redis } from '../lib/config';
import * as redis from '../lib/redis';
import { generate_unique_id } from '../lib/util';

import * as ScraperDB from './db';

import * as StoreDB from '../lib/db';

export let rdb: ioredis.Redis|null = null;
export let storedb: StoreDB.DB|null = null;
export let config: any = null;

interface Task {
    name: string;
    module: string;
    exec: string;
    timeout: number;
    repeat?: moment.Duration;
    options?: any;
}

interface Call {
    runid: string;
    module: string;
    exec: string;
    skip_wait?: boolean;
    retry?: number;
    options?: any;
}

const BASE_TASKS: Task[] = [
    {
        name: 'load_gamelist',
        module: 'gamelist',
        exec: 'list_all_games',
        timeout: 300,
        repeat: moment.duration(7, 'days'),
    },
    {
        name: 'pull_latest_new_runs',
        module: 'latest-runs',
        exec: 'pull_latest_runs',
        timeout: 300,
        repeat: moment.duration(1, 'minutes'),
    },
    {
        name: 'pull_latest_verified_runs',
        module: 'latest-runs',
        exec: 'pull_latest_runs',
        timeout: 300,
        repeat: moment.duration(1, 'minutes'),
        options: {
            verified: true,
        },
    },
    {
        name: 'pull_all_runs',
        module: 'all-runs',
        exec: 'list_all_runs',
        timeout: 300,
        repeat: moment.duration(2, 'weeks'),
    },
    {
        name: 'hack_chart_total_runs',
        module: 'hack',
        exec: 'gen_total_runs',
        timeout: 14400,
        repeat: moment.duration(1, 'days'),
    },
    {
        name: 'hack_chart_volume',
        module: 'hack',
        exec: 'gen_volume',
        timeout: 14400,
        repeat: moment.duration(1, 'days'),
    },
];

export function join_runid(parts: string[]) {
    // prevent injection by remapping ':' character in case it somehow ends up in separator
    return parts.map((v) => v.replace(':', '_')).join('/');
}

const TEMPL_REPLACE = /{{\s*(.+?)\s*}}/g;

export function template_call(call: Call, obj: any) {

    const rpl = (v: string) => {
        const m = v.match(/^{{\s*(.+?)\s*}}$/);
        return _.get(obj, m![1]);
    };

    // do a fun regex function replace of the fields of the call
    call.runid = _.replace(call.runid, TEMPL_REPLACE, rpl);

    for (const option in call.options) {
        if (typeof call.options[option] == 'string') {
            call.options[option] = _.replace(call.options[option], TEMPL_REPLACE, rpl);
        }
    }

    // this is technically unnecessary because this function works in place
    return call;
}

export async function push_call(call: Call, priority: number) {
    console.log('[PUSHC]', call.module, call.exec, call.runid);

    await rdb!.multi()
        .rpush(`${ScraperDB.locs.callqueue}:${priority}`, ScraperDB.join([
            call.runid,
            call.module,
            call.exec,
            call.retry ? call.retry.toString() : '0',
            JSON.stringify(call.options) || '{}',
        ]))
        .exec();

}

async function do_call(call: Call, priority: number) {
    const loader = require(`./loaders/${call.module}`);

    try {
        return await loader[call.exec](call.runid, call.options);
    } catch (err) {
        // error handling is supposed to happen in the loaders, but if it is rethrown with a trigger string the task can be rescheduled.
        if (err == 'reschedule') {
            call.retry = call.retry ? call.retry + 1 : 1;

            if (call.retry > config.scraper.maxRetries) {
                console.error('[DROPC] Giving up (too many retries):', call);
                return;
            }

            await push_call(call, priority);
        } else {
            console.error('[DROPC]', call.runid, err);
        }
    }
}

async function pop_call(): Promise<any> {
    let rawc = null;

    // using custom command for redis stored in middleware/redis/iblpoprpush.lua
    // it does as the name implies.

    // there is technically a small chance that a job could be dropped here. but its unlikely
    // and we cannot use a script to go around this just yet. Could be something todo later!

    const ks = await rdb!.keys(ScraperDB.locs.callqueue + ':*');

    if (!ks.length) {
        return null;
    }

    let q_to_call = ks[0];

    for (const k of ks) {
        if (parseInt(q_to_call.split(':')[1]) > parseInt(k.split(':')[1])) {
            q_to_call = k;
        }
    }

    rawc = await rdb!.lpop(q_to_call);

    if (!rawc) {
        // retry pulling from the queue
        return await pop_call();
    }

    const priority = parseInt(q_to_call.split(':')[1]);
    const raw_running_task = ScraperDB.join([Date.now().toString(), rawc]);

    const d = rawc.split(':');

    const call: Call = {
        runid: d[0],
        module: d[1],
        exec: d[2],
        retry: parseInt(d[3]),
        options: JSON.parse(d.slice(4).join(':')),
    };

    await rdb!.multi()
        .rpush(ScraperDB.locs.running_tasks, raw_running_task)
        .exec();

    console.log('[POP C]', call.module, call.exec, call.runid);

    const ret = await do_call(call, priority);

    // when the call returns we can safely remove from running tasks
    await rdb!.lrem(ScraperDB.locs.running_tasks, 1, raw_running_task);

    if (call.skip_wait) {
        setTimeout(pop_call, 1);
    }

    return ret;
}

export function spawn_task(task: Task) {
    // scan currently enqueued tasks to ensure previous runid still does not exist
    const runid = generate_unique_id(ScraperDB.ID_LENGTH);

    const loader = require(`./loaders/${task.module}`);

    console.log('[SPAWN]', task.name);
    loader[task.exec](runid, task.options);

    return runid;
}

async function init_task(task: Task) {
    const runid = task.name + '/' + generate_unique_id(ScraperDB.ID_LENGTH);

    await push_call({
        runid,
        module: task.module,
        exec: task.exec,
        options: task.options,
    }, 0);

    await rdb!.hset(ScraperDB.locs.pending_tasks, task.name, ScraperDB.join([
        runid,
        moment().add(task.repeat).toISOString(),
    ]));

    if (task.repeat) {
        setTimeout(async () => {
            await init_task(task);
        }, task.repeat.asMilliseconds());
    }
}

// checks the running tasks array. if the task has been running too long, it gets popped back in as a task to retry later.
async function wipe_running_tasks() {

    try {
        const running_tasks = await rdb!.lrange(ScraperDB.locs.running_tasks, 0, -1);

        console.log('[WIPET]', running_tasks.length, 'running tasks found');

        for (const t of running_tasks) {
            const spl = t.split(':');
            const time = parseInt(spl.shift()!);

            if (time < Date.now() - config.scraper.runningTaskTimeout * 1000) {
                console.log('[WIPET]', t);
                await rdb!.multi()
                        .lrem(ScraperDB.locs.running_tasks, 1, t)
                        .lpush(ScraperDB.join([ScraperDB.locs.callqueue, '1']), spl.join(':'))
                        .exec();
            }
        }
    } catch (err) {
        console.error('Could not wipe:', err);
    }
}

export async function connect(conf: Config) {
    config = conf;
    rdb = load_scraper_redis(config);
    storedb = await StoreDB.load_db(config);

    redis.defineCommands(rdb);
}

export async function run(config: Config) {

    console.log('Start scraper');

    await connect(config);

    // spawn the base tasks
    // config can override which base tasks we use

    const baseTaskNames = config.scraper.baseTasks.length ? config.scraper.baseTasks : _.map(BASE_TASKS, 'name');

    console.log('Loading tasks:', baseTaskNames);

    for (const taskName of baseTaskNames) {
        const task = _.find(BASE_TASKS, (v) => v.name === taskName);

        if (!task) {
            continue;
        }

        const ptask = (await rdb!.hget(ScraperDB.locs.pending_tasks, task.name)) as string;

        if (ptask) {
            const ptaskParts = ptask.split(':');
            ptaskParts.shift(); // removes first (unneeded) argument
            const rtime = moment(ptaskParts.join(':'));

            if (rtime.isAfter(moment())) {
                // schedule for the time difference
                const sched_ms = rtime.diff(moment());
                console.log('[SCHED]', task.name, sched_ms);
                setTimeout(async () => {
                    await init_task(task as Task);
                }, sched_ms);
                continue;
            }
        }

        // if we make it this far, spawn right now. schedule for later
        await init_task(task);
    }

    // keep pulling calls at the given rate
    setInterval(pop_call, 1000.0 / config.scraper.rate);

    await wipe_running_tasks();
    setInterval(wipe_running_tasks, 60 * 1000);
}
