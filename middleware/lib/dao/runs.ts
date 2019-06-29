import * as _ from 'lodash';
import * as moment from 'moment';
import * as assert from 'assert';

import { Dao, DaoConfig, IndexDriver } from './';

import { GameDao, Game, BulkGame, game_to_bulk } from './games';

import { DB } from '../db';

import {
    BaseMiddleware,
    normalize,
} from '../speedrun-api';

import { BulkRun } from './runs';
import { BulkUser, User, user_to_bulk } from './users';
import { Category, BulkCategory, category_to_bulk } from './categories';
import { BulkLevel, Level, level_to_bulk } from './levels';
import { Genre } from './genres';

/// information about a new PB from a player
export interface NewRecord {
    old_run: LeaderboardRunEntry,
    new_run: LeaderboardRunEntry
}

export interface RunTimes {
    primary: string
    primary_t: number
    realtime?: string
    realtime_t: number
    realtime_noloads?: string
    realtime_noloads_t: number
    ingame?: string
    ingame_t: number
}

export interface RunSystem {
    platform?: string
    emulated?: boolean
    region?: string
}

export interface BulkRun {
    id: string
    date: string
    players: BulkUser[]
    times: RunTimes
    system: RunSystem
    values: {[key: string]: string}

    [key: string]: any
}

export interface Run extends BulkRun, BaseMiddleware {
    weblink: string
    game: BulkGame|string
    level?: BulkLevel|string|null
    category: BulkCategory|string
    submitted: string
    videos: {
        text: string
        links: {
            uri: string
        }[]
    },

    comment: string
    status: {
        status: 'new'|'verified'|'rejected'
        examiner?: User|string
        'verify-date': string
    }

    values: {[key: string]: string}
}

export interface LeaderboardRunEntry {
    place?: number|null
    run: BulkRun
}

export function normalize_run(d: Run) {
    normalize(d);

    if(d.players) {
        d.players = d.players.map(<any>user_to_bulk);
    }

    if(_.isObject(d.game))
        d.game = game_to_bulk(<Game>d.game);
    if(_.isObject(d.category))
        d.category = category_to_bulk(<Category>d.category);
    if(_.isObject(d.level)) {
        if(!_.keys(d.level).length)
            delete d.level;
        else
            d.level = level_to_bulk(<Level>d.level);
    }
}

/// TODO: Use decorators
export function run_to_bulk(run: Run): BulkRun {
    let newr = _.pick(run, 'id', 'date', 'players', 'times', 'system', 'values');

    newr.players = newr.players.map(v => user_to_bulk(<User>v));

    return newr;
}

export const LATEST_VERIFIED_RUNS_KEY = 'verified_runs';
export const LATEST_NEW_RUNS_KEY = 'latest_new_runs'


export class RecentRunsIndex implements IndexDriver<LeaderboardRunEntry> {
    name: string;
    private date_property: string;
    private redis_key: string;
    private keep_count: number;
    private max_return: number;

    constructor(name: string, date_property: string, redis_key: string, keep_count: number, max_return: number) {
        this.name = name;
        this.date_property = date_property;
        this.redis_key = redis_key;
        this.keep_count = keep_count;
        this.max_return = max_return;
    }

    async load(conf: DaoConfig<LeaderboardRunEntry>, keys: string[]): Promise<(LeaderboardRunEntry|null)[]> {

        assert.equal(keys.length, 1, 'RecentRunsIndex only supports reading from a single key at a time');

        // we only read the first
        let spl = keys[0].split(':');

        let genre = spl[0];
        let offset = parseInt(spl[1]);

        let latest_run_ids: string[] = await conf.db.redis.zrevrange(this.redis_key + (genre ? ':' + genre : ''),
            offset, offset + this.max_return - 1);

        return await conf.load(latest_run_ids);
    }

    async apply(conf: DaoConfig<LeaderboardRunEntry>, objs: LeaderboardRunEntry[]) {
        // have to get games to deal with genre data
        let game_ids = _.map(objs, 'run.game.id');
        let games = _.zipObject(game_ids, await new GameDao(conf.db).load(game_ids));

        let m = conf.db.redis.multi();

        for(let lbr of objs) {
            let date_score = moment(_.get(<Run>lbr.run, this.date_property) || 0).unix().toString();

            m
                .zadd(this.redis_key, date_score, lbr.run.id)
                .zremrangebyrank(this.redis_key, 0, -this.keep_count - 1);

            let game = <Game>games[<string>(<BulkGame>(<Run>lbr.run).game).id];

            if(!game)
                throw `Missing game for run: ${lbr.run.id}, game id: ${(<BulkGame>(<Run>lbr.run).game).id}`

            for(let genre of <Genre[]>game.genres) {
                let genre_runs = this.redis_key + ':' + genre.id;
                m.zadd(genre_runs, date_score, lbr.run.id)
                    .zremrangebyrank(genre_runs, 0, -this.keep_count - 1);
            }
        }

        await m.exec();
    }

    async clear(conf: DaoConfig<LeaderboardRunEntry>, objs: LeaderboardRunEntry[]) {
        let keys = _.map(objs, conf.id_key);

        await conf.db.redis.zrem(this.redis_key,
            ...keys);
    }

    has_changed(old_obj: LeaderboardRunEntry, new_obj: LeaderboardRunEntry): boolean {
        return _.get(<Run>old_obj.run, this.date_property) != _.get(<Run>new_obj.run, this.date_property);
    }
}

export interface RunDaoOptions {
    latest_runs_history_length?: number;
    max_items?: number;
}

export class RunDao extends Dao<LeaderboardRunEntry> {
    constructor(db: DB, config?: RunDaoOptions) {
        super(db, 'runs', 'mongo');

        this.id_key = _.property('run.id');

        this.indexes = [
            new RecentRunsIndex('latest_new_runs', 'submitted', LATEST_NEW_RUNS_KEY,
                config && config.latest_runs_history_length ? config.latest_runs_history_length : 1000,
                config && config.max_items ? config.max_items : 100
            ),
            new RecentRunsIndex('latest_verified_runs', 'status.verify-date', LATEST_VERIFIED_RUNS_KEY,
                config && config.latest_runs_history_length ? config.latest_runs_history_length : 1000,
                config && config.max_items ? config.max_items : 100
            )
        ];
    }

    async load_latest_runs(offset?: number, genreId?: string, verified: boolean = true) {
        let key = `${genreId || ''}:${offset || 0}`;
        return await this.load_by_index(verified ? 'latest_verified_runs' : 'latest_new_runs', key);
    }

    protected async pre_store_transform(run: LeaderboardRunEntry): Promise<LeaderboardRunEntry> {
        normalize_run(<Run>run.run);
        return run;
    }
}
