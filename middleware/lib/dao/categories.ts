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

    delete ret.variables;

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

export function standard_sort_categories(categories: Category[]) {
    // first, sort by some predefined names that should always be in the front
    return _.sortBy(categories, (c) => {
        let name = c.name.toLowerCase();

        let score = 0;

        if(name === 'any%')
            score = 0;
        else if(name.match(/^all.*/))
            score = 1;
        else if(name === 'low%')
            score = 2;
        else if(name === '100%')
            score = 3;
        else if(name.match(/^any%/))
            score = 4;
        else if(name.match(/^100%/))
            score = 5;
        else if(name.match(/%$/))
            score = 6;
        else if(name.match(/^\d+.*/))
            score = 1 + parseInt(name.match(/^(\d+).*/)![1]) / 1000000;
        else
            score = 7;

        if(c.type !== 'per-game')
            score += 100;
        if(c.miscellaneous)
            score += 500;

        return _.padStart(score.toFixed(6), 10, '0') + name;
    });
}

export class CategoryDao extends Dao<Category> {
    constructor(db: DB) {
        super(db, 'categories', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new RedisMultiIndex('game', 'game')
        ];
    }

    async apply_for_game(game_id: string, new_categories: Category[]) {
        let old_categories = await this.load_by_index('game', game_id);
        if(old_categories.length)
            await this.remove(_.map(old_categories, 'id'))

        await this.save(new_categories);
    }

    protected async pre_store_transform(obj: Category): Promise<Category> {
        normalize_category(obj);
        return obj;
    }
}
