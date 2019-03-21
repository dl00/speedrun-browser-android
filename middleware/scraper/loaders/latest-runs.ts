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

        let latest_run: string|null = options.latest_run || await scraper.storedb!.getset(speedrun_db.locs.latest_run, runs[0].id);

        if(!latest_run) {
            return;
        }

        // trigger a leaderboard update for each affected run
        for(let run of runs) {
            if(run.id == latest_run) {
                return;
            }

            console.log('loader/latest-runs: [SCHED]', run.id);
            
            await scraper.push_call({
                runid: scraper.join_runid([runid, <string>run.game, run.category + (run.level ? '_' + run.level : ''), 'leaderboard']),
                module: 'leaderboard',
                exec: 'pull_leaderboard',
                options: {
                    game_id: run.game,
                    category_id: run.category,
                    level_id: run.level
                }
            }, 5);
        }

        // reschedule with additional offset to go back sync
        await scraper.push_call({
            runid: runid,
            module: 'latest-runs',
            exec: 'latest_runs',
            options: {
                offset: res.pagination.offset + res.pagination.size,
                latest_run: latest_run
            }
        }, 1);
    }
    catch(err) {
        console.error('loader/latest-runs: could not retrieve and process pulls for latest runs:', options, err.statusCode || err);
        throw 'permanent';
    }
}