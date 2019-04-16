import * as scraper from './';
import * as push_notify from './push-notify';

import * as config from '../lib/config';
import * as speedrun_db from '../lib/speedrun-db';
import { LeaderboardRunEntry, Run, Game, Category, Level, User } from '../lib/speedrun-api';


scraper.connect(config.load_config());

export async function send_dummy_player_notify(runId: string) {
    let run: LeaderboardRunEntry = JSON.parse(
        <string>await scraper.storedb!.hget(speedrun_db.locs.runs, runId));

    let player: User = JSON.parse(
        <string>await scraper.storedb!.hget(speedrun_db.locs.players, (<Run>run.run).players[0].id));
    let game: Game = JSON.parse(
        <string>await scraper.storedb!.hget(speedrun_db.locs.games, (<Game>(<Run>run.run).game).id));
    let category: Category = <Category>(<Run>run.run).category;
    let level: Level|null = <Level|null>(<Run>run.run).level;

    push_notify.notify_player_record({new_run: run, old_run: run}, player, game, category, <any>level);
}