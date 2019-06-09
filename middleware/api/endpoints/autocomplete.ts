
import { Router } from 'express';

import * as _ from 'lodash';

import * as api from '../';
import * as api_response from '../response';

import { GameDao } from '../../lib/dao/games';
import { UserDao } from '../../lib/dao/users';

type IndexerResponse = {[type: string]: any[]};

const router = Router();

router.get('/', async (req, res) => {
    let query = <string>req.query.q;

    if(!query || query.length > api.config!.api.maxSearchLength)
        api_response.error(res, api_response.err.INVALID_PARAMS(['q'], 'invalid length'));

    query = query.toLowerCase();

    // search all the indexer indexes
    try {
        let results: IndexerResponse = {
            games: _.reject(await new GameDao(api.storedb!).load_by_index('autocomplete', query), _.isNil),
            players: _.reject(await new UserDao(api.storedb!).load_by_index('autocomplete', query), _.isNil)
        };

        return api_response.custom(res, {
            search: results
        });
    }
    catch(err) {
        console.log('api/autocomplete: could not autocompleted:', err);
        api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;
