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

// leaderboards can have subcategories. correct the places returned by the speedrun
// api to take these subcategories into account
export function correct_leaderboard_run_places(d: Leaderboard, vars: Variable[]) {
    if(!d.runs)
        return;

    let subcategory_vars = _.filter(vars, 'is-subcategory');

    let last_places: {[key: string]: number} = {};
    let last_runs: {[key: string]: LeaderboardRunEntry} = {};
    let seen_players: {[key: string]: {[key: string]: 1}} = {};

    for(let run of d.runs) {
        let subcategory_id = '';

        for(let v of subcategory_vars) {
            subcategory_id += run.run.values[v.id];
        }

        let player_ids = _.map(run.run.players, 'id').join('_');

        if(seen_players[subcategory_id] && seen_players[subcategory_id][player_ids]) {
            delete run.place;
        }
        else {
            last_places[subcategory_id] = last_places[subcategory_id] ?
                last_places[subcategory_id] + 1 : 1;

            // handle ties by checking the previous run's time
            run.place =
                last_places[subcategory_id] !== 1 &&
                    last_runs[subcategory_id].run.times.primary_t === run.run.times.primary_t ?
                last_runs[subcategory_id].place :
                last_places[subcategory_id];

            last_runs[subcategory_id] = run;

            if(!seen_players[subcategory_id])
                seen_players[subcategory_id] = {};
            seen_players[subcategory_id][player_ids] = 1;
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
        for(let i = 0;i < lb.runs.length * 0.95;i++) {
            let run = lb.runs[i];

            if(!run.place)
                continue;

            let subcategory_id;
            if(!subcategory_vars.length)
                subcategory_id = 'main';
            else
                subcategory_id = _.chain(run.run.values)
                    .pick(..._.map(subcategory_vars, 'id'))
                    .toPairs()
                    .flatten()
                    .join('_')
                    .value();

            let p = {
                x: <number>run.place,
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
