// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as speedrun_api from '../../lib/speedrun-api';
import * as speedrun_db from '../../lib/speedrun-db';
import request from '../../lib/request';

import * as scraper from '../index';

export async function pull_latest_runs(runid: string, options: any) {
    try {
        let res = await request(speedrun_api.API_PREFIX + `/runs?status=verified&orderby=verify-date&direction=desc&offset=${options.offset || 0}&max=200`);

        let runs: speedrun_api.Run[] = res.data;

        if(!runs.length)
            return;

        let latest_run_verify_date: string|null = options.latest_run_verify_date ||
            await scraper.storedb!.getset(speedrun_db.locs.latest_run_verify_date,
                runs[0].status['verify-date']);

        if(!latest_run_verify_date) {
            return;
        }

        // trigger a leaderboard update for each affected run
        let lb_pulls: {[key: string]: boolean} = {};

        for(let run of runs) {
            if(run.status['verify-date'] <= latest_run_verify_date) {
                return;
            }

            let rid = scraper.join_runid(
                [runid, <string>run.game, run.category + (run.level ? '_' + run.level : ''),
                'leaderboard']
            );

            if(lb_pulls[rid])
                continue;

            lb_pulls[rid] = true;

            let options = {
                game_id: run.game,
                category_id: run.category,
                level_id: run.level
            };

            await scraper.push_call({
                runid: rid,
                module: 'leaderboard',
                exec: 'pull_leaderboard',
                options: options
            }, 5);
        }

        // reschedule with additional offset to go back sync
        await scraper.push_call({
            runid: runid,
            module: 'latest-runs',
            exec: 'pull_latest_runs',
            options: {
                offset: res.pagination.offset + res.pagination.size,
                latest_run_verify_date: latest_run_verify_date
            }
        }, 1);
    }
    catch(err) {
        console.error('loader/latest-runs: could not retrieve and process pulls for latest runs:', options, err.statusCode || err);
        throw 'permanent';
    }
}
