import * as _ from 'lodash';

import { Run, RunDao, LeaderboardRunEntry, populate_run_sub_documents } from '../../lib/dao/runs';
import { UserDao } from '../../lib/dao/users';
import { Leaderboard, LeaderboardDao } from '../../lib/dao/leaderboards';

import * as puller from '../puller';

import * as scraper from '../index';

export async function list_all_runs(runid: string, options: any) {
    try {
        let res = await puller.do_pull(scraper.storedb!,
            '/runs?max=200&offset=' + (options ? options.offset : 0) + '&embed=players');

        // handle player obj updates before anything else
        let updated_players = _.chain(res.data.data)
            .map('players.data')
            .flatten()
            .value();

        let user_dao = new UserDao(scraper.storedb!);
        let players = await user_dao.load(_.map(updated_players, 'id'));
        await user_dao.save(players.map((v, i) => _.merge(v, <any>updated_players[i])));

        let runs: Run[] = res.data.data.map((run: any) => {
            run.game = {id: run.game};
            run.category = {id: run.category};

            if(run.level)
                run.level = {id: run.level};

            run.players = run.players.data;

            return run;
        });

        let pr = await populate_run_sub_documents(scraper.storedb!, runs);
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
