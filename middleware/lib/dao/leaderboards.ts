import * as _ from 'lodash';

import { Dao } from './';

import { MongoMultiIndex } from './backing/mongo';

import { DB } from '../db';

import { BaseMiddleware, Variable, normalize } from '../speedrun-api';

import { LeaderboardRunEntry, Run, run_to_bulk } from './runs';
import { Game } from './games';
import { Category } from './categories';
import { User } from './users';

export interface Leaderboard extends BaseMiddleware {
    weblink: string
    game: Game|string
    category: Category|string
    level?: string
    platform?: string
    region?: string
    emulators?: string
    'video-only'?: boolean
    runs: LeaderboardRunEntry[]

    players: {[id: string]: User}|{data: User[]}
}

// leaderboards can have subcategories. correct the places returned by the speedrun
// api to take these subcategories into account
export function correct_leaderboard_run_places(d: Leaderboard, vars: Variable[]) {

    let subcategory_vars = _.filter(vars, 'is-subcategory');

    let last_places: {[key: string]: number} = {};

    if(d.runs) {
        for(let run of d.runs) {
            let subcategory_id = '';

            for(let v of subcategory_vars) {
                subcategory_id += run.run.values[v.id];
            }

            last_places[subcategory_id] = last_places[subcategory_id] ?
                last_places[subcategory_id] + 1 : 1;

            run.place = last_places[subcategory_id];
        }
    }
}

export function normalize_leaderboard(d: Leaderboard) {

    normalize(d);

    if(d.runs) {
        d.runs.map((v) => {
            v.run = run_to_bulk(<Run>v.run);

            return v;
        });
    }
    else
        d.runs = [];

    delete d.players;

    // make sure we are not harboring an empty object in level, that will break the app
    if(!_.isString(d.level))
        delete d.level;
}

export class LeaderboardDao extends Dao<Leaderboard> {
    constructor(db: DB) {
        super(db, 'leaderboards', 'mongo');

        this.id_key = (lb: Leaderboard) => lb.category + (lb.level ? '_' + lb.level : '');

        this.indexes = [
            new MongoMultiIndex('game', 'game')
        ];
    }

    protected async pre_store_transform(leaderboard: Leaderboard): Promise<Leaderboard> {
        normalize_leaderboard(leaderboard);
        return leaderboard;
    }
}
