import * as _ from 'lodash';

import { Router } from 'express';

import * as speedrun_db from '../../lib/speedrun-db';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

// retrieves a list of games from most popular to least popular
router.get('/', async (req, res) => {
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

    let total = await api.storedb!.zcard(speedrun_db.locs.game_rank);

    if(start > total - 1)
        return api_response.error(res, api_response.err.INVALID_PARAMS(['start'], 'past the end of list'));
    
    end = Math.min(end, total - 1);

    try {
        let ids = await api.storedb!.zrevrange(speedrun_db.locs.game_rank, start, end);
        let games_raw = await api.storedb!.hmget(speedrun_db.locs.games, ...ids);

        let games = _.chain(games_raw)
            .reject(_.isNil)
            .map(JSON.parse)
            .value();

        return api_response.complete(res, games, {
            code: ((end + 1) % total).toString(),
            total: total
        });
    } catch(err) {
        console.error('api/games: could not send games:', err);
        return api_response.error(res, api_response.err.INTERNAL_ERROR());
    }

    
});

// retrieve one or more games by id
// if only one game is requested, embed additionally the categories and levels
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    // remap abbrevations as necessary
    let abbr_remapped = await api.storedb!.hmget(speedrun_db.locs.game_abbrs, ...ids);
    ids = _.zipWith(abbr_remapped, ids, (abbr, id) => abbr || id);
    let games_raw = await api.storedb!.hmget(speedrun_db.locs.games, ...ids);

    let games = <any[]>_.chain(games_raw)
        .reject(_.isNil)
        .map(JSON.parse)
        .value();

    
    if(games.length === 1) {
        let category_raw = await api.storedb!.hget(speedrun_db.locs.categories, games[0].id);
        let level_raw = await api.storedb!.hget(speedrun_db.locs.levels, games[0].id);

        if(category_raw)
            games[0].categories = JSON.parse(category_raw);
        if(level_raw)
            games[0].levels = JSON.parse(level_raw);
    }

    return api_response.complete(res, games);
});

module.exports = router;