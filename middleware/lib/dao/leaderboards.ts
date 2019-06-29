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

export function add_leaderboard_run(d: Leaderboard, run: Run, vars: Variable[]): LeaderboardRunEntry {

    let lbe: LeaderboardRunEntry = { run: run };

    if(!d.runs)
        d.runs = [];

    let run_idx = _.sortedIndexBy(d.runs, lbe, v => v.run.times.primary_t)

    let subcategory_var_ids = _.map(_.filter(vars, 'is-subcategory'), 'id');

    // check for existing role with the same player and parameters
    let existing_idx = _.findIndex(d.runs, v => {
        return _.isEqual(run.players, v.run.players) &&
        _.isEqual(
            _.pick(run.values, subcategory_var_ids),
            _.pick(v.run.values, subcategory_var_ids)
        );
    });

    if(existing_idx != -1 && d.runs[existing_idx].run.times.primary_t < run.times.primary_t)
        return { run: run }; // has no place on this leaderboard because its obsolete

    // only add verified runs
    if(lbe.run.status['verify-date']) {
        if(existing_idx !== -1)
            d.runs.splice(existing_idx, 1);

        d.runs.splice(run_idx, 0, lbe);

        correct_leaderboard_run_places(d, vars);
        return d.runs[run_idx];
    }
    else {
        // calculate what place the run *would* be in
        lbe.place = run_idx > 0 ? d.runs[run_idx - 1].place : 1;
        if(run_idx > 0 && d.runs[run_idx - 1].run.times.primary_t !== lbe.run.times.primary_t)
            lbe.place!++;

        return lbe;
    }
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
