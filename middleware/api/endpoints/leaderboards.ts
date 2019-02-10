

import * as _ from 'lodash';

import { Router } from 'express';

import * as speedrun_db from '../../lib/speedrun-db';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

// retrieve one or more leaderboards by id
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    let leaderboards_raw = await api.storedb!.hmget(speedrun_db.locs.leaderboards, ...ids);

    let leaderboards = _.chain(leaderboards_raw)
        .reject(_.isNil)
        .map(JSON.parse)
        .value();

    return api_response.complete(res, leaderboards);
});

module.exports = router;