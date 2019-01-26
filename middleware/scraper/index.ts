import _ from 'lodash';
import moment from 'moment';

import ioredis from 'ioredis';

import { Config } from '../lib/config';

import { generate_unique_id } from '../lib/util';

import * as DB from './db';

let rdb: ioredis.Redis|null = null;

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
    options?: any
}

const BASE_TASKS: Task[] = [
    {
        name: 'load_gamelist',
        module: 'gamelist',
        exec: 'load',
        repeat: moment.duration(1, 'day')
    }
];

export async function push_call(call: Call) {
    rdb.rpush(DB.locs.callqueue, DB.join([
        call.runid,
        call.module,
        call.exec,
        JSON.stringify(call.options)
    ]));
}

async function do_call(call: Call) {
    let loader = require(`./lib/${call.module}`);

    return await loader[call.exec](call.runid, call.module);
}

async function pop_call() {
    let d = (await rdb.lpop(DB.locs.callqueue)).split(':');

    let call: Call = {
        runid: d[0],
        module: d[1],
        exec: d[2],
        options: JSON.parse(d[3])
    };

    await do_call(call);
}

export function spawn_task(task: Task) {
    // scan currently enqueued tasks to ensure previous runid still does not exist
    let runid = generate_unique_id(DB.ID_LENGTH);

    let loader = require(`./loaders/${task}`);

    loader(runid, task.options);

    return runid;
}

async function init_task(task: Task) {
    await push_call({
        runid: generate_unique_id(DB.ID_LENGTH),
        module: task.module,
        exec: task.exec,
        options: task.options
    })
    spawn_task(task);
    
    if(task.repeat) {
        setInterval(() => {
            let runid = spawn_task(task);
            rdb.hset(DB.locs.pending_tasks, task.name, DB.join([
                runid,
                moment().add(task.repeat).toString()
            ]));
        }, task.repeat.milliseconds());
    }
}

export async function run(config: Config, redis_db: ioredis.Redis) {

    rdb = redis_db;

    // spawn the base tasks
    for(let task of BASE_TASKS) {
        let rtime = moment(await rdb.hget(DB.locs.pending_tasks, task.name));

        if(!rtime || rtime.isBefore(moment())) {
            // spawn right now. schedule for later
            init_task(task);
        }
        else {
            // schedule for the time difference
            setTimeout(() => {
                init_task(task);
            }, rtime.diff(moment()));
        }
    }

    // keep pulling calls at the given rate
    setInterval(pop_call, 1000 * config.scraper.rate);
}