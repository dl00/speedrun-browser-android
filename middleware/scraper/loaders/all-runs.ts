import * as _ from 'lodash';

import { populate_run_sub_documents, Run, RunDao } from '../../lib/dao/runs';
import { UserDao } from '../../lib/dao/users';

import * as puller from '../puller';

import * as scraper from '../index';

export async function list_all_runs(runid: string, options: any) {
    try {
        const res = await puller.do_pull(scraper.storedb!,
            '/runs?max=200&orderby=submitted&direction=desc&offset=' + (options ? options.offset : 0) + '&embed=players');

        let runs: Run[] = res.data.data.map((run: any) => {
            run.game = {id: run.game};
            run.category = {id: run.category};

            if (run.level) {
                run.level = {id: run.level};
            }

            run.players = run.players.data;

            return run;
        });

        // handle player obj updates before anything else
        const updated_players = _.chain(res.data.data)
            .map('players')
            .flatten()
            .filter('id')
            .value();

        const user_dao = new UserDao(scraper.storedb!);
        const players = await user_dao.load(_.map(updated_players, 'id'), {skipComputed: true});
        await user_dao.save(players.map((v, i) => _.merge(v, updated_players[i] as any)));

        const pr = await populate_run_sub_documents(scraper.storedb!, runs);
        if (pr.drop_runs.length) {
            _.remove(runs, (r) => _.find(pr.drop_runs, (dr) => dr.id === r.id));
        }

        if (runs.length) {
            await new RunDao(scraper.storedb!).save(runs.map((run: Run) => {
                return {run: run}
            }));
        }

        if (res.data.pagination.max == res.data.pagination.size) {
            // schedule another load
            const new_offset = (options ? options.offset : 0) + res.data.pagination.size;
            await scraper.push_call({
                runid,
                module: 'all-runs',
                exec: 'list_all_runs',
                options: {
                    offset: new_offset,
                },
            }, 0);
        }
    } catch (err) {
        console.error('loader/all-runs: could not get a bulk listing of speedruns:', options, err.statusCode, err);
        throw new Error('reschedule');
    }
}
