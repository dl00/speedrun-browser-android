// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';
import * as moment from 'moment';

import * as speedrun_api from '../../lib/speedrun-api';
import * as speedrun_db from '../../lib/speedrun-db';
import request from '../../lib/request';

import * as scraper from '../index';
import * as push_notify from '../push-notify';

export async function pull_leaderboard(runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + '/leaderboards/' + options.game_id +
            (options.level_id ? '/level/' + options.level_id + '/' + options.category_id : '/category/' + options.category_id) + '?embed=players');

        let lb: speedrun_api.Leaderboard = res.data;

        let res2 = await scraper.storedb!.multi()
            .hget(speedrun_db.locs.games, <string>lb.game)
            .hget(speedrun_db.locs.categories, <string>lb.game)
            .hget(speedrun_db.locs.levels, <string>lb.game)
            .exec();

        let game = JSON.parse(res2[0][1]);
        let category = _.find(JSON.parse(res2[1][1]), v => v.id === options.category_id);
        let level = _.find(JSON.parse(res2[2][1]), v => v.id === options.level_id);

        if(!game || !category) {
            // this is a new game/category, bail and refresh the new
            await scraper.push_call({
                runid: scraper.join_runid([runid, <string>lb.game]),
                module: 'gamelist',
                exec: 'pull_game',
                options: {
                    id: lb.game
                }
            }, 1);

            return;
        }

        let updated_players: {[id: string]: speedrun_api.User} = {};
        if(lb.players != null && _.isArray(lb.players.data)) {
            updated_players = _.keyBy(lb.players.data, player => player.id || '');
        }

        speedrun_api.correct_leaderboard_run_places(lb, category.variables);

        // record runs
        for(let run of lb.runs) {
            run.run.players = run.run.players.map(v => v.id && updated_players[v.id] ? updated_players[v.id] : v);
            (<speedrun_api.Run>run.run).game = game;
            (<speedrun_api.Run>run.run).category = category;
            (<speedrun_api.Run>run.run).level = level;
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
        let new_records = await speedrun_db.apply_leaderboard_bests(scraper.storedb!, lb, updated_players);

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

            if(player.names && player.names['international'])
                await scraper.storedb!.hset(speedrun_db.locs.player_abbrs, player.names['international'], player_id)
        }

        // write the leaderboard to db (excluding the players)
        speedrun_api.normalize_leaderboard(lb);
        await scraper.storedb!.hset(speedrun_db.locs.leaderboards, options.category_id + (options.level_id ? '_' + options.level_id : ''), JSON.stringify(_.omit(lb, 'players')));

        // send push notifications as needed. All notifications are triggered by a player record change
        for(let nr of new_records) {
            await scraper.storedb!.multi()
                .zadd(speedrun_db.locs.verified_runs, moment((<speedrun_api.Run>nr.new_run.run).status['verify-date']).unix().toString(), nr.new_run.run.id)
                .zremrangebyrank(speedrun_db.locs.verified_runs, 0, -scraper.config.scraper.db.latestRunsLength - 1)
                .exec();


            if(nr.new_run.place == 1) {
                // new record on this category/level, send notification
                push_notify.notify_game_record(nr, game, category, level);
            }

            // this should be a personal best. send notification to all attached players who are regular users
            for(let pid of nr.new_run.run.players) {
                if(updated_players[pid.id])
                    push_notify.notify_player_record(nr, updated_players[pid.id], game, category, level);
            }
        }
    }
    catch(err) {
        console.error('loader/leaderboard: could not retrieve and process leaderboard/players:', options, err.statusCode || err);
        throw 'reschedule';
    }
}
