import Promise from 'bluebird';
global.Promise = Promise

import { load_config } from './lib/config';

import * as api from './api';
import { Sched } from './sched';

import * as cluster from 'cluster';

export async function start_sched(config: any) {
    const sched = new Sched(config);
    await sched.exec();
}

export async function start_server(config: any) {
    await api.run(config);
}

export async function run() {
    const config = load_config();

    if (cluster.isMaster) {
        cluster.fork({ROLE: 'scraper'});
        cluster.fork({ROLE: 'web'});
    } else if (process.env.ROLE == 'scraper') {
        await start_sched(config);
    } else if (process.env.ROLE == 'web') {
        await start_server(config);
    }
}

if (require.main == module) {
    run().then(() => {
        console.log('Startup completed');
    }, console.error);
}
