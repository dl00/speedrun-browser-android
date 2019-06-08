import * as _ from 'lodash';

import { DB } from '../db';

import {
    BaseMiddleware, normalize,
} from '../speedrun-api';

import { Dao } from './';

export interface BulkLevel {
    id: string
    name: string
}

export interface Level extends BulkLevel, BaseMiddleware {}

export function level_to_bulk(level: Level): BulkLevel {
    return _.pick(level, 'id', 'name');
}

export class LevelDao extends Dao<Level> {
    constructor(db: DB) {
        super(db, 'levels', 'redis');

        this.id_key = _.property('id');

        this.indexes = [

        ];
    }

    protected async pre_store_transform(level: Level): Promise<Level> {
        normalize(level);
        return level;
    }
}
