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


        let updated_players: {[id: string]: speedrun_api.User} = {};
        if(lb.players != null && _.isArray(lb.players.data)) {
            updated_players = _.keyBy(lb.players.data);
        }

        // record runs
        for(let run of lb.runs) {
            run.run.players.map(v => v.id ? updated_players[v.id] : v);
            speedrun_api.normalize_run(<any>run.run);
        }

        let set_run_vals = <any>_.chain(lb.runs)
            .keyBy('run.id')
            .mapValues(JSON.stringify)
            .toPairs()
            .flatten()
            .value();
        
        if(set_run_vals.length)
            await scraper.storedb!.hmset(speedrun_db.locs.runs, ...set_run_vals);
        
        // record players
        // this applies players as well as set their personal best
        await speedrun_db.apply_leaderboard_bests(scraper.storedb!, lb, updated_players);

        for(let player_id in updated_players) {
            let player: speedrun_api.User = (<any>updated_players)[player_id];
            let indexes: { text: string, score: number, namespace?: string }[] = [];
            for(let name in player.names) {

                if(!player.names[name])
                    continue;

                let idx: any = { text: player.names[name].toLowerCase(), score: 100 - player.names[name].length };

                if(name != 'international')
                    idx.namespace = name;
                
                indexes.push(idx);
            }

            await scraper.indexer_players.add(player_id, indexes);
        }

        // write the leaderboard to db (excluding the players)
        speedrun_api.normalize_leaderboard(lb);
        await scraper.storedb!.hset(speedrun_db.locs.leaderboards, options.category_id + (options.level_id ? '_' + options.level_id : ''), JSON.stringify(_.omit(lb, 'players')));
    }
    catch(err) {
        console.error('loader/leaderboard: could not retrieve and process leaderboard/players:', options, err.statusCode || err);
        throw 'reschedule';
    }
}