import * as _ from 'lodash';

import { Router } from 'express';

import * as speedrun_db from '../../lib/speedrun-db';
import { load_indexer, load_config } from '../../lib/config';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

let indexer = load_indexer(load_config(), 'genres');

// retrieves a list of games from most popular to least popular
router.get('/', async function(req, res) {
    let query = <string>req.query.q;

    if(!query) {
        try {
            let top_genres = await api.storedb!.redis.zrevrange(
                speedrun_db.locs.genre_rank,
                0, 19
            );

            let raw = await api.storedb!.redis.hmget(
                speedrun_db.locs.genres,
                ...top_genres);

            let results = _.chain(raw)
                .reject(_.isNil)
                .map(JSON.parse)
                .value();

            return api_response.complete(res, results);
        }
        catch(err) {
            console.log('api/genres: could not get top list:', err);
            api_response.error(res, api_response.err.INTERNAL_ERROR());
        }
    }

    if(query.length > api.config!.api.maxSearchLength)
        api_response.error(res, api_response.err.INVALID_PARAMS(['q'], 'invalid length'));

    query = query.toLowerCase();

    // search all the indexer indexes
    try {
        let ids = await indexer.search_raw(query, {maxResults: 20});

        if(ids.length) {
            // resolve all the results
            let raw = await api.storedb!.redis.hmget(
                speedrun_db.locs.genres,
                ...ids);

            let results = _.chain(raw)
                .reject(_.isNil)
                .map(JSON.parse)
                .value();

            return api_response.complete(res, results);
        }
        else
            api_response.complete(res, []);
    }
    catch(err) {
        console.log('api/genres: could not autocompleted:', err);
        api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;
