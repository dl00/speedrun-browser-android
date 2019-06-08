import * as _ from 'lodash';

import { Run, RunDao, LeaderboardRunEntry } from '../../lib/dao/runs';
import { LeaderboardDao } from '../../lib/dao/leaderboards';
import { GameDao, BulkGame } from '../../lib/dao/games';
import { CategoryDao, Category } from '../../lib/dao/categories';
import { LevelDao, Level } from '../../lib/dao/levels';

import * as puller from '../puller';

import * as scraper from '../index';

export async function list_all_runs(runid: string, options: any) {
    try {
        let res = await puller.do_pull(scraper.storedb!,
            '/runs&max=200&offset=' + (options ? options.offset : 0));

        let runs: Run[] = res.data.data;

        let game_ids = <string[]>_.uniq(_.map(runs, 'game'));
        let category_ids = <string[]>_.uniq(_.map(runs, 'category'));
        let level_ids = <string[]>_.uniq(_.map(runs, 'level'));

        let leaderboard_ids = <string[]>_.uniq(_.map(runs, run => run.category + (run.level ? '_' + run.level : '')));

        let games = _.zipObject(game_ids, await new GameDao(scraper.storedb!).load(game_ids));
        let categories = _.zipObject(category_ids, await new CategoryDao(scraper.storedb!).load(category_ids));
        let levels = _.zipObject(level_ids, await new LevelDao(scraper.storedb!).load(level_ids));

        for(let run of runs) {
            run.game = <BulkGame>games[<string>run.game];
            run.category = <Category>categories[<string>run.category];
            run.level = <Level|null>levels[<string>run.level];
        }

        let leaderboards = await new LeaderboardDao(scraper.storedb!).load(leaderboard_ids);

        let lbrs: LeaderboardRunEntry[] = runs.map((run) => {

        });

        await new RunDao(scraper.storedb!).save(lbrs);

        if(res.data.pagination.max == res.data.pagination.size) {
            // schedule another load
            await scraper.push_call({
                runid: runid,
                module: 'all-runs',
                exec: 'list_all_runs',
                options: {
                    offset: (options ? options.offset : 0) + res.data.pagination.size
                }
            }, 0);
        }
    }
    catch(err) {
        console.error('loader/all-runs: could not get a bulk listing of speedruns:', options, err.statusCode);
        throw 'reschedule';
    }
}
