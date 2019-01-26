import IORedis from 'ioredis';

import { config } from './lib/config';

export function start_scraper() {
    let rdb = new IORedis(config.redis);
}

export function start_server() {
    let rdb = new IORedis(config.redis);
}

export function run() {
    start_scraper();
    start_server();
}

if(require.main == module) {
    run();
}