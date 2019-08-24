import { load_config } from './lib/config';

import * as scraper from './scraper';
import * as api from './api';

import * as cluster from 'cluster';

export async function start_scraper(config: any) {
    await scraper.run(config);
}

export async function start_server(config: any) {
    await api.run(config);
}

export async function run() {
    let config = load_config();

    if(cluster.isMaster) {
        cluster.fork({ROLE: 'scraper'});
        cluster.fork({ROLE: 'web'});
    }
    else if(process.env.ROLE == 'scraper') {
        await start_scraper(config);
    }
    else if(process.env.ROLE == 'web') {
        await start_server(config);
    }
}

if(require.main == module) {
    run().then(() => {
        console.log('Startup completed');
    }, console.error);
}
