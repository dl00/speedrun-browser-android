import * as _ from 'lodash';

import * as scraper from './';
import * as push_notify from './push-notify';

import * as config from '../lib/config';

import { Category } from '../lib/dao/categories';
import { Level } from '../lib/dao/levels';
import { User, UserDao } from '../lib/dao/users';

import { Game, GameDao } from '../lib/dao/games';
import { LeaderboardRunEntry, Run, RunDao } from '../lib/dao/runs';

import { make_all_wr_charts } from '../lib/dao/runs/charts';

scraper.connect(config.load_config()).then(_.noop(), console.error);

export async function send_dummy_player_notify(run_id: string) {
    const runs = await new RunDao(scraper.storedb!).load(run_id);

    const player = (await new UserDao(scraper.storedb!).load((runs[0]!.run as Run).players[0].id))[0] as User;
    const game = (await new GameDao(scraper.storedb!).load(((runs[0]!.run as Run).game as Game).id))[0] as Game;
    const category: Category = (runs[0]!.run as Run).category as Category;
    const level: Level|null = (runs[0]!.run as Run).level as Level|null;

    await push_notify.notify_player_record({new_run: runs[0]!, old_run: runs[0]!}, player, game, category, level as any);
}

export async function massage_all_runs(skip = 0) {
    return await new RunDao(scraper.storedb!).massage({}, skip);
}

export async function restore_single_run(id: string) {
    const run_dao = new RunDao(scraper.storedb!);
    const run = await run_dao.load(id);
    if (run[0] != null) {
        return await run_dao.save(run[0] as LeaderboardRunEntry);
    }
    else {
        return 'Run does not exist.';
    }
}

export async function regenerate_autocomplete() {
    const game_dao = new GameDao(scraper.storedb!);
    game_dao.indexes.find((ind) => ind.name == 'autocomplete')!.forceIndex = true;

    await game_dao.massage();

    const user_dao = new UserDao(scraper.storedb!);
    user_dao.indexes.find((ind) => ind.name == 'autocomplete')!.forceIndex = true;

    await user_dao.massage();
}

export async function generate_all_charts() {
    await make_all_wr_charts(new RunDao(scraper.storedb!));
}
