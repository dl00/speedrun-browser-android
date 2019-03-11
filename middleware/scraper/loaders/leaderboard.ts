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

        await speedrun_db.apply_leaderboard_bests(scraper.storedb!, lb);

        // add players to the autocomplete
        for(let player_id in lb.players) {
            let player: speedrun_api.User = (<any>lb.players)[player_id];
            let indexes: { text: string, score: number, namespace?: string }[] = [];
            for(let name in player.names) {

                if(!player.names[name])
                    continue;

                let idx: any = { text: player.names[name].toLowerCase(), score: 1 };

                if(name != 'international')
                    idx.namespace = name;
                
                indexes.push(idx);
            }

            await scraper.indexer_players.add(player_id, indexes);
        }
    }
    catch(err) {
        console.error('loader/leaderboard: could not retrieve and process leaderboard/players:', options, err.statusCode || err);
        throw 'reschedule';
    }
}