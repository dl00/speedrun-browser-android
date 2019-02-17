import * as _ from 'lodash';
import * as moment from 'moment';
import * as ioredis from 'ioredis';

import * as speedrun_api from './speedrun-api';

/// key values/prefixes for which datasets can be found
export const locs: {[index: string]: string} = {
    game_rank: 'game_rank',
    games: 'games',
    runs: 'runs',
    categories: 'categories',
    levels: 'levels',
    leaderboards: 'leaderboards'
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

        // we only care about verified runs
        if(run.run.status.status !== 'verified')
            continue;

        let d = moment(run.run.submitted);

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