import { config } from './lib/config';

import * as scraper from './scraper';
import * as api from './api';

export function start_scraper() {
    scraper.run(config);
}

export function start_server() {
    api.run(config);
}

export function run() {
    config.load_config();
    
    start_scraper();
    start_server();
}

if(require.main == module) {
    run();
}