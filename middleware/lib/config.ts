import fs from 'fs';
import path from 'path';

import _ from 'lodash';

import { RedisOptions } from 'ioredis';

export interface Config {
    listen: {
        /// Hostname to accept connections from. Use 0.0.0.0 for all interfaces.
        host: string,

        /// Port for the API to accept connections from
        port: number
    }

    /// Configure the DB connection
    redis: RedisOptions

    scraper: {
        /// How fast should the API scan for changes?
        rate: number

        ///
    }
}

export const DEFAULT_CONFIG: Config = {
    listen: {
        host: '0.0.0.0',
        port: 3500
    },

    redis: {},

    scraper: {
        rate: 1.0
    }
}

/// A list of places to look for configuration files. Later configurations override prior configs.
const CONFIG_LOCATIONS = [
    '/speedrunbrowser-middleware/config/config.json',
    path.join(process.cwd(), 'config.json'),
    path.join(process.cwd(), '../config.json'),
    process.env.CONFIG_FILE
];

export var config = DEFAULT_CONFIG;

export function load_config() {
    let config = DEFAULT_CONFIG;

    for(let loc of CONFIG_LOCATIONS) {
        if(fs.existsSync(loc)) {
            try {
                config = _.merge(config, require(loc));
                console.log('Loaded configuration:', loc);
            }
            catch(err) {
                // TODO: Might want to make sure this is actually "file not found"
            }
        }
    }

    return config;
}