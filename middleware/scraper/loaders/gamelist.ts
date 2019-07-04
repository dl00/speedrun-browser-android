// Generates an effective, searchable index of games to browse. Takes into account:
// * Game popularity (according to wikipedia data)
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as puller from '../puller';

import { Game, GameDao, normalize_game } from '../../lib/dao/games';
import { Genre, GenreDao } from '../../lib/dao/genres';
import { Category, CategoryDao } from '../../lib/dao/categories';
import { Level, LevelDao } from '../../lib/dao/levels';

import * as scraper from '../index';

export async function list_all_games(runid: string, options: any) {

    try {
        let res = await puller.do_pull(scraper.storedb!,
            '/games?_bulk=yes&max=1000&offset=' + (options ? options.offset : 0));

        for(let game of <Game[]>res.data.data) {
            await scraper.push_call({
                runid: scraper.join_runid([runid, game.id]),
                module: 'gamelist',
                exec: 'pull_game',
                options: {
                    id: game.id
                }
            }, 10);
        }

        if(res.data.pagination.max == res.data.pagination.size) {
            // schedule another load
            await scraper.push_call({
                runid: runid,
                module: 'gamelist',
                exec: 'list_all_games',
                options: {
                    offset: (options ? options.offset : 0) + res.data.pagination.size
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
        let res = await puller.do_pull(scraper.storedb!, '/games/' + options.id + '?embed=platforms,regions,genres');

        let game: Game = res.data.data;

        normalize_game(game);

        // write the game to db
        await new GameDao(scraper.storedb!, scraper.config).save(game);

        // write any genres to the db
        if(game.genres && (<Genre[]>game.genres).length) {
            let genre_dao = await new GenreDao(scraper.storedb!);
            await genre_dao.save(<Genre[]>game.genres);
            for(let genre of <Genre[]>game.genres) {
                await genre_dao.rescore_genre(genre.id);
            }
        }

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
        console.error('loader/gamelist: could not retrieve single game entry:', options, err.statusCode, err, _.get(err, 'previousErrors[0]'));
        throw 'reschedule';
    }
}

export async function pull_game_categories(runid: string, options: any) {
    try {
        let res = await puller.do_pull(scraper.storedb!, '/games/' + options.id + '/categories?embed=variables');

        let categories: Category[] = res.data.data;

        // write the categories to db
        await new CategoryDao(scraper.storedb!).apply_for_game(options.id, categories.map(v => {
            v.game = options.id;
            return v;
        }));

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
        let res = await puller.do_pull(scraper.storedb!, '/games/' + options.id + '/levels');

        let levels: Level[] = res.data.data;

        await new LevelDao(scraper.storedb!).apply_for_game(options.id, levels.map(v => {
            v.game = options.id;
            return v;
        }));

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
    catch(err) {
        console.error('loader/gamelist: could not retrieve levels for single game:', options, err.statusCode);
        throw 'reschedule';
    }
}

export async function postprocess_game(_runid: string, options: any) {
    try {
        await new GameDao(scraper.storedb!, scraper.config).rescore_games(options.id);
    } catch(err) {
        // this should not happen unless there is some internal problem with the previous steps
        console.error('loader/gamelist: could not postprocess game:', options, err);
        throw 'reschedule';
    }
}
