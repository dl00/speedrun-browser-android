import * as ioredis from 'ioredis';

import * as speedrun_db from '../speedrun-db';
import { Game } from '../speedrun-api';

// Operations:
// 1. update scoring system for games to be based on available speedrunning data:
// * read leaderboard data
// * using the time data was pulled, find number of speedruns updated in the last 3 months. Divide by number of leaderboards.
// * games with shorter overall leaderboards will be at a disadvantage.

export default async function(db: ioredis.Redis, _indexer: any) {

    let cursor = 0;
    let done_count = 0;
    let total_count = await db.hlen(speedrun_db.locs.games);

    do {
        console.log('Abbreviate Index Games:', done_count, '/', total_count);

        let res = await db.hscan(speedrun_db.locs.games, cursor);

        cursor = res[0];

        let abbr_mapping: string[] = [];

        // cursor returns both keys and values. Iterate by values.
        for(let i = 1;i < res[1].length;i += 2) {
            let game: Game = JSON.parse(res[1][i]);

            abbr_mapping.push(game.abbreviation, game.id);
        }

        if(abbr_mapping.length)
            await db.hmset(speedrun_db.locs.game_abbrs, ...abbr_mapping);

        done_count += res[1].length / 2;

    } while(cursor != 0);
}