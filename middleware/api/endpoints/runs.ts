import * as _ from 'lodash';

import { Router, Request, Response } from 'express';

import * as speedrun_api from '../../lib/speedrun-api'
import * as speedrun_db from '../../lib/speedrun-db';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

async function get_latest_runs(req: Request, res: Response) {
    let start = 0;

    if(req.query.start) {
        start = parseInt(req.query.start);
    }

    let end = start + api.config!.api.maxItems - 1;
    if(req.query.count)
        end = start + parseInt(req.query.count) - 1;

    if(isNaN(start) || start < 0)
        return api_response.error(res, api_response.err.INVALID_PARAMS(['start']));

    if(isNaN(end) || end < start || end - start + 1 > api.config!.api.maxItems)
        return api_response.error(res, api_response.err.INVALID_PARAMS(['count']));

    let db_key = speedrun_db.locs.verified_runs;

    if(req.params.id) {
        // filter by genre
        db_key += ':' + req.params.id;
    }

    let total = await api.storedb!.zcard(db_key);

    if(start > total - 1)
        return api_response.error(res, api_response.err.INVALID_PARAMS(['start'], 'past the end of list'));

    end = Math.min(end, total - 1);

    try {
        let ids = await api.storedb!.zrevrange(db_key, start, end);
        let runs_raw = await api.storedb!.hmget(speedrun_db.locs.runs, ...ids);

        let runs = _.chain(runs_raw)
            .reject(_.isNil)
            .map(JSON.parse)
            .value();

        return api_response.complete(res, runs, {
            code: ((end + 1) % total).toString(),
            total: total
        });
    } catch(err) {
        console.error('api/games: could not send latest runs:', err);
        return api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
}

router.get('/latest/genre/:id', get_latest_runs);
router.get('/latest', get_latest_runs);

// retrieve one or more runs by id
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    let lre_raw = await api.storedb!.hmget(speedrun_db.locs.runs, ...ids);

    let lres: speedrun_api.LeaderboardRunEntry[] = <any[]>_.chain(lre_raw)
        .reject(_.isNil)
        .map(JSON.parse)
        .value();

    return api_response.complete(res, lres);
});

module.exports = router;
