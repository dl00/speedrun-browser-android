import * as fs from 'fs';
import * as path from 'path';

import * as express from 'express';

import * as ioredis from 'ioredis';

import { Config } from '../lib/config';

export var storedb: ioredis.Redis|null = null;
export var config: Config|null = null;

export function create_express_server() {
    let app = express();

    // add middlewares

    return app;
}

export function run(conf: Config) {

    console.log('Start API');

    config = conf;

    // load storedb
    storedb = new ioredis(config!.redis);

    // create an express server
    const app = create_express_server();

    // load endpoints
    let endpoint_modules = fs.readdirSync(path.join(__dirname, 'endpoints'));
    let loaded: {[name: string]: express.Router} = {};
    for(let endpoint_module of endpoint_modules) {
        let name = endpoint_module.split('.')[0];
        if(loaded[name])
            continue;

        console.log('Load endpoint:', '/' + name);
        loaded[name] = require(path.join(__dirname, 'endpoints', name));
        app.use('/api/v1/' + name, loaded[name]);
    }
    
    app.listen(config!.listen, () => {
        console.log('API Ready', config!.listen);
    });
}