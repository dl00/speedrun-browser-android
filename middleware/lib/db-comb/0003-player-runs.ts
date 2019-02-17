import * as ioredis from 'ioredis';

import * as speedrun_api from '../speedrun-api';
import * as speedrun_db from '../speedrun-db';

// Operations:
// * normalize every object stored in the db
// * use special normalize functions as necessary

export default async function(db: ioredis.Redis, _indexer: any) {
    // for now noop
}