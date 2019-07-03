// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as puller from '../puller';

import * as scraper from '../index';

import { Run, RunDao, LeaderboardRunEntry } from '../../lib/dao/runs';
import { UserDao } from '../../lib/dao/users';
import { Leaderboard, LeaderboardDao, add_leaderboard_run } from '../../lib/dao/leaderboards';

import { populate_run_sub_documents } from './all-runs';
import { BulkGame } from '../../lib/dao/games';
import { BulkCategory } from '../../lib/dao/categories';
import { BulkLevel } from '../../lib/dao/levels';
import { Variable } from '../../lib/speedrun-api';

export async function pull_latest_runs(runid: string, options: any) {
    try {

        let run_date_property: 'status.verify-date'|'submitted' = options.verified ? 'status.verify-date' : 'submitted';
        let latest_run_redis_property: 'latest_run_verify_date'|'latest_run_new_date' = options.verified ? 'latest_run_verify_date' : 'latest_run_new_date';

        let res = await puller.do_pull(scraper.storedb!,
            `/runs?status=verified&orderby=${options.verified ? 'verify-date' : 'submitted'}&direction=desc&offset=${options.offset || 0}&max=200`);

        let runs: Run[] = res.data.data;

        if(!runs.length)
            return;

        let latest_run_date: string|null = options.latest_run_date ||
            await scraper.storedb!.redis.getset(latest_run_redis_property,
                _.get(runs[0], run_date_property));

        if(!latest_run_date) {
            return;
        }

        let remove_after = _.findIndex(runs, run => _.get(run, run_date_property) <= latest_run_date!);
        runs.splice(remove_after, 100000000);

        if(!runs.length)
            return;

        // install runs on the database
        let pr = await populate_run_sub_documents(runs);
        if(pr.drop_runs.length)
            runs = _.remove(runs, r => _.findIndex(pr.drop_runs, r) !== -1);

        // download missing runs
        for(let mr of pr.drop_runs) {
            if(mr.game)
                await scraper.push_call({
                    runid: runid + '/' + mr.id,
                    module: 'gamelist',
                    exec: 'pull_game',
                    options: {
                        id: (<BulkGame>mr.game).id || mr.game
                    }
                }, 5);
        }

        if(!runs.length)
            return;

        let leaderboard_ids = <string[]>_.map(runs, run =>
            (<BulkCategory>run.category).id +
            (run.level ? '_' + (<BulkLevel>run.level).id : ''));

        let leaderboard_ids_deduped = _.uniq(leaderboard_ids);
        let leaderboards = <{[id: string]: Leaderboard}>_.zipObject(leaderboard_ids_deduped, await new LeaderboardDao(scraper.storedb!).load(leaderboard_ids_deduped));

        let lbres: LeaderboardRunEntry[] = [];

        for(let run of runs) {

            let leaderboard = leaderboards[(<BulkCategory>run.category).id + (run.level ? '_' + (<BulkLevel>run.level).id : '')];

            if(!leaderboard) {
                continue;
            }

            lbres.push(add_leaderboard_run(
                leaderboard,
                run,
                <Variable[]>pr.categories[(<BulkCategory>run.category).id]!.variables)
            );
        }

        if(lbres.length)
            await new RunDao(scraper.storedb!).save(lbres);

        let clean_leaderboards = _.reject(_.values(leaderboards), _.isNil);
        if(clean_leaderboards.length)
            await new LeaderboardDao(scraper.storedb!).save(_.values(leaderboards));

        await new UserDao(scraper.storedb!).apply_runs(lbres);

        // reschedule with additional offset to go back sync
        await scraper.push_call({
            runid: runid,
            module: 'latest-runs',
            exec: 'pull_latest_runs',
            options: {
                verified: options.verified,
                offset: res.data.pagination.offset + res.data.pagination.size,
                latest_run_date: latest_run_date
            }
        }, 1);
    }
    catch(err) {
        console.error('loader/latest-runs: could not retrieve and process pulls for latest runs:', options, err.statusCode || err);
        throw 'permanent';
    }
}
