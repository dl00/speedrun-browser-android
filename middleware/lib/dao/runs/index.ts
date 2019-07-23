import * as _ from 'lodash';
import * as moment from 'moment';
import * as assert from 'assert';

import { Dao, DaoConfig, IndexDriver } from '../';

import { GameDao, Game, BulkGame, game_to_bulk } from '../games';

import { RecordChartIndex, get_player_pb_chart } from './charts';
import { SupportingStructuresIndex } from './supporting-structures';
import { Chart } from '../charts'

import { DB } from '../../db';

import {
    BaseMiddleware,
    normalize,
} from '../../speedrun-api';

import { BulkUser, User, user_to_bulk } from '../users';
import { CategoryDao, Category, BulkCategory, category_to_bulk } from '../categories';
import { LevelDao, BulkLevel, Level, level_to_bulk } from '../levels';
import { UserDao } from '../users';
import { Genre } from '../genres';

/// information about a new PB from a player
export interface NewRecord {
    old_run: LeaderboardRunEntry,
    new_run: LeaderboardRunEntry
}

export interface RunTimes {
    primary: string
    primary_t: number
    realtime?: string
    realtime_t?: number
    realtime_noloads?: string
    realtime_noloads_t?: number
    ingame?: string
    ingame_t?: number
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
    return newr;
}

function generate_month_boundaries(start: number, end: number) {
    // todo: pre-allocate array?
    let boundaries = [];

    for(let i = start;i < end;i++) {
        for(let j = 1;j <= 12;j++) {
            boundaries.push(`${i}-${_.padStart(j.toString(), 2, '0')}`);
        }
    }

    return boundaries;
}

export interface PopulateRunResponse {
    drop_runs: Run[],
    games: {[id: string]: Game|null}
    categories: {[id: string]: Category|null}
    levels: {[id: string]: Level|null}
}

// returns a list of runs which cannot be processed because the game is missing, or similar
export async function populate_run_sub_documents(db: DB, runs: Run[]): Promise<PopulateRunResponse> {
    let game_ids = <string[]>_.uniq(_.map(runs, 'game.id'));
    let category_ids = <string[]>_.uniq(_.map(runs, 'category.id'));
    let level_ids = <string[]>_.uniq(_.map(runs, 'level.id'));

    let player_ids = <string[]>_.uniq(_.flatten(_.map(runs, (run) => {
        return _.reject(_.map(run.players, 'id'), _.isNil);
    })));

    let games = _.zipObject(game_ids, await new GameDao(db).load(game_ids));
    let categories = _.zipObject(category_ids, await new CategoryDao(db).load(category_ids));
    let levels = _.zipObject(level_ids, await new LevelDao(db).load(level_ids));
    let players = _.zipObject(player_ids, await new UserDao(db).load(player_ids));

    // list of runs we are skipping processing
    let drop_runs: Run[] = [];

    for(let run of runs) {
        if(!run.game || !run.category ||
            !games[(<BulkGame>run.game).id] || !categories[(<BulkCategory>run.category).id]) {
            drop_runs.push(run);
            continue;
        }

        run.game = <BulkGame>games[(<BulkGame>run.game).id];
        run.category = <Category>categories[(<BulkCategory>run.category).id];

        if(run.level && (<BulkLevel>run.level).id)
            run.level = <Level|null>levels[(<BulkLevel>run.level).id];

        // handle special cases for users
        for(let i = 0;i < run.players.length;i++) {
            if(!run.players[i].id)
                continue;
            if(!players[run.players[i].id]) {
                // new player
                // currently the best way to solve this is to do a game resync
                drop_runs.push(run);
                continue;
            }

            run.players[i] = <BulkUser>players[run.players[i].id];
        }
    }

    return {
        drop_runs: drop_runs,
        games: games,
        categories: categories,
        levels: levels
    };
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

            if(lbr.run.times.primary_t <= 0.01) {
                // ensure these "dummy" runs are never added
                m.zrem(this.redis_key, lbr.run.id);
                continue;
            }

            let date_score = moment(_.get(<Run>lbr.run, this.date_property) || 0).unix().toString();

            m
                .zadd(this.redis_key, date_score, lbr.run.id)
                .zremrangebyrank(this.redis_key, 0, -this.keep_count - 1);

            let game = <Game>games[<string>(<BulkGame>(<Run>lbr.run).game).id];

            if(!game)
                throw new Error(`Missing game for run: ${lbr.run.id}, game id: ${(<BulkGame>(<Run>lbr.run).game).id}`);

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

        // TODO: these mongodb indexes are just hardcoded in here for now...
        // at least this first index for mongodb is overkill, its just used for an aggregation
        db.mongo.collection(this.collection).createIndex({
            'run.date': 1
        }, {
            background: true,
            partialFilterExpression: {'run.status.status': 'verified'}
        }).then(_.noop);

        db.mongo.collection(this.collection).createIndex({
            'run.game.id': 1,
            'run.date': 1
        }, {
            background: true
        }).then(_.noop);

        db.mongo.collection(this.collection).createIndex({
            'run.category.id': 1,
            'run.level.id': 1,
            'run.date': 1
        }, {
            background: true
        }).then(_.noop);

        db.mongo.collection(this.collection).createIndex({
            'run.players.id': 1,
            'run.game.id': 1,
            'run.category.id': 1,
            'run.level.id': 1,
            'run.date': 1
        }, {
            background: true
        }).then(_.noop);

        this.indexes = [
            new RecentRunsIndex('latest_new_runs', 'submitted', LATEST_NEW_RUNS_KEY,
                config && config.latest_runs_history_length ? config.latest_runs_history_length : 1000,
                config && config.max_items ? config.max_items : 100
            ),
            new RecentRunsIndex('latest_verified_runs', 'status.verify-date', LATEST_VERIFIED_RUNS_KEY,
                config && config.latest_runs_history_length ? config.latest_runs_history_length : 1000,
                config && config.max_items ? config.max_items : 100
            ),
            new SupportingStructuresIndex('supporting_structures'),
            new RecordChartIndex('chart_wrs')
        ];
    }

    /// return new records saved to DB during the lifetime of this Dao instance
    collect_new_records(): NewRecord[] {
        return (<SupportingStructuresIndex>this.indexes.find(ind => ind.name === 'supporting_structures')).new_records;
    }

    /// regenerates speedruns remote data matching the given filter
    /// useful when supplementary data structures are changed
    async massage_runs(filter = {}) {
        let cursor = await this.db.mongo.collection(this.collection)
            .find(filter)
            // sorting gives a significant speed boost because it reduces the number
            // of arbitrary different categories and levels which may need to be requested,
            // significantly reducing the number of times data has to be serialized/deserialized
            .sort({
                'run.game.id': 1,
                'run.date': 1
            });

        let batchSize = 500;
        let count = 0;

        let runs = new Array(batchSize);

        while(await cursor.hasNext()) {

            let cur;
            for(cur = 0;cur < batchSize && await cursor.hasNext();cur++) {
                runs[cur] = await cursor.next();
            }

            runs.splice(cur, batchSize);

            count += cur;

            console.log('[MASSAGE]', count);

            // reload the game, category, level, players, and leaderboard place for this run
            await populate_run_sub_documents(this.db, _.map(runs, 'run'));
            await this.save(runs);
        }

        return count;
    }

    async load_latest_runs(offset?: number, genreId?: string, verified: boolean = true) {
        let key = `${genreId || ''}:${offset || 0}`;
        return await this.load_by_index(verified ? 'latest_verified_runs' : 'latest_new_runs', key);
    }

    protected async pre_store_transform(run: LeaderboardRunEntry): Promise<LeaderboardRunEntry> {
        normalize_run(<Run>run.run);
        return run;
    }

    async get_historical_run_count(): Promise<Chart> {
        let month_bounaries: string[] = generate_month_boundaries(2010, new Date().getUTCFullYear() + 1);

        let data = (await this.db.mongo.collection(this.collection).aggregate([
            {
                $match: {'run.status.status': 'verified'}
            },
            {
                $facet: _.chain(month_bounaries)
                        .map((v) => {
                            return [v, [{$match: {'run.date': {$lt: v}}}, {$count: 'y'}]]
                        })
                        .fromPairs()
                        .value()
            }
        ]).toArray())[0];

        let now = Date.now() / 1000;

        return {
            item_id: 'site_historical_runs',
            item_type: 'runs',
            parent_type: 'runs',
            chart_type: 'line',
            data: {
                'main': _.chain(data)
                    .mapValues((v, k) => {
                        return {
                            x: new Date(k).getTime() / 1000,
                            y: v[0].y
                        }
                    })
                    .values()
                    .filter(p => p.x < now)
                    .value()
            },
            timestamp: new Date()
        };
    }

    private async get_submission_volume(filter: any) {
        let month_bounaries: string[] = generate_month_boundaries(2010, new Date().getUTCFullYear() + 1);

        let d = (await this.db.mongo.collection(this.collection).aggregate([
            {
                $match: filter
            },
            {
                $bucket: {
                    groupBy: '$run.date',
                    boundaries: month_bounaries,
                    default: '1970-01',
                    output: {
                        count: {$sum: 1}
                    }
                }
            }
        ]).toArray()).map(v => {
            return {
                x: new Date(v._id).getTime() / 1000,
                y: v.count
            }
        });

        // remove unknown entries
        if(d.length && d[0].x === 0)
            d.splice(0, 1);

        if(!d.length)
            return d;

        // fill in skipped boundaries
        let bound_cur = _.findIndex(month_bounaries, v => d[0].x == new Date(v + '-01').getTime() / 1000);
        for(let i = 1;i < d.length;i++) {
            let to_add = [];
            while(d[i].x != new Date(month_bounaries[++bound_cur] + '-01').getTime() / 1000) {
                to_add.push({ x: new Date(month_bounaries[bound_cur] + '-01').getTime() / 1000, y: 0})
            }

            if(to_add.length) {
                d.splice(i, 0, ...to_add)
                i += to_add.length
            }
        }

        // remove current month, if any
        if(new Date(d[d.length - 1].x).getUTCMonth() == new Date().getUTCMonth() &&
            new Date(d[d.length - 1].x).getUTCFullYear() == new Date().getUTCFullYear())
            d.splice(d.length - 1, 1)

        return d;
    }

    async get_site_submission_volume(): Promise<Chart> {
        return {
            item_id: 'site_volume',
            item_type: 'runs',
            parent_type: 'runs',
            chart_type: 'bar',
            data: {
                'main': await this.get_submission_volume({ 'run.status.status': 'verified' })
            },
            timestamp: new Date()
        };
    }

    async get_leaderboard_submission_volume(category_id: string, level_id: string|null): Promise<Chart> {
        let filter: any = {
            'run.category.id': category_id,
            'run.status.status': 'verified'
        };

        if(level_id)
            filter['run.level.id'] = level_id;

        return {
            item_id: category_id + (level_id ? '_' + level_id : ''),
            item_type: 'runs',
            chart_type: 'bar',
            data: {
                'main': await this.get_submission_volume(filter)
            },
            timestamp: new Date()
        }
    }

    async get_game_submission_volume(game_id: string): Promise<Chart> {
        return {
            item_id: game_id,
            item_type: 'runs',
            chart_type: 'bar',
            data: {
                'main': await this.get_submission_volume({
                    'run.game.id': game_id,
                    'run.status.status': 'verified'
                })
            },
            timestamp: new Date()
        }
    }

    async get_player_submission_volume(player_id: string): Promise<Chart> {
        return {
            item_id: player_id,
            item_type: 'runs',
            chart_type: 'bar',
            data: {
                'main': await this.get_submission_volume({
                    'run.players.id': player_id,
                    'run.status.status': 'verified'
                })
            },
            timestamp: new Date()
        }
    }

    async get_player_favorite_runs(player_id: string): Promise<Chart> {
        let chart_data = await this.db.mongo.collection(this.collection).aggregate([
            {
                $match: {
                    'run.players.id': player_id
                }
            },
            {
                $group: {
                    _id: '$run.game.id',
                    count: {$sum: 1},
                    game: {$mergeObjects: "$run.game"}
                }
            },
            {
                $sort: {
                    count: -1
                }
            }
        ]).toArray();

        return {
            item_id: player_id,
            item_type: 'games',
            parent_type: 'runs',
            chart_type: 'pie',
            data: {
                'main': _.chain(chart_data)
                .map(p => {
                    return {
                        x: 0,
                        y: p.count,
                        obj: p.game
                    }
                })
                .value()
            },
            timestamp: new Date()
        }
    }

    async get_player_pb_chart(player_id: string, game_id: string) {
        return await get_player_pb_chart(this, player_id, game_id);
    }
}
