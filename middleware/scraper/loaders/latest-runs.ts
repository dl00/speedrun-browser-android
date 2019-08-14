// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as puller from '../puller';

import * as scraper from '../index';

import * as push_notify from '../push-notify';

import { Run, RunDao, LeaderboardRunEntry, populate_run_sub_documents } from '../../lib/dao/runs';
import { UserDao, User } from '../../lib/dao/users';
import { Leaderboard, LeaderboardDao } from '../../lib/dao/leaderboards';

import { BulkGame } from '../../lib/dao/games';
import { BulkCategory } from '../../lib/dao/categories';
import { BulkLevel } from '../../lib/dao/levels';

export async function pull_latest_runs(runid: string, options: any) {
    try {

        let run_date_property: 'status.verify-date'|'submitted' = options.verified ? 'status.verify-date' : 'submitted';
        let latest_run_redis_property: 'latest_run_verify_date'|'latest_run_new_date' = options.verified ? 'latest_run_verify_date' : 'latest_run_new_date';

        let res = await puller.do_pull(scraper.storedb!,
            `/runs?status=verified&orderby=${options.verified ? 'verify-date' : 'submitted'}&direction=desc&offset=${options.offset || 0}&max=100&embed=players`);

        let runs: Run[] = res.data.data.map((run: any) => {
            run.game = {id: run.game};
            run.category = {id: run.category};

            if(run.level)
                run.level = {id: run.level};

            run.players = run.players.data;

            return run;
        });

        if(!runs.length)
            return;

        let latest_run_date: string|null = options.latest_run_date ||
            await scraper.storedb!.redis.getset(latest_run_redis_property,
                _.get(runs[0], run_date_property));

        if(!latest_run_date) {
            return;
        }

        let remove_after = _.findIndex(runs, run => _.get(run, run_date_property) <= latest_run_date!);

        if(remove_after === 0)
            return;
        else if(remove_after !== -1)
            runs = runs.slice(0, remove_after);

        // update users
        let updated_players = _.chain(runs)
            .map('players')
            .flatten()
            .filter('id')
            .value();

        let user_dao = new UserDao(scraper.storedb!);
        let players = await user_dao.load(_.map(updated_players, 'id'), {skipComputed: true});
        await user_dao.save(players.map((v, i) => _.merge(v, <any>updated_players[i])));

        // install runs on the database
        let pr = await populate_run_sub_documents(scraper.storedb!, runs);

        if(pr.drop_runs.length)
            // DO NOT use `runs =` here or else it will get an array of what was REMOVED.
            _.remove(runs, r => _.find(pr.drop_runs, dr => dr.id === r.id));

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

            lbres.push({run: run});
        }

        let run_dao = new RunDao(scraper.storedb!);
        if(lbres.length) {
            await run_dao.save(lbres);
            // reload from db in order to get computed properties
            lbres = <LeaderboardRunEntry[]>await run_dao.load(_.map(lbres, 'run.id'));
        }

        // send push notifications for new records
        let new_records = run_dao.collect_new_records();
        for(let nr of new_records) {

            let record_run = lbres.find(r => r.run.id === nr.new_run.run.id);

            if(!record_run)
                return;

            if(record_run.place == 1) {
                // new record on this category/level, send notification
                await push_notify.notify_game_record(nr, record_run.run.game, record_run.run.category, record_run.run.level);
            }

            // this should be a personal best. send notification to all attached players who are regular users
            for(let p of record_run.run.players) {
                await push_notify.notify_player_record(nr, <User>p,
                    record_run.run.game, record_run.run.category, record_run.run.level);
            }
        }

        // reschedule with additional offset to go back sync
        if(remove_after === -1)
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
