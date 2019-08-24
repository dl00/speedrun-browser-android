import * as _ from 'lodash';
import * as moment from 'moment';

import { Dao, DaoConfig, IndexDriver, IndexerIndex } from './';

import { DB } from '../db';

import { RedisMapIndex } from './backing/redis';

import { Category } from './categories';
import { Genre } from './genres';
import { Leaderboard, LeaderboardDao } from './leaderboards';
import { Level } from './levels';

import { Asset,
    BaseMiddleware,
    Names,
    normalize,
    Platform,
    Publisher,
    Region,
    UpstreamData,
} from '../speedrun-api';

import assert = require('assert');

export interface BulkGame {
    id: string;
    names: Names;
    abbreviation: string;
    weblink: string;
    assets: BulkGameAssets;
    platforms: string[]|UpstreamData<Platform>|Platform[];
    regions: string[]|UpstreamData<Region>|Region[];
    genres: string[]|UpstreamData<Genre>|Genre[];
    'release-date': string;
}

export interface BulkGameAssets {
    'cover-large'?: Asset;
    'trophy-1st'?: Asset;
    'trophy-2nd'?: Asset;
    'trophy-3rd'?: Asset;
    'trophy-4th'?: Asset;
}

export interface GameAssets extends BulkGameAssets {
    logo?: Asset;
    'cover-tiny'?: Asset;
    'cover-small'?: Asset;
    'cover-medium'?: Asset;
    icon?: Asset;
    background?: Asset;
    foreground?: Asset;
}

export function game_assets_to_bulk(game_assets: GameAssets): BulkGameAssets {
    return _.pick(game_assets, 'cover-large', 'trophy-1st', 'trophy-2nd', 'trophy-3rd', 'trophy-4th');
}

export interface Game extends BulkGame, BaseMiddleware {
    released: number;
    romhack?: boolean;
    developers: string[];
    publishers: string[]|Publisher[];
    created: string;
    assets: GameAssets;
    score?: number;

    categories?: Category[];
    levels?: Level[];
}

/// TODO: Use decorators
export function game_to_bulk(game: Game): BulkGame {
    const bulkGame: BulkGame = _.pick(game, 'id', 'names', 'abbreviation', 'weblink',
        'assets', 'platforms', 'regions', 'genres', 'release-date');

    bulkGame.assets = game_assets_to_bulk(game.assets);

    return bulkGame;
}

export function normalize_game(d: Game) {
    normalize(d);

    if (d.platforms && (d.platforms as UpstreamData<Platform>).data) {
        d.platforms = (d.platforms as UpstreamData<Platform>).data;
    }

    if (d.regions && (d.regions as UpstreamData<Region>).data) {
        d.regions = (d.regions as UpstreamData<Region>).data;
    }

    if (d.genres && (d.genres as UpstreamData<Genre>).data) {
        d.genres = (d.genres as UpstreamData<Genre>).data;
    }

    for (const platform in d.platforms) {
        normalize((d.platforms as any)[platform]);
    }
    for (const region in d.regions) {
        normalize((d.regions as any)[region]);
    }

    for (const genre in d.genres) {
        normalize((d.genres as any)[genre]);
    }
}

const POPULAR_GAMES_KEY = 'game_rank';

class PopularGamesIndex implements IndexDriver<Game> {
    public name: string;
    private max_return: number;

    constructor(name: string, max_return: number) {
        this.name = name;
        this.max_return = max_return;
    }

    public async load(conf: DaoConfig<Game>, keys: string[]): Promise<Array<Game|null>> {
        assert.equal(keys.length, 1, 'PopularGamesIndex only supports reading from a single key at a time');

        // we only read the first
        const spl = keys[0].split(':');

        const genre = spl[0];
        const offset = parseInt(spl[1]);

        const popular_game_ids: string[] = await conf.db.redis.zrevrange(POPULAR_GAMES_KEY + (genre ? ':' + genre : ''),
            offset, offset + this.max_return - 1);

        if (!popular_game_ids.length) {
            return [];
        }

        return await conf.load(popular_game_ids);
    }

    public async apply(conf: DaoConfig<Game>, games: Game[]) {
        // have to get games to deal with genre data
        const m = conf.db.redis.multi();

        for (const game of games) {

            const game_score = game.score || 0;

            // install master rank list
            m.zadd(POPULAR_GAMES_KEY, game_score.toString(), game.id);

            // install on category lists
            // TODO: switch to `speedrun_api.Genre[] instead of any[]`
            for (const genre of game.genres as any[]) {
                m.zadd(POPULAR_GAMES_KEY + ':' + (genre.id || genre), game_score.toString(), game.id);
            }
        }

        await m.exec();
    }

    public async clear(conf: DaoConfig<Game>, objs: Game[]) {
        const keys = _.map(objs, conf.id_key);

        await conf.db.redis.zrem(POPULAR_GAMES_KEY,
            ...keys);
    }

    public has_changed(old_obj: Game, new_obj: Game): boolean {
        return old_obj.score != new_obj.score;
    }

    public async get_genre_count(conf: DaoConfig<Game>, genre_ids: string[]): Promise<number[]> {

        const m = conf.db.redis.multi();

        for (const id of genre_ids) {
            m.zcard(POPULAR_GAMES_KEY + ':' + id);
        }

        const res: Array<[any, number]> = await m.exec();

        return res.map((v) => v[1]);
    }
}

function get_game_search_indexes(game: Game) {
    const indexes: Array<{ text: string, score: number, namespace?: string }> = [];

    if (game.abbreviation) {
        indexes.push({ text: game.abbreviation.toLowerCase(), score: game.score || 1 });
    }

    for (const name in game.names) {
        if (!game.names[name]) {
            continue;
        }

        const idx: any = { text: game.names[name].toLowerCase(), score: game.score || 1 };
        if (name != 'international') {
            idx.namespace = name;
        }

        indexes.push(idx);
    }

    return indexes;
}

export interface GameDaoOptions {
    max_items?: number;

    game_score_time_now?: moment.Moment;
    game_score_leaderboard_updated_cutoff?: moment.Moment;
    game_score_leaderboard_edge_cutoff?: moment.Moment;
}

export class GameDao extends Dao<Game> {

    private game_score_time_now: moment.Moment;
    private game_score_leaderboard_updated_cutoff: moment.Moment;
    private game_score_leaderboard_edge_cutoff: moment.Moment;

    constructor(db: DB, options?: GameDaoOptions) {
        super(db, 'games', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new RedisMapIndex('abbr', 'abbreviation'),
            new IndexerIndex('games', get_game_search_indexes),
            new PopularGamesIndex('popular_games', (options && options.max_items) ? options.max_items : 100),
        ];

        this.game_score_time_now = options && options.game_score_time_now ? options.game_score_time_now : moment();
        this.game_score_leaderboard_updated_cutoff = this.game_score_time_now.subtract(1, 'year');
        this.game_score_leaderboard_edge_cutoff = this.game_score_time_now.subtract(3, 'months');

        _.assign(this, _.pick(options, 'game_score_leaderboard_edge_cutoff', 'game_score_leaderboard_updated_cutoff'));
    }

    public async save(games: Game|Game[]): Promise<void> {
        if (!_.isArray(games)) {
            games = [games];
        }

        for (const game of games) {
            // make sure every game comes in with a score, otherwise its confusing
            if (_.isNil(game.score)) {
                await this.calculate_score(game);
            }
        }

        await super.save(games);
    }

    // refreshes the score for a game by reading its leaderboards.
    // returnes the games with the score calculated
    public async rescore_games(ids: string|string[]) {
        const games = _.reject(await this.load(ids), _.isNil) as Game[];

        for (const game of games) {
            game.score = await this.calculate_score(game);
        }

        await this.save(games);

        return games;
    }

    public async load_popular(offset?: number, genre_id?: string) {
        const key = `${genre_id || ''}:${offset || 0}`;
        return await this.load_by_index('popular_games', key);
    }

    public async get_genre_count(genre_id: string[]): Promise<number[]> {
        // TODO: this is kind of loose
        return await (this.indexes[2] as PopularGamesIndex).get_genre_count(this, genre_id);
    }

    protected async pre_store_transform(game: Game): Promise<Game> {
        normalize_game(game);
        return game;
    }

    // calculates the score for a single game by reading activity on its leaderboards
    private async calculate_score(game: Game): Promise<number> {
        // look at the game's leaderboards, for categories not levels. Find the number of records
        let leaderboards = await new LeaderboardDao(this.db).load_by_index('game', game.id) as Leaderboard[];

        leaderboards = leaderboards.filter((v) => _.isNil(v.level));

        const div = 1 + Math.log(Math.max(1, leaderboards.length));

        const game_score = Math.ceil(_.chain(leaderboards)
            .reject(_.isNil)
            .map(_.bind(this.generate_leaderboard_score, this))
            .sum().value() / div);

        return game_score;
    }

    private generate_leaderboard_score(leaderboard: Leaderboard) {
        let score = 0;

        for (const run of leaderboard.runs) {
            const d = moment(run.run.date);

            if (d.isAfter(this.game_score_leaderboard_edge_cutoff)) {
                score += 4;
            }
            else if (d.isAfter(this.game_score_leaderboard_updated_cutoff)) {
                score++;
 }
        }

        return score;
    }
}
