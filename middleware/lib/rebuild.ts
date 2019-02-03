import * as fs from 'fs';
import * as path from 'path';

import * as ioredis from 'ioredis';

let Indexer: any = require('@13013/indexer');

import { config } from './config';

export async function run() {
    let rdb = new ioredis(config.redis);
    let indx = new Indexer('games', config.indexer.config, config.indexer.redis);

    let update_modules = fs.readdirSync(path.join(__dirname, 'db-updates'));
    for(let update_module of update_modules) {
        let mod = require(path.join(__dirname, 'db-updates', update_module));

        console.log('Run: ', update_module);
        try {
            await mod.default(rdb, indx);
        }
        catch(err) {
            console.error('Failure in module: ', update_module);
            console.error(err);
        }
    }
}


if(module == require.main) {
    run();
}