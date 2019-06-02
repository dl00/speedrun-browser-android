import * as _ from 'lodash';

import { Router, Request, Response } from 'express';

import * as speedrun_db from '../../lib/speedrun-db';

import * as api from '../';
import * as api_response from '../response';

import { GameDao, Game } from '../../lib/dao/games';

const router = Router();

async function get_popular_games(req: Request, res: Response) {
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

    try {
        let games = await new GameDao(api.storedb!,
            {max_items: api.config!.api.maxItems}).load_popular(start, req.params.id);

        return api_response.complete(res, games, {
            code: (end + 1).toString(),
            total: 100000
        });
    } catch(err) {
        console.error('api/games/genre: could not send genred games:', err);
        return api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
}

// retrieves a list of games from most popular to least popular
router.get('/', get_popular_games);
router.get('/genre/:id', get_popular_games);

// retrieve one or more games by id
// if only one game is requested, embed additionally the categories and levels
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    // remap abbrevations as necessary
    let game_dao = new GameDao(api.storedb!);

    try {

        let games: (Game|null)[];
        let games_no_abbr = await game_dao.load(ids);
        if(games_no_abbr.indexOf(null) == -1)
            games = games_no_abbr;
        else {
            let games_abbr = await game_dao.load_by_index('abbr', ids);
            games = _.zipWith(games_no_abbr, games_abbr, (a: any, b: any) => a || b);

            return api_response.complete(res, games);
        }

        if(games.length === 1 && !_.isNil(games[0])) {
            let category_raw = await api.storedb!.redis.hget(speedrun_db.locs.categories, games[0]!.id);
            let level_raw = await api.storedb!.redis.hget(speedrun_db.locs.levels, games[0]!.id);

            if(category_raw)
                games[0]!.categories = JSON.parse(category_raw);
            if(level_raw)
                games[0]!.levels = JSON.parse(level_raw);
        }

        return api_response.complete(res, games);
    }
    catch(err) {
        console.error('api/games/genre: could not send genred games:', err);
        return api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;
