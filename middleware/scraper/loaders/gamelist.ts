// Generates an effective, searchable index of games to browse. Takes into account:
// * Game popularity (according to wikipedia data)
// * Autocomplete (indexer) indexing

import * as speedrun_api from '../../lib/speedrun-api';
import * as speedrun_db from '../../lib/speedrun-db';
import * as wikipedia from '../../lib/wikipedia';
import request from '../../lib/request';

import * as scraper from '../index';

import * as moment from 'moment';

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
        let res = await request(speedrun_api.API_PREFIX + '/games/' + options.id);

        let game: speedrun_api.Game = res.data;

        // write the game to db
        await scraper.storedb!.hset(speedrun_db.locs.games, options.id, JSON.stringify(game));

        // search for the article on wikipedia
        let wikipedia_pages = await wikipedia.search(game.id);

        // by default, have very few page views because if no results, then it is undoubtebly not a very notable game
        let wikipedia_pageviews = 1;
        if(wikipedia_pages.search.length) {
            // load pageviews (just use the top search result for now)
            wikipedia_pageviews = await wikipedia.get_pageviews(wikipedia_pages.search[0].title.replace(' ', '_'), moment().subtract(1, 'month'), moment());
        }


        let indexes: { text: string, score: number, namespace?: string }[] = [];
        for(let name in game.names) {

            if(!game.names[name])
                continue;

            let idx: any = { text: game.names[name], score: wikipedia_pageviews };

            if(name != 'international')
                idx.namespace = name;
            
            indexes.push(idx);
        }

        // install autocomplete entry
        await scraper.indexer.add(game.id, indexes);

        const LEADERBOARD_CACHE_PULL = {
            runid: scraper.join_runid([runid, options.id, '{{ id }}']),
            module: 'cache',
            exec: 'load',
            options: {
                url: speedrun_api.API_PREFIX + '/leaderboards/' + options.id + '/category/{{ id }}?embed=platforms,regions',
                id: '{{ id }}',
                db_loc: [
                    {
                        hash: 'leaderboards'
                    }
                ]
            }
        };

        const CATEGORY_CACHE_PULL = {
            runid: scraper.join_runid([runid, game.id, 'categories']),
            module: 'cache',
            exec: 'load',
            options: {
                url: speedrun_api.API_PREFIX + '/games/' + options.id + '/categories?embed=variables',
                id: options.id,
                db_loc: [{
                    hash: 'categories',
                    sub: 'categories'
                }],

                chain: [
                    // also retrieve full leaderboard for category
                    LEADERBOARD_CACHE_PULL
                ]
            }
        };

        // unfortunately we have to load the categories for a game in a separate request...
        await scraper.push_call(CATEGORY_CACHE_PULL, 9);
    }
    catch(err) {
        console.error('Could not retrieve single game entry:', options, err.statusCode);
        throw 'reschedule';
    }
}