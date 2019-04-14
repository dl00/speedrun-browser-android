import * as _ from 'lodash';

import { Router } from 'express';

import * as speedrun_db from '../../lib/speedrun-db';

import * as api from '../';
import * as api_response from '../response';

const router = Router();

// retrieve what type of item a set of ids is
router.get('/:ids', async (req, res) => {
    let ids = req.params.ids.split(',');

    if(ids.length > api.config!.api.maxItems) {
        return api_response.error(res, api_response.err.TOO_MANY_ITEMS());
    }

    let multi = api.storedb!.multi();

    for(let id of ids) {
        multi
            .hget(speedrun_db.locs.game_abbrs, id)
            .hexists(speedrun_db.locs.games, id)
            .hget(speedrun_db.locs.player_abbrs, id)
            .hexists(speedrun_db.locs.players, id)
            .hexists(speedrun_db.locs.runs, id)
    }

    let d = await multi.exec();

    let types: ({type: string, id: string}|null)[] = [];

    for(let i = 0;i < ids.length;i++) {
        if(d[i * 5][1]) {
            types.push({
                type: 'game',
                id: d[i * 5][1]
            });
        }
        else if(d[i * 5 + 1][1]) {
            types.push({
                type: 'game',
                id: ids[i]
            });
        }
        else if(d[i * 5 + 2][1]) {
            
            types.push({
                type: 'player',
                id: d[i * 5 + 2][1]
            });
        }
        else if(d[i * 5 + 3][1]) {
            types.push({
                type: 'player',
                id: ids[i]
            });
        }
        else if(d[i * 5 + 4][1]) {
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