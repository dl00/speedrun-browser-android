import * as scraper from './';
import * as push_notify from './push-notify';

import * as config from '../lib/config';
import * as speedrun_db from '../lib/speedrun-db';
import { Category, Level, User } from '../lib/speedrun-api';

import { RunDao, Run } from '../lib/dao/runs';
import { Game } from '../lib/dao/games';

scraper.connect(config.load_config());

export async function send_dummy_player_notify(run_id: string) {
    let runs = await new RunDao(scraper.storedb!).load(run_id);

    let player: User = JSON.parse(
        <string>await scraper.storedb!.redis.hget(speedrun_db.locs.players, (<Run>runs[0]!.run).players[0].id));
    let game: Game = JSON.parse(
        <string>await scraper.storedb!.redis.hget(speedrun_db.locs.games, (<Game>(<Run>runs[0]!.run).game).id));
    let category: Category = <Category>(<Run>runs[0]!.run).category;
    let level: Level|null = <Level|null>(<Run>runs[0]!.run).level;

    push_notify.notify_player_record({new_run: runs[0]!, old_run: runs[0]!}, player, game, category, <any>level);
}
