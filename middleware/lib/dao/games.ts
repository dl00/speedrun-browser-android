import * as _ from 'lodash';
import * as moment from 'moment';

import { Dao, DaoConfig, IndexDriver } from './';

import { DB } from '../db';

import { RedisMapIndex } from './backing/redis';

import { Leaderboard, LeaderboardDao } from './leaderboards';

import { Names,
    UpstreamData,
    Platform,
    Region,
    Publisher,
    Genre,
    Category,
    Level,
    Asset,
    BaseMiddleware,
    normalize
} from '../speedrun-api';

import * as speedrun_db from '../speedrun-db';

import assert = require('assert');

export interface BulkGame {
    id: string
    names: Names
    abbreviation: string
    weblink: string
    assets: BulkGameAssets
    platforms: string[]|UpstreamData<Platform>|Platform[]
    regions: string[]|UpstreamData<Region>|Region[]
    genres: string[]|UpstreamData<Genre>|Genre[]
    'release-date': string
}

export interface BulkGameAssets {
    'cover-large'?: Asset
    'trophy-1st'?: Asset
    'trophy-2nd'?: Asset
    'trophy-3rd'?: Asset
    'trophy-4th'?: Asset
}

export interface GameAssets extends BulkGameAssets {
    logo?: Asset
    'cover-tiny'?: Asset
    'cover-small'?: Asset
    'cover-medium'?: Asset
    icon?: Asset
    background?: Asset
    foreground?: Asset
}

export function game_assets_to_bulk(game_assets: GameAssets): BulkGameAssets {
    return _.pick(game_assets, 'cover-large', 'trophy-1st', 'trophy-2nd', 'trophy-3rd', 'trophy-4th');
}

export interface Game extends BulkGame, BaseMiddleware {
    released: number
    romhack?: boolean,
    developers: string[]
    publishers: string[]|Publisher[]
    created: string
    assets: GameAssets
    score?: number

    categories?: Category[];
    levels?: Level[];
}

/// TODO: Use decorators
export function game_to_bulk(game: Game): BulkGame {
    let bulkGame: BulkGame = _.pick(game, 'id', 'names', 'abbreviation', 'weblink',
        'assets', 'platforms', 'regions', 'genres', 'release-date');

    bulkGame.assets = game_assets_to_bulk(game.assets);

    return bulkGame
}

export function normalize_game(d: Game) {
    normalize(d);

    if(d.platforms && (<UpstreamData<Platform>>d.platforms).data) {
        d.platforms = (<UpstreamData<Platform>>d.platforms).data;
    }

    if(d.regions && (<UpstreamData<Region>>d.regions).data) {
        d.regions = (<UpstreamData<Region>>d.regions).data;
    }

    if(d.genres && (<UpstreamData<Genre>>d.genres).data) {
        d.genres = (<UpstreamData<Genre>>d.genres).data;
    }

    for(let platform in d.platforms) {
        normalize((<any>d.platforms)[platform]);
    }
    for(let region in d.regions) {
        normalize((<any>d.regions)[region]);
    }

    for(let genre in d.genres) {
        normalize((<any>d.genres)[genre]);
    }
}

const POPULAR_GAMES_KEY = 'game_rank';

class PopularGamesIndex implements IndexDriver<Game> {
    name: string;
    private max_return: number;

    constructor(name: string, max_return: number) {
        this.name = name;
        this.max_return = max_return;
    }

    async load(conf: DaoConfig<Game>, keys: string[]): Promise<(Game|null)[]> {
        assert.equal(keys.length, 1, 'PopularGamesIndex only supports reading from a single key at a time');

        // we only read the first
        let spl = keys[0].split(':');

        let genre = spl[0];
        let offset = parseInt(spl[1]);

        let popular_game_ids: string[] = await conf.db.redis.zrevrange(POPULAR_GAMES_KEY + (genre ? ':' + genre : ''),
            offset, offset + this.max_return - 1);

        return await conf.load(popular_game_ids);
    }

    async apply(conf: DaoConfig<Game>, games: Game[]) {
        // have to get games to deal with genre data
        let m = conf.db.redis.multi();

        for(let game of games) {

            let game_score = game.score || 0;

            // install master rank list
            await m.zadd(POPULAR_GAMES_KEY, game_score.toString(), game.id);

            // install on category lists
            // TODO: switch to `speedrun_api.Genre[] instead of any[]`
            for(let genre of <any[]>game.genres) {
                await m.zadd(POPULAR_GAMES_KEY + ':' + (genre.id || genre), game_score.toString(), game.id);
            }
        }
    }

    async clear(conf: DaoConfig<Game>, objs: Game[]) {
        let keys = _.map(objs, conf.id_key);

        await conf.db.redis.zrem(POPULAR_GAMES_KEY,
            ...keys);
    }

    has_changed(old_obj: Game, new_obj: Game): boolean {
        return old_obj.score != new_obj.score;
    }
}

export interface GameDaoOptions {
    max_items?: number;
}

export class GameDao extends Dao<Game> {
    constructor(db: DB, options?: GameDaoOptions) {
        super(db, 'games', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new RedisMapIndex('abbr', 'abbreviation'),
            new PopularGamesIndex('popular_games', (options && options.max_items) ? options.max_items : 100)
        ];
    }

    // refreshes the score for a game by reading its leaderboards.
    // returnes the games with the score calculated
    async rescore_games(ids: string|string[]) {
        let games = <Game[]>_.reject(await this.load(ids), _.isNil);

        for(let game of games) {
            game.score = await this.calculate_score(game);
        }

        await this.save(games);

        return games;
    }

    // calculates the score for a single game by reading activity on its leaderboards
    private async calculate_score(game: Game): Promise<number> {
        let game_score = 0;

        // look at the game's leaderboards, for categories not levels. Find the number of records
        let categories_raw = await this.db.redis.hget(speedrun_db.locs.categories, game.id);

        if(categories_raw) {
            let categories = JSON.parse(categories_raw);

            categories = _.filter(categories, c => c.type == 'per-game');

            let div = 1 + Math.log(Math.max(1, categories.length));

            if(categories.length) {
                let leaderboards = await new LeaderboardDao(this.db).load(_.map(categories, 'id'));

                game_score = Math.ceil(_.chain(leaderboards)
                    .reject(_.isNil)
                    .map(GameDao.generate_leaderboard_score)
                    .sum().value() / div);
            }
        }

        return game_score;
    }

    private static generate_leaderboard_score(leaderboard: Leaderboard) {
        let timenow = leaderboard.updated ? moment(leaderboard.updated) : moment();

        let updated_cutoff = timenow.subtract(1, 'year');
        let edge_cutoff = timenow.subtract(3, 'months');

        let score = 0;

        for(let run of leaderboard.runs) {
            let d = moment(run.run.date);

            if(d.isAfter(edge_cutoff))
                score += 4;
            else if(d.isAfter(updated_cutoff))
                score++;
        }

        return score;
    }

    async load_popular(offset: number, genreId: string) {
        let key = `${genreId || ''}:${offset || 0}`;
        return await this.load_by_index('popular_games', key);
    }
}
