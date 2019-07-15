import * as _ from 'lodash';

import { Dao } from './';

import { MongoMultiIndex } from './backing/mongo';

import { DB } from '../db';

import { BaseMiddleware, Variable, normalize } from '../speedrun-api';

import { LeaderboardRunEntry, Run, run_to_bulk } from './runs';
import { Game } from './games';
import { Category } from './categories';
import { User } from './users';

import { Chart } from './charts';

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
        return _.isEqual(_.map(run.players, 'id').sort(), _.map(v.run.players, 'id').sort()) &&
        _.isEqual(
            _.pick(run.values, subcategory_var_ids),
            _.pick(v.run.values, subcategory_var_ids)
        );
    });

    if(existing_idx !== -1 && d.runs[existing_idx].run.times.primary_t < run.times.primary_t)
        return { run: run }; // has no place on this leaderboard because its obsolete

    // simulate leaderboard changes for unverified runs
    if(lbe.run.status.status !== 'verified') {
        d = _.cloneDeep(d);
    }

    if(existing_idx !== -1)
        d.runs.splice(existing_idx, 1);

    d.runs.splice(run_idx, 0, lbe);

    correct_leaderboard_run_places(d, vars);
    return d.runs[run_idx];
}

// leaderboards can have subcategories. correct the places returned by the speedrun
// api to take these subcategories into account
export function correct_leaderboard_run_places(d: Leaderboard, vars: Variable[]) {

    let subcategory_vars = _.filter(vars, 'is-subcategory');

    let last_places: {[key: string]: number} = {};
    let last_runs: {[key: string]: LeaderboardRunEntry} = {};

    if(d.runs) {
        for(let run of d.runs) {
            let subcategory_id = '';

            for(let v of subcategory_vars) {
                subcategory_id += run.run.values[v.id];
            }



            last_places[subcategory_id] = last_places[subcategory_id] ?
                last_places[subcategory_id] + 1 : 1;

            // handle ties by checking the previous run's time
            run.place =
                last_places[subcategory_id] !== 1 &&
                    last_runs[subcategory_id].run.times.primary_t === run.run.times.primary_t ?
                last_runs[subcategory_id].place :
                last_places[subcategory_id];

            last_runs[subcategory_id] = run;
        }
    }
}

export function make_distribution_chart(lb: Leaderboard, vars: Variable[]): Chart {

    let subcategory_vars = _.filter(vars, 'is-subcategory');

    let chart: Chart = {
        item_id: `leaderboards_distribution`,
        item_type: 'runs',
        chart_type: 'line',
        data: {},
        timestamp: new Date()
    };

    if(lb.runs) {
        for(let i = 0;i < lb.runs.length;i++) {
            let run = lb.runs[i];

            let subcategory_id;
            if(!subcategory_vars.length)
                subcategory_id = 'main';
            else
                subcategory_id = _.chain(run.run.values)
                    .pick(...subcategory_vars)
                    .toPairs()
                    .flatten()
                    .join('_')
                    .value();

            let p = {
                x: i,
                y: run.run.times.primary_t,
                obj: run_to_bulk(<Run>run.run)
            }

            if(chart.data[subcategory_id])
                chart.data[subcategory_id].push(p);
            else
                chart.data[subcategory_id] = [p];
        }
    }

    return chart;
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
