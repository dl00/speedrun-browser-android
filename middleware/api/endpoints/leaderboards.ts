

import * as _ from 'lodash';

import { Router } from 'express';

import * as speedrun_api from '../../lib/speedrun-api'
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

    let leaderboards: speedrun_api.Leaderboard[] = <any[]>_.chain(leaderboards_raw)
        .reject(_.isNil)
        .map(JSON.parse)
        .value();
    
    for(let leaderboard of leaderboards) {
        if(!leaderboard.players) {

            let player_ids = _.chain(leaderboard.runs)
                .map(v => _.map(v.run.players, 'id'))
                .flatten()
                .reject(_.isNil)
                .uniq()
                .value();
            
            if(!player_ids.length) {
                leaderboard.players = {};
                continue;
            }

            let players_raw = await api.storedb!.hmget(speedrun_db.locs.players, ...player_ids);

            leaderboard.players = <any>_.chain(players_raw)
                .reject(_.isNil)
                .map(JSON.parse)
                .map(v => _.pick(v, 'id', 'names', 'weblink', 'name-style', 'role'))
                .keyBy('id')
                .value();
        }
    }

    return api_response.complete(res, leaderboards);
});

module.exports = router;