import * as fs from 'fs';
import * as path from 'path';

import * as _ from 'lodash';

import { RedisOptions } from 'ioredis';

export interface Config {
    listen: {
        /// Hostname to accept connections from. Use 0.0.0.0 for all interfaces.
        host: string

        /// Port for the API to accept connections from
        port: number
    },

    api: {
        /// Maximum number of individual elements to return in a single request
        maxItems: number

        /// Maximum number of characters which may be used to search for an autocomplete result
        maxSearchLength: number
    }

    /// Configure the DB connection
    redis: RedisOptions

    scraper: {
        /// How fast should the API scan for changes?
        rate: number

        /// The maximum number of times to repeat the same task before giving up
        maxRetries: number

        /// Override configuration options for the redis connection (for example, different DB index)
        redis: RedisOptions

        /// Number of seconds before a running task is considered stalled/lost
        runningTaskTimeout: number
    }

    /// Settings for the indexing engine--used for autocomplete and any title search
    indexer: {

        /// Options for the indexer
        config: {
            depth: number, // how many letters to match sequences
            spread: number, // extra sequences to generate to account for misspellings/errornous letters
            scoreLength: number // determines the maximum score understood by the indexer
        }

        /// Override configuration options for this redis connection
        redis: RedisOptions
    }
}

export const DEFAULT_CONFIG: Config = {
    listen: {
        host: '0.0.0.0',
        port: 3500
    },

    api: {
        maxItems: 30,
        maxSearchLength: 50
    },

    redis: {},

    scraper: {
        // number of calls / second
        rate: 1,

        maxRetries: 3,

        runningTaskTimeout: 300, // 5 minutes

        redis: {
            db: 1
        }
    },

    indexer: {
        config: {
            depth: 3,
            spread: 0,
            scoreLength: 6
        },

        redis: {
            db: 2
        }
    }
}

/// A list of places to look for configuration files. Later configurations override prior configs.
const CONFIG_LOCATIONS: (string|null)[] = [
    '/speedrunbrowser-middleware/config/config.json',
    path.join(process.cwd(), 'config.json'),
    path.join(process.cwd(), '../config.json'),
    process.env.CONFIG_FILE || null
];

export var config = DEFAULT_CONFIG;

export function load_config() {
    let config = DEFAULT_CONFIG;

    for(let loc of CONFIG_LOCATIONS) {

        if(loc == null)
            continue;

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