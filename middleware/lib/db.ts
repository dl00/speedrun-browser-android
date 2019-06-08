import * as _ from 'lodash';

import * as ioredis from 'ioredis';
import * as mongodb from 'mongodb';

import { Config } from './config';

const CURRENT_INDEXERS = ['games', 'players', 'genres'];

let Indexer = require('@13013/indexer');

export interface DB {
    redis: ioredis.Redis;
    mongo: mongodb.Db;

    indexers: {[key: string]: any};

    batch?: {
        redis: ioredis.Pipeline;
        mongo: mongodb.Db;
        mongo_session?: mongodb.ClientSession;
    }

    mongo_client: mongodb.MongoClient
}

export async function start_transaction(db: DB) {
    if(db.batch)
        throw new Error('Cannot start transaction: transaction already in progress');

    db.batch = {
        mongo: db.mongo,
        redis: db.redis.multi()
    };
}

export async function exec_transaction(db: DB) {
    if(!db.batch)
        throw new Error('Cannot end transaction: transaction must already be in progress');

    //await db.batch.mongo_session!.commitTransaction();
    await db.batch.redis.exec();

    delete db.batch;
}

export function load_indexer(config: Config, name: string) {
    return new Indexer(name, config.indexer.config, _.defaults(config.indexer.redis, config.db.redis));
}

export async function load_db(conf: Config): Promise<DB> {

    let db: any = {
        redis: new ioredis(_.merge(conf.db.redis, { lazyConnect: true })),
        mongo: null,
        mongo_client: (await mongodb.connect(conf.db.mongo.uri, _.merge(conf.db.mongo.options, {useNewUrlParser: true }))),
        indexers: {}
    };

    db.mongo = db.mongo_client.db(conf.db.mongo.dbName);

    await db.redis.connect();

    for(let ind of CURRENT_INDEXERS) {
        db.indexers[ind] = load_indexer(conf, ind);
    }

    return db;
}

export async function close_db(db: DB) {
    db.mongo_client.close();
    db.redis.disconnect();

    for(let ind in db.indexers)
        // TODO: fix indexer so we don't have to call the private "rdb" directly
        db.indexers[ind].rdb.disconnect();
}
