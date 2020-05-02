import * as fs from 'fs';
import * as path from 'path';

import * as _ from 'lodash';

import * as ioredis from 'ioredis';
import { SchedConfig } from '../sched';
import { DBConfig } from './db';

export interface Config {

    stackName: string;

    listen: {
        /// Hostname to accept connections from. Use 0.0.0.0 for all interfaces.
        host: string

        /// Port for the API to accept connections from
        port: number,
    };

    api: {
        /// Maximum number of individual elements to return in a single request
        maxItems: number

        /// Maximum number of characters which may be used to search for an autocomplete result
        maxSearchLength: number,
    };

    /// Configure the DB connection
    db: DBConfig;

    sched: SchedConfig;

    scraper: {
        /// How fast should the API scan for changes?
        rate: number

        /// The maximum number of times to repeat the same task before giving up
        maxRetries: number

        /// Override configuration options for the redis connection (for example, different DB index)
        redis: ioredis.RedisOptions

        /// Number of seconds before a running task is considered stalled/lost
        runningTaskTimeout: number

        /// Settings for dispatching push notification
        pushNotify: {
            /// Whether or not to send out push notifications
            enabled: boolean

            /// Authentication JSON file as provided by firebase
            credentialFile: string,
        }

        /// list of database update tasks which should be used by the downloader. empty means run all tasks
        baseTasks: string[]

        /// Settings for storing data in the database
        db: {
            /// The number of runs to keep as marked "latest"
            latestRunsLength: number

            /// The number of gnere-specific runs to keep marked as "latest"
            latestGenreRunsLength: number,
        },

        /// API Token needed to access the twitch API
        twitch: {
            /// api token from creating a new app on twitch developer page
            token: string,
            
            /// server secret, as given from the developer api page after you create the token
            secret: string
        }
    };
}

export const DEFAULT_CONFIG: Config = {

    stackName: 'dev',

    listen: {
        host: '0.0.0.0',
        port: 3500,
    },

    api: {
        maxItems: 40,
        maxSearchLength: 50,
    },

    db: {
        redis: {},
        mongo: {
            uri: 'mongodb://localhost:27017',
            dbName: 'srbrowser',
        },
        indexers: {
            games: {
                config: {
                    depth: 3,
                    spread: 0,
                    scoreLength: 6,
                },
        
                redis: {
                    db: 2,
                }
            },
            players: {
                config: {
                    depth: 3,
                    spread: 0,
                    scoreLength: 6,
                },
        
                redis: {
                    db: 2,
                }
            },
            game_groups: {
                config: {
                    depth: 3,
                    spread: 0,
                    scoreLength: 6,
                },
        
                redis: {
                    db: 2,
                }
            }
        }
    },

    sched: {
        db: {
            redis: {},
            mongo: {
                uri: 'mongodb://localhost:27017',
                dbName: 'srbrowser',
            },
            indexers: {}
        },

        resources: {
            src: {
                name: 'src',
                rateLimit: 1000
            },
            twitch: {
                name: 'twitch',
                rateLimit: 75
            },
            local: {
                name: 'local',
                rateLimit: 0
            }
        },

        generators: {},
        tasks: {},

        jobs: {
            
        }
    },

    scraper: {
        // number of calls / second
        rate: 1,

        maxRetries: 3,

        runningTaskTimeout: 3600, // 1 hour

        redis: {
            db: 1,
        },

        baseTasks: [],

        pushNotify: {
            enabled: false,
            credentialFile: '/speedrunbrowser-middleware/secrets/firebase-service-account.json',
        },

        db: {
            latestRunsLength: 10000,
            latestGenreRunsLength: 1000,
        },

        twitch: {
            token: '',
            secret: ''
        }
    }
};

/// A list of places to look for configuration files. Later configurations override prior configs.
const CONFIG_LOCATIONS: Array<string|null> = [
    '/speedrunbrowser-middleware/config/config.json',
    path.join(process.cwd(), 'config.json'),
    path.join(process.cwd(), '../config.json'),
    process.env.CONFIG_FILE || null,
];

export let config = DEFAULT_CONFIG;

export let load_config = _.memoize(() => {
    let config = DEFAULT_CONFIG;

    for (const loc of CONFIG_LOCATIONS) {

        if (loc == null) {
            continue;
        }

        if (fs.existsSync(loc)) {
            try {
                config = _.merge(config, require(loc));
                console.log('Loaded configuration:', loc);
            } catch (err) {
                // TODO: Might want to make sure this is actually "file not found"
            }
        }
    }

    return config;
});

export function load_scraper_redis(config: Config) {
    return new ioredis(_.defaults(config.scraper.redis, config.db.redis));
}
