// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as speedrun_api from '../../lib/speedrun-api';
import * as speedrun_db from '../../lib/speedrun-db';
import request from '../../lib/request';

import * as scraper from '../index';

export async function pull_leaderboard(_runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + '/leaderboards/' + options.game_id + 
            (options.level_id ? '/level/' + options.level_id + '/' + options.category_id : '/category/' + options.category_id) + '?embed=players');

        let lb: speedrun_api.Leaderboard = res.data;

        // write the leaderboard to db (excluding the players)
        speedrun_api.normalize_leaderboard(lb);
        await scraper.storedb!.hset(speedrun_db.locs.leaderboards, options.category_id + (options.level_id ? '_' + options.level_id : ''), JSON.stringify(_.omit(lb, 'players')));

        if(_.keys(lb.players).length > 0) {
            res = await scraper.storedb!.multi()
                .hget(speedrun_db.locs.games, options.game_id)
                .hget(speedrun_db.locs.categories, options.game_id)
                .hget(speedrun_db.locs.levels, options.game_id)
                .hmget(speedrun_db.locs.players, ..._.keys(lb.players))
                .exec();
            
            let game: speedrun_api.Game = JSON.parse(res[0][1]);
            let category: speedrun_api.Category = _.find(JSON.parse(res[1][1]), v => v.id == options.category_id);
            let level: speedrun_api.Level = _.find(JSON.parse(res[2][1]), v => v.id == options.level_id);

            // store/update player information
            // TODO: not sure why, but typescript errors with wrong value here?
            let players: {[id: string]: speedrun_api.User} = <any>_.chain(<(string|null)[]>res[1][3])
                .remove(_.isNil)
                .map(JSON.parse)
                .keyBy('id')
                .value();

            // set known leaderboard information to player
            _.merge(players, lb.players);

            for(let i = 0;i < lb.runs.length;i++) {
                for(let player of lb.runs[i].run.players) {
                    speedrun_db.apply_personal_best(players[player.id], game, category, level, lb, i);
                }
            }
            
            let flat_players: string[] = <any>_.chain(players)
                .mapValues(JSON.stringify)
                .toPairs()
                .flatten()
                .value();

            await scraper.storedb!.hmset(speedrun_db.locs.players, ...<[string, string]>flat_players);
        }
    }
    catch(err) {
        console.error('loader/leaderboard: could not retrieve and process leaderboard/players:', options, err.statusCode || err);
        throw 'reschedule';
    }
}