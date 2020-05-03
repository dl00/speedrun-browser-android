import * as _ from 'lodash';

import { populate_run_sub_documents, Run, RunDao } from '../lib/dao/runs';
import { RunTimes, RunSystem } from '../lib/dao/runs/structures';
import { UserDao, User } from '../lib/dao/users';

import * as puller from '../lib/puller';

import { CursorData, Sched } from '../sched/index';

const RUN_BATCH_COUNT = 200;

export interface SRCRun {

    id: string;

    weblink: string;

    game: string;
    category: string;
    level?: string|null;

    date: string;
    times: RunTimes;
    system: RunSystem;
    values: {[key: string]: string};

    players: { data: User[] }

    [key: string]: any;
}

export async function generate_all_runs(sched: Sched, cur: CursorData<SRCRun>|null): Promise<CursorData<SRCRun>|null> {
    const res = await puller.do_pull(sched.storedb, `/runs?max=200&orderby=submitted&direction=desc&offset=${cur?.pos || 0}`);

    const nextPos = cur ? parseInt(cur.pos!) : RUN_BATCH_COUNT;

    return {
        items: res.data.data,
        asOf: Date.now(),
        desc: `runs ${nextPos}..${nextPos + RUN_BATCH_COUNT}`,
        pos: res.data.pagination.max == res.data.pagination.size ? nextPos.toString() : null
    }
}

export async function generate_latest_runs(sched: Sched, cur: CursorData<SRCRun>|null): Promise<CursorData<SRCRun>|null> {
    const res = await puller.do_pull(sched.storedb, `/runs?max=200&orderby=submitted&direction=desc&offset=${cur?.pos || 0}`);

    const nextPos = cur ? parseInt(cur.pos!) : RUN_BATCH_COUNT;

    // only keep going if the run dates are such that we have enough

    return {
        items: res.data.data,
        asOf: Date.now(),
        desc: `runs ${nextPos}..${nextPos + RUN_BATCH_COUNT}`,
        pos: res.data.pagination.max == res.data.pagination.size ? nextPos.toString() : null
    }
}

export async function apply_runs(sched: Sched, cur: CursorData<SRCRun>) {

    const runs: Run[] = cur.items.map((run: any) => {
        run.game = {id: run.game};
        run.category = {id: run.category};

        if (run.level) {
            run.level = {id: run.level};
        }

        run.players = run.players.data;

        return run;
    });

    // handle player obj updates before anything else
    const updatedPlayers = _.chain(runs)
        .map('players')
        .flatten()
        .filter('id')
        .value();

    const user_dao = new UserDao(sched.storedb!);
    const users = await user_dao.load(_.map(updatedPlayers, 'id'), {skipComputed: true});
    await user_dao.save(users.map((v, i) => _.merge(v, updatedPlayers[i] as any)));

    const pr = await populate_run_sub_documents(sched.storedb!, runs);

    if (pr.drop_runs.length) {
        _.remove(runs, (r) => _.find(pr.drop_runs, (dr) => dr.id === r.id));
    }

    if (runs.length) {
        await new RunDao(sched.storedb!).save(runs.map((run: Run) => {
            return {run: run}
        }));
    }
}