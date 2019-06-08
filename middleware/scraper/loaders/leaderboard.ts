// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import { GameDao } from '../../lib/dao/games';
import { CategoryDao } from '../../lib/dao/categories';
import { LevelDao } from '../../lib/dao/levels';
import { Leaderboard, LeaderboardDao, correct_leaderboard_run_places } from '../../lib/dao/leaderboards';
import { Run, RunDao } from '../../lib/dao/runs';
import { User, UserDao } from '../../lib/dao/users';

import * as puller from '../puller';

import * as scraper from '../index';
import * as push_notify from '../push-notify';
import { Variable } from '../../lib/speedrun-api';

export async function pull_leaderboard(runid: string, options: any) {
    try {
        let res = await puller.do_pull(scraper.storedb!, '/leaderboards/' + options.game_id +
            (options.level_id ? '/level/' + options.level_id + '/' + options.category_id : '/category/' + options.category_id) + '?embed=players');

        let lb: Leaderboard = res.data.data;

        let games = await new GameDao(scraper.storedb!).load(<string>lb.game);
        let categories = await new CategoryDao(scraper.storedb!).load(<string>lb.category);
        let levels = await new LevelDao(scraper.storedb!).load(<string>lb.level);

        let game = games[0];
        let category = categories[0];
        let level = levels[0];

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

        let updated_players: {[id: string]: User} = {};
        if(lb.players != null && _.isArray(lb.players.data)) {
            updated_players = _.keyBy(lb.players.data, player => player.id || '');
        }

        correct_leaderboard_run_places(lb, <Variable[]>category.variables);

        // record runs
        for(let run of lb.runs) {
            run.run.players = run.run.players.map(v => v.id && updated_players[v.id] ? updated_players[v.id] : v);
            (<Run>run.run).game = game;
            (<Run>run.run).category = category;
            if(level)
                (<Run>run.run).level = level;
        }

        // save the runs
        await new RunDao(scraper.storedb!, scraper.config).save(lb.runs);

        // record players
        // this applies players as well as set their personal best
        let new_records = await new UserDao(scraper.storedb!).apply_leaderboard_bests(lb, updated_players);

        // write the leaderboard to db (excluding the players)
        await new LeaderboardDao(scraper.storedb!).save(<Leaderboard>_.omit(lb, 'players'));

        // send push notifications as needed. All notifications are triggered by a player record change
        for(let nr of new_records) {
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
