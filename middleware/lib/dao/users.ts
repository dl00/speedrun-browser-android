import * as _ from 'lodash';

import { DB } from '../db';

import {
    Names,
    BaseMiddleware,
    normalize,
} from '../speedrun-api';

import { RedisMapIndex } from './backing/redis';

import { game_assets_to_bulk } from './games';
import { NewRecord } from './runs';

import { BulkGameAssets } from './games';
import { RunDao, LeaderboardRunEntry, Run, run_to_bulk } from './runs';

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
    return _.pick(user, 'id', 'names', 'name', 'name-style', 'location');
}

// add/update the given personal best entry for the given user
export function apply_personal_best(player: User, run: LeaderboardRunEntry): NewRecord|null {

    if(!run.run.category || !run.run.category.id)
        return null;

    let category_run: CategoryPersonalBests = {
        id: run.run.category.id,
        name: run.run.category.name,
        type: run.run.category.type
    };

    let game_run: GamePersonalBests = {
        id: run.run.game.id,
        names: run.run.game.names,
        assets: game_assets_to_bulk(run.run.game.assets),
        categories: {}
    };

    let best_run: LeaderboardRunEntry = {
        place: run.place,
        run: run_to_bulk(<Run>run.run)
    };

    if(!best_run.place)
        return null;

    let old_run = null;

    if(run.run.level && run.run.level.id) {

        old_run = _.get(player, `bests["${run.run.game.id}"].categories["${run.run.category.id}"].levels["${run.run.level.id}"].run`);
        if(old_run && (old_run.run.id == best_run.run.id || old_run.place > best_run.place))
            return null;

        let level_run: LevelPersonalBests = {
            id: run.run.level.id,
            name: run.run.level.name,

            run: best_run
        };

        category_run.levels = {};
        category_run.levels[run.run.level.id] = level_run;
    }
    else {
        old_run = _.get(player, `bests["${run.run.game.id}"].categories["${run.run.category.id}"].run`);
        if(old_run && (old_run.run.id == best_run.run.id || old_run.place > best_run.place))
            return null;

        category_run.run = best_run;
    }

    game_run.categories[run.run.category.id] = category_run;

    let new_bests: {[id: string]: GamePersonalBests} = {};

    new_bests[run.run.game.id] = game_run;

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

        this.computed = {
            /// TODO: simplify this
            'bests': async (user) => {

                let run_dao = new RunDao(this.db!);

                await Promise.all(_.map(user.bests, async (bg: GamePersonalBests) => {
                    await Promise.all(_.map(bg.categories, async (bc: CategoryPersonalBests) => {
                        if(bc.run) {
                            bc.run = (await run_dao.load(bc.run.run.id))[0] || bc.run;
                            bc.run.run = run_to_bulk(<Run>bc.run.run);
                        }
                        else if(bc.levels) {
                            let runs = <LeaderboardRunEntry[]>_.reject(await run_dao.load(_.map(bc.levels, 'run.run.id')), _.isNil);

                            for(let id in bc.levels) {
                                bc.levels[id].run = runs.find((r: LeaderboardRunEntry) => {
                                    return r.run.level ? r.run.level.id == id : false;
                                }) || bc.levels[id].run;
                                bc.levels[id].run.run = run_to_bulk(<Run>bc.levels[id].run.run);
                            }
                        }
                    }))
                }));

                return user.bests;
            }
        }

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

    async apply_runs(runs: LeaderboardRunEntry[]): Promise<NewRecord[]> {
        let player_ids = _.chain(runs)
            .map(v => _.map(v.run.players, 'id'))
            .flatten()
            .reject(_.isNil)
            .uniq()
            .value();

        if(!player_ids.length)
            return []; // nothing to do

        let players = <{[id: string]: User}>_.keyBy(
            _.filter(await this.load(player_ids), 'id'),
            'id');

        if(!_.keys(players).length) {
            return []; // still nothing
        }

        let new_records: (NewRecord|null)[] = [];

        for(let run of runs) {
            for(let player of run.run.players) {
                // can only notify for non-guests (with ID)
                if(player.id)
                    new_records.push(
                        apply_personal_best(players[player.id], run));
            }
        }

        await this.save(_.values(players));

        return <NewRecord[]>_.reject(new_records, _.isNil);
    }
}
