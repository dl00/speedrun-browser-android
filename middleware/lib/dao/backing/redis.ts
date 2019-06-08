
import * as _ from 'lodash';

import { DaoConfig, IndexDriver } from '../';

export async function save(conf: DaoConfig<any>, objs: any[]) {
    let ids = _.map(objs, conf.id_key);

    let set_vals = <any>_.chain(objs)
        .keyBy(conf.id_key)
        .mapValues(JSON.stringify)
        .toPairs()
        .flatten()
        .value();

    let r = await conf.db.redis.multi()
        .hmget(conf.collection, ...ids)
        .hmset(conf.collection, ...set_vals)
        .exec();

    return r[0][1].map(JSON.parse);
}

export async function load(conf: DaoConfig<any>, ids: string[]) {
    let raw_vals = await conf.db.redis.hmget(conf.collection, ...ids);
    return <any[]>_.map(raw_vals, JSON.parse);
}

export async function remove(conf: DaoConfig<any>, ids: string[]) {
    await conf.db.redis.hdel(conf.collection, ...ids);
}

export class RedisMapIndex<T> implements IndexDriver<T> {
    name: string;
    key_by: ((obj: T) => string)|string;

    constructor(name: string, key_by: ((obj: T) => string)|string) {
        this.name = name;
        this.key_by = key_by;
    }

    async load(conf: DaoConfig<T>, keys: string[]): Promise<(T|null)[]> {
        let mapIds: string[] = await conf.db.redis.hmget(`${conf.collection}:${this.name}`, ...keys);
        return await conf.load(mapIds);
    }

    async apply(conf: DaoConfig<T>, objs: T[]) {
        let set_vals = <any>_.chain(objs)
            .keyBy(this.key_by)
            .mapValues(conf.id_key)
            .toPairs()
            .flatten()
            .value();

        await conf.db.redis.hmset(`${conf.collection}:${this.name}`, ...set_vals);
    }

    async clear(conf: DaoConfig<T>, objs: T[]) {
        let keys = _.map(objs, <(obj: T) => string>this.key_by);

        await conf.db.redis.hdel(`${conf.collection}:${this.name}`,
            ...keys);
    }

    has_changed(old_obj: T, new_obj: T): boolean {
        if(_.isString(this.key_by))
            return _.get(old_obj, this.key_by) != _.get(new_obj, this.key_by);
        else
            return this.key_by(old_obj) != this.key_by(new_obj);
    }
}

export class RedisMultiIndex<T> implements IndexDriver<T> {
    name: string;
    key_by: ((obj: T) => string)|string;

    constructor(name: string, key_by: ((obj: T) => string)|string) {
        this.name = name;
        this.key_by = key_by;
    }

    async load(conf: DaoConfig<T>, keys: string[]): Promise<(T|null)[]> {
        if(keys.length !== 1)
            throw new Error('RedisMultiIndex expects to only work with a single key');

        let mapIds: string[] = await conf.db.redis.smembers(`${conf.collection}:${this.name}:${keys[0]}`);

        if(!mapIds.length)
            return [];

        return await conf.load(mapIds);
    }

    async apply(conf: DaoConfig<T>, objs: T[]) {
        let m = await conf.db.redis.multi();

        for(let obj of objs) {
            let key = _.isFunction(this.key_by) ? this.key_by(obj) : _.get(obj, this.key_by);
            m.sadd(`${conf.collection}:${this.name}:${key}`, conf.id_key(obj));
        }

        await m.exec();
    }

    async clear(conf: DaoConfig<T>, objs: T[]) {
        let m = await conf.db.redis.multi();

        for(let obj of objs) {
            let key = _.isFunction(this.key_by) ? this.key_by(obj) : _.get(obj, this.key_by);
            m.srem(`${conf.collection}:${this.name}:${key}`, conf.id_key(obj));
        }

        await m.exec();
    }

    has_changed(old_obj: T, new_obj: T): boolean {
        if(_.isString(this.key_by))
            return _.get(old_obj, this.key_by) != _.get(new_obj, this.key_by);
        else
            return this.key_by(old_obj) != this.key_by(new_obj);
    }
}
