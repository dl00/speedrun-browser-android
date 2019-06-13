import * as _ from 'lodash';

import { Router, Request, Response } from 'express';

import * as api from '../';
import * as api_response from '../response';

import { Game, GameDao } from '../../lib/dao/games';
import { Category, CategoryDao, standard_sort_categories } from '../../lib/dao/categories';
import { Level, LevelDao } from '../../lib/dao/levels';

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
            games[0]!.categories = <Category[]>await new CategoryDao(api.storedb!).load_by_index('game', games[0]!.id);

            // since we don't preserve the order from speedrun.com of categories, we have to sort them on our own


            games[0]!.levels = <Level[]>await new LevelDao(api.storedb!).load_by_index('game', games[0]!.id);

            // since we don't preserve the order from speedrun.com, we have to sort them on our own
            games[0]!.levels = _.sortBy(games[0]!.levels, l => l.name.toLowerCase())
        }

        return api_response.complete(res, games);
    }
    catch(err) {
        console.error('api/games/genre: could not send genred games:', err);
        return api_response.error(res, api_response.err.INTERNAL_ERROR());
    }
});

module.exports = router;
