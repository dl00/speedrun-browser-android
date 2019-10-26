// Leaderboard download and handling
// * Separates players
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as puller from '../puller';

import * as scraper from '../index';

import * as push_notify from '../push-notify';

import { populate_run_sub_documents, LeaderboardRunEntry, Run, RunDao } from '../../lib/dao/runs';
import { User, UserDao } from '../../lib/dao/users';

import { BulkGame } from '../../lib/dao/games';

export async function pull_latest_runs(runid: string, options: any) {
    try {

        const run_date_property: 'status.verify-date'|'submitted' = options.verified ? 'status.verify-date' : 'submitted';
        const latest_run_redis_property: 'latest_run_verify_date'|'latest_run_new_date' = options.verified ? 'latest_run_verify_date' : 'latest_run_new_date';

        const res = await puller.do_pull(scraper.storedb!,
            `/runs?status=verified&orderby=${options.verified ? 'verify-date' : 'submitted'}&direction=desc&offset=${options.offset || 0}&max=100&embed=players`);

        let runs: Run[] = res.data.data.map((run: any) => {
            run.game = {id: run.game};
            run.category = {id: run.category};

            if (run.level) {
                run.level = {id: run.level};
            }

            run.players = run.players.data;

            return run;
        });

        if (!runs.length) {
            return;
        }

        const latest_run_date: string|null = options.latest_run_date ||
            await scraper.storedb!.redis.getset(latest_run_redis_property,
                _.get(runs[0], run_date_property));

        if (!latest_run_date) {
            return;
        }

        const remove_after = _.findIndex(runs, (run) => _.get(run, run_date_property) <= latest_run_date!);

        if (remove_after === 0) {
            return;
        } else if (remove_after !== -1) {
            runs = runs.slice(0, remove_after);
 }

        // update users
        const updated_players = _.chain(runs)
            .map('players')
            .flatten()
            .filter('id')
            .value();

        const user_dao = new UserDao(scraper.storedb!);
        const players = await user_dao.load(_.map(updated_players, 'id'), {skipComputed: true});
        await user_dao.save(players.map((v, i) => _.merge(v, updated_players[i] as any)));

        // install runs on the database
        const pr = await populate_run_sub_documents(scraper.storedb!, runs);

        if (pr.drop_runs.length) {
            // DO NOT use `runs =` here or else it will get an array of what was REMOVED.
            _.remove(runs, (r) => _.find(pr.drop_runs, (dr) => dr.id === r.id));
        }

        // download missing runs
        for (const mr of pr.drop_runs) {
            if (mr.game) {
                await scraper.push_call({
                    runid: runid + '/' + mr.id,
                    module: 'gamelist',
                    exec: 'pull_game',
                    options: {
                        id: (mr.game as BulkGame).id || mr.game,
                    },
                }, 5);
            }
        }

        if (!runs.length) {
            return;
        }

        const run_dao = new RunDao(scraper.storedb!);

        await run_dao.save(runs.map((run: Run) => {
            return {run: run}
        }));
        // reload from db in order to get computed properties
        let lbres = (await run_dao.load(_.map(runs, 'id')) as LeaderboardRunEntry[]);

        // send push notifications for new records
        const new_records = run_dao.collect_new_records();
        for (const nr of new_records) {

            const record_run = lbres.find((r) => r.run.id === nr.new_run.run.id);

            // TODO: these conditions should not be needed yet somehow they are
            if (!record_run || !record_run.run.game || !record_run.run.category) {
                continue;
            }

            nr.new_run = _.cloneDeep(record_run);

            if (record_run.place == 1 && record_run.run.status.status == 'verified') {
                // new record on this category/level, send notification
                await push_notify.notify_game_record(nr, record_run.run.game, record_run.run.category, record_run.run.level);
            }

            // this should be a personal best. send notification to all attached players who are regular users
            if(record_run.run.status.status != 'verified') {
                for (const p of record_run.run.players) {
                    await push_notify.notify_player_record(nr, p as User,
                        record_run.run.game, record_run.run.category, record_run.run.level);
                }
            }
        }

        // reschedule with additional offset to go back sync
        if (remove_after === -1) {
            await scraper.push_call({
                runid,
                module: 'latest-runs',
                exec: 'pull_latest_runs',
                options: {
                    verified: options.verified,
                    offset: res.data.pagination.offset + res.data.pagination.size,
                    latest_run_date,
                },
            }, 1);
        }
    } catch (err) {
        console.error('loader/latest-runs: could not retrieve and process pulls for latest runs:', options, err.statusCode || err);
        throw new Error('permanent');
    }
}
