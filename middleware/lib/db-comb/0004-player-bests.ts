import * as ioredis from 'ioredis';

//import * as speedrun_api from '../speedrun-api';
import * as speedrun_db from '../speedrun-db';
import { Leaderboard } from '../speedrun-api';
import { Config } from '../config';

// Operations:
// * normalize every object stored in the db
// * use special normalize functions as necessary

export default async function(db: ioredis.Redis, _config: Config) {
    let cursor = 0;
    let done_count = 0;
    let total_count = await db.hlen(speedrun_db.locs.leaderboards);

    do {
        console.log('Player Bests Leaderboard Scan:', done_count, '/', total_count);

        let res = await db.hscan(speedrun_db.locs.leaderboards, cursor, 'COUNT', 400);

        cursor = res[0];

        // cursor returns both keys and values. Iterate by values.
        for(let i = 1;i < res[1].length;i += 2) {
            let leaderboard: Leaderboard = JSON.parse(res[1][i]);
            await speedrun_db.apply_leaderboard_bests(db, leaderboard, {});
        }

        done_count += res[1].length / 2;

    } while(cursor != 0);
}
