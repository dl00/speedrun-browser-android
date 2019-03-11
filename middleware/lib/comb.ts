import * as _ from 'lodash';

import * as fs from 'fs';
import * as path from 'path';

import * as ioredis from 'ioredis';

import { load_config } from './config';

export async function run_single(name: string) {
    let config = load_config();

    let rdb = new ioredis(config.redis);

    let mod = require(path.join(__dirname, 'db-comb', name));

    console.log('Run: ', name);
    try {
        await mod.default(rdb, config);
    }
    catch(err) {
        console.error('Failure in module: ', name);
        console.error(err);
    }
}

export async function run() {
    console.log('[COMB] Start');

    let update_modules = fs.readdirSync(path.join(__dirname, 'db-comb'));
    for(let update_module of update_modules) {
        run_single(update_module);
    }

    console.log('[COMB] Stop');
}

async function run_and_quit() {
    if(process.argv.length > 2)
        await run_single(process.argv[2]);
    else
        await run();
    process.exit(0);
}

if(module == require.main) {
    run_and_quit();
}
