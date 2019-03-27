import * as _ from 'lodash';
import * as moment from 'moment';
import * as ioredis from 'ioredis';

import * as speedrun_api from './speedrun-api';

/// information about a new PB from a player
export interface NewRecord {
    old_run: speedrun_api.LeaderboardRunEntry,
    new_run: speedrun_api.LeaderboardRunEntry
}

/// key values/prefixes for which datasets can be found
export const locs: {[index: string]: string} = {
    game_rank: 'game_rank',
    game_abbrs: 'game_abbrs',
    player_abbrs: 'player_abbrs',
    latest_run: 'latest_run',
    games: 'games',
    runs: 'runs',
    categories: 'categories',
    levels: 'levels',
    leaderboards: 'leaderboards',
    players: 'players'
}

/// Finds all leaderboards for a particular game by id
/// Return a list of leaderboard ids
export async function list_leaderboards(db: ioredis.Redis, game_id: string) {
    // first, read list of categories and levels
    let categories = [];
    let categories_raw = await db.hget(locs.categories, game_id);
    if(categories_raw)
        categories = JSON.parse(categories_raw);
    
    if(!categories.length)
        return [];
    
    let levels = [];
    let levels_raw = await db.hget(locs.levels, game_id);
    if(levels_raw)
        levels = JSON.parse(levels_raw);
    
    let grouped_categories = _.groupBy(categories, 'type');
    
    let leaderboard_ids = _.map(grouped_categories['per-game'], 'id') || [];

    if(grouped_categories['per-level']) {
        for(let per_level_category of grouped_categories['per-level']) {
            for(let level of levels) {
                leaderboard_ids.push(per_level_category.id + '_' + level.id)
            }
        }
    }

    return leaderboard_ids;
}

export function generate_leaderboard_score(leaderboard: speedrun_api.Leaderboard) {
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

export async function rescore_game(db: ioredis.Redis, indexer: any, game: speedrun_api.Game) {

    let game_score = 0;

    // look at the game's leaderboards, for categories not levels. Find the number of records
    let categories_raw = await db.hget(locs.categories, game.id);

    if(categories_raw) {
        let categories = JSON.parse(categories_raw);

        categories = _.filter(categories, c => c.type == 'per-game');

        let div = 1 + Math.log(Math.max(1, categories.length));

        if(categories.length) {
            let leaderboards_raw = await db.hmget(locs.leaderboards, ..._.map(categories, 'id'));
            if(leaderboards_raw)
                game_score = Math.ceil(_.chain(leaderboards_raw)
                    .reject(_.isNil)
                    .map(JSON.parse)
                    .map(generate_leaderboard_score)
                    .sum().value() / div);
        }
    }

    let indexes: { text: string, score: number, namespace?: string }[] = [];

    indexes.push({ text: game.abbreviation.toLowerCase(), score: game_score });
    
    for(let name in game.names) {

        if(!game.names[name])
            continue;

        let idx: any = { text: game.names[name].toLowerCase(), score: game_score };

        if(name != 'international')
            idx.namespace = name;
        
        indexes.push(idx);
    }

    // install autocomplete entry
    await indexer.add(game.id, indexes);

    // install master rank list
    await db.zadd(locs.game_rank, game_score.toString(), game.id);
}

// add/update the given personal best entry for the given user
export function apply_personal_best(player: speedrun_api.User, 
            game: speedrun_api.Game, category: speedrun_api.Category, 
            level: speedrun_api.Level|null, 
            lb: speedrun_api.Leaderboard, 
            index: number): NewRecord|null {

    let category_run: speedrun_api.CategoryPersonalBests = {
        id: category.id,
        name: category.name,
        type: category.type
    };

    let game_run: speedrun_api.GamePersonalBests = {
        id: game.id,
        names: game.names,
        assets: speedrun_api.game_assets_to_bulk(game.assets),
        categories: {}
    };

    let best_run: { place: number, run: speedrun_api.BulkRun } = {
        place: lb.runs[index].place,
        run: lb.runs[index].run
    };

    let old_run = null;

    if(level) {

        old_run = _.get(player, `bests["${game.id}"].categories["${category.id}"].levels["${level.id}"].run`);
        if(old_run.run.id == best_run.run.id)
            return null;

        let level_run: speedrun_api.LevelPersonalBests = {
            id: level.id,
            name: level.name,

            run: best_run
        };

        category_run.levels = {};
        category_run.levels[level.id] = level_run;
    }
    else {
        old_run = _.get(player, `bests["${game.id}"].categories["${category.id}"].run.run.id`);
        if(old_run.run.id == best_run.run.id)
            return null;

        category_run.run = best_run;
    }

    game_run.categories[category.id] = category_run;

    let new_bests: {[id: string]: speedrun_api.GamePersonalBests} = {};

    new_bests[game.id] = game_run;

    _.merge(player, {bests: new_bests});

    return {
        old_run: old_run,
        new_run: best_run
    };
}

export async function apply_leaderboard_bests(db: ioredis.Redis, lb: speedrun_api.Leaderboard, updated_players: {[id: string]: speedrun_api.User}): Promise<NewRecord[]> {

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

    let res = await db.multi()
        .hget(locs.games, game_id)
        .hget(locs.categories, game_id)
        .hget(locs.levels, game_id)
        .hmget(locs.players, ...player_ids)
        .exec();
    
    let game: speedrun_api.Game = JSON.parse(res[0][1]);
    let category: speedrun_api.Category = _.find(JSON.parse(res[1][1]), v => v.id == category_id);
    let level: speedrun_api.Level = _.find(JSON.parse(res[2][1]), v => v.id == level_id);

    if(!category)
        return [];

    // store/update player information
    // TODO: not sure why, but typescript errors with wrong value here?
    let players: {[id: string]: speedrun_api.User} = <any>_.chain(<(string|null)[]>res[3][1])
        .reject(_.isNil)
        .map(JSON.parse)
        .keyBy('id')
        .value();

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
    
    let flat_players: string[] = <any>_.chain(players)
        .mapValues(JSON.stringify)
        .toPairs()
        .flatten()
        .value();

    await db.hmset(locs.players, ...<[string, string]>flat_players);

    return <NewRecord[]>_.reject(new_records, _.isNil);
}