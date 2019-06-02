import * as _ from 'lodash';
import * as assert from 'assert';

import { DB } from '../db';

export interface DaoConfig<T> {
    db: DB;
    collection: string;
    backing: 'redis'|'mongo'

    id_key: (obj: T) => string;

    save(objs: T|T[]): Promise<void>;
    load(ids: string|string[]): Promise<any[]>;
}

export interface IndexDriver<T> {
    name: string;

    /// retrieve objects using the given string index keys
    load(conf: DaoConfig<T>, keys: string[]): Promise<(T|null)[]>;

    /// sets indexes associated with the given objects. the objects have already been stored in the db.
    apply(conf: DaoConfig<T>, objs: T[]): Promise<void>;

    /// unset indexes associated with the given objects.
    clear(conf: DaoConfig<T>, objs: T[]): Promise<void>;

    /// indicates if the given objects generate different indexes, thereby requiring a refresh
    has_changed(old_obj: T, new_obj: T): boolean;
}

export class Dao<T> implements DaoConfig<T> {
    db: DB;
    collection: string;
    backing: "redis" | "mongo";
    indexes: IndexDriver<T>[] = [];
    id_key: (obj: T) => string = _.property('id');

    constructor(db: DB, collection: string, backing?: 'redis'|'mongo') {
        this.collection = collection;
        this.db = db;
        this.backing = backing || 'redis';
    }

    async save(objs: T|T[]) {
        if(!_.isArray(objs))
            objs = [objs];

        // keep a copy of the previous values for index processing
        let prev_objs = await require(`./backing/${this.backing}`).save(this, objs);

        assert.equal(objs.length, prev_objs.length,
            'previous objects should be same length and mappable to new objs');

        // process indexes
        // get a list of deleted and inserted indexes
        // updates will trigger both a delete and insert, whereas inserts will only trigger an insert
        for(let idx of this.indexes) {
            // updated objects can be seen by a change in the key_by property
            let insert_indexes_objs = [], remove_previous_index_objs = [];
            for(let i = 0;i < objs.length;i++) {
                // new objects are indicated by null in prev_objs returned values
                if(_.isNil(prev_objs[i]))
                    insert_indexes_objs.push(objs[i]);
                else if(idx.has_changed(prev_objs[i], objs[i])) {
                    remove_previous_index_objs.push(prev_objs[i]);
                    insert_indexes_objs.push(objs[i]);
                }
            }

            if(remove_previous_index_objs.length)
                await idx.clear(this, remove_previous_index_objs);
            if(insert_indexes_objs.length)
                await idx.apply(this, insert_indexes_objs);
        }
    }

    async load(ids: string|string[]): Promise<(T|null)[]> {
        if(!_.isArray(ids))
            ids = [ids];

        return await require(`./backing/${this.backing}`).load(this, ids);
    }

    async remove(ids: string|string[]): Promise<(T|null)[]> {
        if(!_.isArray(ids))
            ids = [ids];

        if(this.indexes.length) {
            let objs = <T[]>_.reject(await this.load(ids), _.isNil);

            // first delete indexes
            for(let idx of this.indexes) {
                await idx.clear(this, objs);
            }
        }

        // now delete actual obj
        return await require(`./backing/${this.backing}`).remove(this, ids);
    }

    async load_by_index(index: string, vals: string|string[]): Promise<(T|null)[]> {
        if(!_.isArray(vals))
            vals = [vals];

        let idx = _.find(this.indexes, v => v.name == index);

        if(!idx)
            throw `Undefined Index: ${index}`;

        return await idx.load(this, vals);
    }
}
