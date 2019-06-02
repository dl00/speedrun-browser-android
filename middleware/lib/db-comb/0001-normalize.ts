import * as _ from 'lodash';

import * as ioredis from 'ioredis';

import * as speedrun_api from '../speedrun-api';
import * as speedrun_db from '../speedrun-db';
import { Config } from '../config';

import { normalize_game } from '../dao/games';
import { normalize_leaderboard } from '../dao/leaderboards';
import { normalize_run } from '../dao/runs';

function do_normalize(type: string, d: any) {
    switch(type) {
    case 'games':
        normalize_game(d);
    case 'categories':
        speedrun_api.normalize_category(d);
    case 'leaderboards':
        normalize_leaderboard(d);
    case 'runs':
        normalize_run(d);
    default:
        speedrun_api.normalize(d);
    }
}

// Operations:
// * normalize every object stored in the db
// * use special normalize functions as necessary

export default async function(db: ioredis.Redis, _config: Config) {
    for(let type in speedrun_db.locs) {

        // TODO: Refactor
        if(type == 'game_rank' ||
            type == 'genre_rank' ||
            type == 'game_abbrs' ||
            type == 'player_abbrs' ||
            type == 'latest_run_verify_date' ||
            type == 'latest_run' ||
            type == 'verified_runs')
            continue;

        let cursor = 0;
        let done_count = 0;
        let total_count = await db.hlen(speedrun_db.locs[type]);

        do {
            console.log(`Normalize (${type}):`, done_count, '/', total_count);

            let res = await db.hscan(speedrun_db.locs[type], cursor);

            cursor = res[0];

            // cursor returns both keys and values. Iterate by values.
            for(let i = 1;i < res[1].length;i += 2) {
                let d;
                try {
                    d = JSON.parse(res[1][i]);
                }
                catch(err) {
                    let bad_id = res[1][i - 1];
                    console.log(`Failed to parse json (${bad_id}): ${err}`);
                    // remove this bad json from db
                    await db.hdel(speedrun_db.locs[type], bad_id);

                    continue;
                }

                let orig = _.cloneDeep(d);

                if(_.isArray(d)) {
                    for(let item of d) {
                        do_normalize(type, item);
                    }
                }
                else {
                    do_normalize(type, d);
                }

                // only call redis with a change if the data is different
                if(!_.isEqual(d, orig)) {
                    // TODO: Technically we can do a HMSET here but this is way easier to work with typescript
                    await db.hset(speedrun_db.locs[type], res[1][i - 1], JSON.stringify(d));
                }
            }

            done_count += res[1].length / 2;

        } while(cursor != 0);
    }
}
