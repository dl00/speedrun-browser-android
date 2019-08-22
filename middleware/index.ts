import { load_config } from './lib/config';

import * as scraper from './scraper';
import * as api from './api';

import * as cluster from 'cluster';

export function start_scraper(config: any) {
    scraper.run(config);
}

export function start_server(config: any) {
    api.run(config);
}

export async function run() {
    let config = load_config();

    if(cluster.isMaster) {
        cluster.fork({ROLE: 'scraper'});
        cluster.fork({ROLE: 'web'});
    }
    else if(process.env.ROLE == 'scraper') {
        start_scraper(config);
    }
    else if(process.env.ROLE == 'web') {
        start_server(config);
    }
}

if(require.main == module) {
    run().then(() => {
        console.log('Startup completed');
    });
}
