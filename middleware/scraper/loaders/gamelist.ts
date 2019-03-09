// Generates an effective, searchable index of games to browse. Takes into account:
// * Game popularity (according to wikipedia data)
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as speedrun_api from '../../lib/speedrun-api';
import * as speedrun_db from '../../lib/speedrun-db';
import request from '../../lib/request';

import * as scraper from '../index';

export async function list_all_games(runid: string, options: any) {

    try {
        let res = await request(speedrun_api.API_PREFIX + '/games', {
            qs: {
                _bulk: 'yes',
                max: 1000,
                offset: options ? options.offset : 0
            }
        });

        for(let game of <speedrun_api.Game[]>res.data) {
            await scraper.push_call({
                runid: scraper.join_runid([runid, game.id]),
                module: 'gamelist',
                exec: 'pull_game',
                options: {
                    id: game.id
                }
            }, 10);
        }

        if(res.pagination.max == res.pagination.size) {
            // schedule another load
            await scraper.push_call({
                runid: runid,
                module: 'gamelist',
                exec: 'list_all_games',
                options: {
                    offset: (options ? options.offset : 0) + res.pagination.size
                }
            }, 0);
        }
    }
    catch(err) {
        console.error('loader/gamelist: could not get a bulk listing of games:', options, err.statusCode);
        throw 'reschedule';
    }
}

export async function pull_game(runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + '/games/' + options.id + '?embed=platforms,regions');

        let game: speedrun_api.Game = res.data;

        // write the game to db
        speedrun_api.normalize_game(game);
        await scraper.storedb!.hset(speedrun_db.locs.games, options.id, JSON.stringify(game));
        await scraper.storedb!.hset(speedrun_db.locs.game_abbrs, game.abbreviation, game.id);

        // unfortunately we have to load the categories for a game in a separate request...
        await scraper.push_call({
            runid: scraper.join_runid([runid, options.id, 'categories']),
            module: 'gamelist',
            exec: 'pull_game_categories',
            options: {
                id: options.id
            }
        }, 9);
    }
    catch(err) {
        console.error('loader/gamelist: could not retrieve single game entry:', options, err.statusCode);
        throw 'reschedule';
    }
}

export async function pull_game_categories(runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + '/games/' + options.id + '/categories?embed=variables');

        let categories: speedrun_api.Category[] = res.data;

        // write the categories to db
        for(let category of categories) {
            speedrun_api.normalize_category(category);
        }
        await scraper.storedb!.hset(speedrun_db.locs.categories, options.id, JSON.stringify(categories));

        let grouped_categories = _.groupBy(categories, 'type');

        if(grouped_categories['per-level']) {
            await scraper.push_call({
                runid: scraper.join_runid([runid, options.id, 'levels']),
                module: 'gamelist',
                exec: 'pull_game_levels',
                options: {
                    categories: _.map(grouped_categories['per-level'], 'id'),
                    id: options.id
                }
            }, 9);
        }
        
        if(grouped_categories['per-game']) {
            for(let category of grouped_categories['per-game']) {
                await scraper.push_call({
                    runid: scraper.join_runid([runid, options.id, category.id, 'leaderboard']),
                    module: 'leaderboard',
                    exec: 'pull_leaderboard',
                    options: {
                        game_id: options.id,
                        category_id: category.id
                    }
                }, 8);
            }
        }

        await scraper.push_call({
            runid: scraper.join_runid([runid, options.id, 'postprocess']),
            module: 'gamelist',
            exec: 'postprocess_game',
            skip_wait: true,
            options: {
                id: options.id
            }
        }, 9);
    }
    catch(err) {
        console.error('loader/gamelist: could not retrieve categories for single game:', options, err.statusCode);
        throw 'reschedule';
    }
}

export async function pull_game_levels(runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + '/games/' + options.id + '/levels');

        let levels: speedrun_api.Level[] = res.data;


        if(levels.length) {
            for(let level of levels) {
                speedrun_api.normalize(level);
            }
            await scraper.storedb!.hset(speedrun_db.locs.levels, options.id, JSON.stringify(levels));
            for(let level of levels) {
                for(let category_id of options.categories) {
                    await scraper.push_call({
                        runid: scraper.join_runid([runid, options.id, level.id + '_' + category_id, 'leaderboard']),
                        module: 'leaderboard',
                        exec: 'pull_leaderboard',
                        options: {
                            game_id: options.id,
                            category_id: category_id,
                            level_id: level.id
                        }
                    }, 8);
                }
            }
        }
    }
    catch(err) {
        console.error('loader/gamelist: could not retrieve levels for single game:', options, err.statusCode);
        throw 'reschedule';
    }
}

export async function postprocess_game(_runid: string, options: any) {
    try {
        let raw_game = await scraper.storedb!.hget(speedrun_db.locs.games, options.id);
        if(!raw_game)
            throw 'game does not exist in db!';

        await speedrun_db.rescore_game(scraper.storedb!, scraper.indexer, JSON.parse(raw_game));
    } catch(err) {
        // this should not happen unless there is some internal problem with the previous steps
        console.error('loader/gamelist: could not postprocess game:', options, err);
        throw 'reschedule';
    }
}