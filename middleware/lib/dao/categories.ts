import * as _ from 'lodash';

import { DB } from '../db';

import { RedisMultiIndex } from './backing/redis';

import {
    UpstreamData,
    BaseMiddleware,
    Variable,
    normalize
} from '../speedrun-api';

import { Dao } from './';

export interface BulkCategory {
    id: string
    name: string
    type: string

    variables?: UpstreamData<Variable>|Variable[]
}

export interface Category extends BulkCategory, BaseMiddleware {
    weblink: string
    rules?: string
    miscellaneous: boolean
    game?: string;
}

export function category_to_bulk(category: Category): BulkCategory {
    let ret = _.pick(category, 'id', 'name', 'type', 'variables');

    if(category.variables) {
        for(let v of <Variable[]>category.variables) {
            // remove any rules embedded in the values which can be annoyingly large
            for(let val in v.values.values) {
                delete (<any>v.values.values)[val].rules;
            }
        }
    }

    return ret;
}

export function normalize_category(d: Category) {
    normalize(d);

    if(d.variables && (<UpstreamData<Variable>>d.variables).data) {
        d.variables = (<UpstreamData<Variable>>d.variables).data;
    }

    for(let variable in d.variables) {
        normalize((<any>d.variables)[variable]);
    }
}

export class CategoryDao extends Dao<Category> {
    constructor(db: DB) {
        super(db, 'categories', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new RedisMultiIndex('game', 'game')
        ];
    }

    protected async pre_store_transform(obj: Category): Promise<Category> {
        normalize(obj);
        return obj;
    }
}
