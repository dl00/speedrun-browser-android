import request from '../lib/request';

import { DB } from '../lib/db';

import { PullDao, Pull } from '../lib/dao/pulls';

import { API_PREFIX } from '../lib/speedrun-api';

export async function do_pull(db: DB, api_url: string): Promise<Pull> {
    let res = await request(API_PREFIX + api_url);

    let pull = {
        api_url: api_url,
        data: res,
        timestamp: new Date()
    }

    await new PullDao(db).save(pull);

    return pull;
}
