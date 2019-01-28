import { config } from './lib/config';

import * as scraper from './scraper';

export function start_scraper() {
    scraper.run(config);
}

export function start_server() {
}

export function run() {
    start_scraper();
    start_server();
}

if(require.main == module) {
    run();
}