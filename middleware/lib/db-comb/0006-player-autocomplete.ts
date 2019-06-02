import * as ioredis from 'ioredis';

//import * as speedrun_api from '../speedrun-api';
import * as speedrun_db from '../speedrun-db';
import { User } from '../speedrun-api';
import { Config, load_indexer } from '../config';

// Operations:
// * normalize every object stored in the db
// * use special normalize functions as necessary

export default async function(db: ioredis.Redis, config: Config) {
    let cursor = 0;
    let done_count = 0;
    let total_count = await db.hlen(speedrun_db.locs.players);

    let indexer = load_indexer(config, 'players');

    do {
        console.log('Player Autocomplete:', done_count, '/', total_count);

        let res = await db.hscan(speedrun_db.locs.players, cursor);

        cursor = res[0];

        // cursor returns both keys and values. Iterate by values.
        for(let i = 1;i < res[1].length;i += 2) {
            let player: User = JSON.parse(res[1][i]);

            let indexes: { text: string, score: number, namespace?: string }[] = [];
            for(let name in player.names) {

                if(!player.names[name])
                    continue;

                let idx: any = { text: player.names[name].toLowerCase(), score: 100 - player.names[name].length };

                if(name != 'international')
                    idx.namespace = name;

                indexes.push(idx);
            }

            await indexer.add(player.id, indexes);
        }

        done_count += res[1].length / 2;

    } while(cursor != 0);
}
