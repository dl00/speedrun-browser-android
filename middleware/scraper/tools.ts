import * as scraper from './';
import * as push_notify from './push-notify';

import * as config from '../lib/config';

import { Category } from '../lib/dao/categories';
import { Level } from '../lib/dao/levels';
import { UserDao, User } from '../lib/dao/users';

import { RunDao, Run } from '../lib/dao/runs';
import { GameDao, Game } from '../lib/dao/games';

import { make_all_wr_charts } from '../lib/dao/runs/charts';

scraper.connect(config.load_config());

export async function send_dummy_player_notify(run_id: string) {
    let runs = await new RunDao(scraper.storedb!).load(run_id);

    let player = <User>(await new UserDao(scraper.storedb!).load((<Run>runs[0]!.run).players[0].id))[0];
    let game = <Game>(await new GameDao(scraper.storedb!).load((<Game>(<Run>runs[0]!.run).game).id))[0];
    let category: Category = <Category>(<Run>runs[0]!.run).category;
    let level: Level|null = <Level|null>(<Run>runs[0]!.run).level;

    push_notify.notify_player_record({new_run: runs[0]!, old_run: runs[0]!}, player, game, category, <any>level);
}

export async function massage_all_runs() {
    return await new RunDao(scraper.storedb!).massage_runs();
}

export async function generate_all_charts() {
    await make_all_wr_charts(new RunDao(scraper.storedb!));
}
