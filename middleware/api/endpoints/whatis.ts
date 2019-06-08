import * as _ from 'lodash';

import { Router } from 'express';

import * as api from '../';
import * as api_response from '../response';

import { GameDao } from '../../lib/dao/games';
import { UserDao } from '../../lib/dao/users';
import { RunDao } from '../../lib/dao/runs';

const router = Router();

// retrieve what type of item a set of ids is
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    let game_dao = new GameDao(api.storedb!);
    let games = await game_dao.load(ids);
    let games_by_abbr = await game_dao.load_by_index('abbr', ids);

    let user_dao = new UserDao(api.storedb!);
    let players = await new UserDao(api.storedb!).load(ids);
    let players_by_abbr = await user_dao.load_by_index('abbr', ids);

    let runs = await new RunDao(api.storedb!).load(ids);

    let types: ({type: string, id: string}|null)[] = [];

    for(let i = 0;i < ids.length;i++) {
        if(games[i] || games_by_abbr[i]) {
            types.push({
                type: 'game',
                id: games_by_abbr[i] ? games_by_abbr[i]!.id : ids[i]
            });
        }
        else if(players[i] || players_by_abbr[i]) {
            types.push({
                type: 'player',
                id: players_by_abbr[i] ? players_by_abbr[i]!.id : ids[i]
            });
        }
        else if(runs[i]) {
            types.push({
                type: 'run',
                id: ids[i]
            });
        }
        else
            types.push(null);
    }

    return api_response.complete(res, types);
});

module.exports = router;
