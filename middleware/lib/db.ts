import * as _ from 'lodash';

import * as ioredis from 'ioredis';
import * as mongodb from 'mongodb';

import { Config } from './config';

export interface DB {
    redis: ioredis.Redis;
    mongo: mongodb.Db;
    mongo_client: mongodb.MongoClient
}

export async function load_db(conf: Config): Promise<DB> {

    let db: any = {
        redis: new ioredis(_.merge(conf.db.redis, { lazyConnect: true })),
        mongo: null,
        mongo_client: (await mongodb.connect(conf.db.mongo.uri, _.merge(conf.db.mongo.options, {useNewUrlParser: true })))
    };

    db.mongo = db.mongo_client.db(conf.db.mongo.dbName);

    await db.redis.connect();

    return db;
}

export async function close_db(db: DB) {
    db.mongo_client.close();
    db.redis.disconnect();
}
