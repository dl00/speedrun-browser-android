import { load_config } from './lib/config';

import * as scraper from './scraper';
import * as api from './api';

export function start_scraper(config: any) {
    scraper.run(config);
}

export function start_server(config: any) {
    api.run(config);
}

export async function run() {
    let config = load_config();

    start_scraper(config);
    start_server(config);
}

if(require.main == module) {
    run().then(() => {
        console.log('Startup completed');
    });
}
