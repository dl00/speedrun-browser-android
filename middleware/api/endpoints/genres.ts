import * as _ from 'lodash';

import { Router } from 'express';

import { GenreDao } from '../../lib/dao/genres';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

// retrieves a list of games from most popular to least popular
router.get('/', async function(req, res) {
    let query = <string>req.query.q;

    let genre_dao = new GenreDao(api.storedb!);

    if(!query) {
        try {
            let genres = await genre_dao.load_popular();
            return api_response.complete(res, genres);
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
        let genres = await genre_dao.load_by_index('autocomplete', query);
        api_response.complete(res, genres);
    }
    catch(err) {
        console.log('api/genres: could not autocompleted:', err);
        api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;
