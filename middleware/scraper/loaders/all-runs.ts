import * as _ from 'lodash';

import { Run, RunDao, LeaderboardRunEntry } from '../../lib/dao/runs';
import { Leaderboard, LeaderboardDao } from '../../lib/dao/leaderboards';
import { GameDao, Game, BulkGame } from '../../lib/dao/games';
import { CategoryDao, Category } from '../../lib/dao/categories';
import { LevelDao, Level } from '../../lib/dao/levels';
import { UserDao, BulkUser } from '../../lib/dao/users';

import * as puller from '../puller';

import * as scraper from '../index';

export interface PopulateRunResponse {
    drop_runs: Run[],
    games: {[id: string]: Game|null}
    categories: {[id: string]: Category|null}
    levels: {[id: string]: Level|null}
}

// returns a list of runs which cannot be processed because the game is missing, or similar
export async function populate_run_sub_documents(runs: Run[]): Promise<PopulateRunResponse> {
    let game_ids = <string[]>_.uniq(_.map(runs, 'game'));
    let category_ids = <string[]>_.uniq(_.map(runs, 'category'));
    let level_ids = <string[]>_.uniq(_.map(runs, 'level'));

    let player_ids = <string[]>_.uniq(_.flatten(_.map(runs, (run) => {
        return _.reject(_.map(run.players, 'id'), _.isNil);
    })));

    let games = _.zipObject(game_ids, await new GameDao(scraper.storedb!).load(game_ids));
    let categories = _.zipObject(category_ids, await new CategoryDao(scraper.storedb!).load(category_ids));
    let levels = _.zipObject(level_ids, await new LevelDao(scraper.storedb!).load(level_ids));
    let players = _.zipObject(player_ids, await new UserDao(scraper.storedb!).load(player_ids));

    // list of runs we are skipping processing
    let drop_runs: Run[] = [];

    for(let run of runs) {
        if(!games[<string>run.game] || !categories[<string>run.category]) {
            drop_runs.push(run);
            continue;
        }

        run.game = <BulkGame>games[<string>run.game];
        run.category = <Category>categories[<string>run.category];
        run.level = <Level|null>levels[<string>run.level];

        // handle special cases for users
        for(let i = 0;i < run.players.length;i++) {
            if(!run.players[i].id)
                continue;
            if(!players[run.players[i].id]) {
                // new player
                // currently the best way to solve this is to do a game resync
                drop_runs.push(run);
                continue;
            }

            run.players[i] = <BulkUser>players[run.players[i].id];
        }
    }

    return {
        drop_runs: drop_runs,
        games: games,
        categories: categories,
        levels: levels
    };
}

export async function list_all_runs(runid: string, options: any) {
    try {
        let res = await puller.do_pull(scraper.storedb!,
            '/runs?max=200&offset=' + (options ? options.offset : 0));

        let runs: Run[] = res.data.data;

        let pr = await populate_run_sub_documents(runs);
        if(pr.drop_runs.length)
            runs = _.remove(runs, r => _.findIndex(pr.drop_runs, r) !== -1);

        let leaderboard_ids = <string[]>_.map(runs, run => run.category + (run.level ? '_' + run.level : ''));

        let leaderboard_ids_deduped = _.uniq(leaderboard_ids);
        let leaderboards = <{[id: string]: Leaderboard}>_.zipObject(leaderboard_ids_deduped, await new LeaderboardDao(scraper.storedb!).load(leaderboard_ids_deduped));

        let lbrs: LeaderboardRunEntry[] = runs.map((run, i) => {

            if(!leaderboards[leaderboard_ids[i]])
                return { run: run };

            let entry = _.find(leaderboards[leaderboard_ids[i]].runs, r => r.run.id === run.id);

            return {
                place: entry ? entry.place : null,
                run: run
            }
        });

        await new RunDao(scraper.storedb!).save(lbrs);

        if(res.data.pagination.max == res.data.pagination.size) {
            // schedule another load
            let new_offset = (options ? options.offset : 0) + res.data.pagination.size;
            await scraper.push_call({
                runid: runid,
                module: 'all-runs',
                exec: 'list_all_runs',
                options: {
                    offset: new_offset
                }
            }, 0);
        }
    }
    catch(err) {
        console.error('loader/all-runs: could not get a bulk listing of speedruns:', options, err.statusCode, err);
        throw 'reschedule';
    }
}
