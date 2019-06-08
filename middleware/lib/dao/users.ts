import * as _ from 'lodash';

import { DB } from '../db';

import {
    Names,
    BaseMiddleware,
    normalize,
} from '../speedrun-api';

import { RedisMapIndex } from './backing/redis';

import { Game, GameDao, game_assets_to_bulk } from './games';
import { Category, CategoryDao } from './categories';
import { Level, LevelDao } from './levels';
import { Leaderboard } from './leaderboards';
import { NewRecord } from './runs';

import { BulkGameAssets } from './games';
import { LeaderboardRunEntry, Run, run_to_bulk } from './runs';

import { Dao, IndexerIndex } from './';

export interface GamePersonalBests {
    id: string
    names: Names
    assets: BulkGameAssets

    categories: {[id: string]: CategoryPersonalBests}
}

export interface CategoryPersonalBests {
    id: string
    name: string
    type: string

    levels?: {[id: string]: LevelPersonalBests}

    run?: LeaderboardRunEntry
}

export interface LevelPersonalBests {
    id: string
    name: string

    run: LeaderboardRunEntry
}

export interface BulkUser {
    id: string,
    names?: Names,
    name?: string,
    'name-style'?: {
        style: 'solid'|''
        color: {
            light: string
            dark: string
        }
    }
}

export interface User extends BulkUser, BaseMiddleware {
    weblink?: string
    role?: 'banned'|'user'|'trusted'|'moderator'|'admin'|'programmer'
    signup?: string

    bests: {[id: string]: GamePersonalBests}
}

export function user_to_bulk(user: User) {
    return _.pick(user, 'id', 'names', 'name', 'name-style');
}

// add/update the given personal best entry for the given user
export function apply_personal_best(player: User,
            game: Game, category: Category,
            level: Level|null,
            lb: Leaderboard,
            index: number): NewRecord|null {

    let category_run: CategoryPersonalBests = {
        id: category.id,
        name: category.name,
        type: category.type
    };

    let game_run: GamePersonalBests = {
        id: game.id,
        names: game.names,
        assets: game_assets_to_bulk(game.assets),
        categories: {}
    };

    let best_run: LeaderboardRunEntry = {
        place: lb.runs[index].place,
        run: run_to_bulk(<Run>lb.runs[index].run)
    };

    if(!best_run.place)
        return null;

    let old_run = null;

    if(level) {

        old_run = _.get(player, `bests["${game.id}"].categories["${category.id}"].levels["${level.id}"].run`);
        if(old_run && (old_run.run.id == best_run.run.id || old_run.place > best_run.place))
            return null;

        let level_run: LevelPersonalBests = {
            id: level.id,
            name: level.name,

            run: best_run
        };

        category_run.levels = {};
        category_run.levels[level.id] = level_run;
    }
    else {
        old_run = _.get(player, `bests["${game.id}"].categories["${category.id}"].run`);
        if(old_run && (old_run.run.id == best_run.run.id || old_run.place > best_run.place))
            return null;

        category_run.run = best_run;
    }

    game_run.categories[category.id] = category_run;

    let new_bests: {[id: string]: GamePersonalBests} = {};

    new_bests[game.id] = game_run;

    _.merge(player, {bests: new_bests});

    return {
        old_run: old_run,
        new_run: best_run
    };
}

function get_user_search_indexes(user: User) {
    let indexes: { text: string, score: number, namespace?: string }[] = [];

    if(user.name)
        indexes.push({ text: user.name.toLowerCase(), score: 1 });
    else {
        for(let name in user.names) {
            if(!user.names[name])
                continue;

            let idx: any = { text: user.names[name].toLowerCase(), score: 1 };
            if(name != 'international')
                idx.namespace = name;

            indexes.push(idx);
        }
    }

    return indexes;
}

export function normalize_user(d: User) {
    normalize(d);
}

export class UserDao extends Dao<User> {
    constructor(db: DB) {
        super(db, 'users', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new RedisMapIndex('abbr', (v: User) => {
                if(v.names && v.names['international'])
                    return v.names['international'];

                // TODO: this is kind of dumb
                return '';
            }),
            new IndexerIndex('players', get_user_search_indexes)
        ];
    }

    protected async pre_store_transform(user: User): Promise<User> {
        normalize_user(user);
        return user;
    }

    async apply_leaderboard_bests(lb: Leaderboard, updated_players: {[id: string]: User}): Promise<NewRecord[]> {

        let player_ids = _.chain(lb.runs)
            .map(v => _.map(v.run.players, 'id'))
            .flatten()
            .reject(_.isNil)
            .uniq()
            .value();

        if(!player_ids.length)
            return []; // nothing to do

        let game_id = (<any>lb.game).id || lb.game;
        let category_id = (<any>lb.category).id || lb.category;
        let level_id = lb.level;

        // TODO: Parallelize

        let games = await new GameDao(this.db).load(game_id);
        let categories = await new CategoryDao(this.db).load(category_id);
        let level = null;
        if(level_id) {
            let levels = await new LevelDao(this.db).load(level_id);
            level = levels[0]!;
        }


        let game: Game = games[0]!;
        let category: Category = categories[0]!;

        if(!category)
            return [];

        // store/update player information
        // TODO: not sure why, but typescript errors with wrong value here?
        let players = <{[id: string]: User}>_.keyBy(
            _.filter(await this.load(player_ids), 'id'),
            'id');

        // set known leaderboard information to player
        _.merge(players, updated_players);

        if(!_.keys(players).length) {
            return []; // still nothing
        }

        let new_records: (NewRecord|null)[] = [];

        for(let i = 0;i < lb.runs.length;i++) {
            for(let player of lb.runs[i].run.players) {
                new_records.push(apply_personal_best(players[player.id], game, category, level, lb, i));
            }
        }

        this.save(_.values(players));

        return <NewRecord[]>_.reject(new_records, _.isNil);
    }
}