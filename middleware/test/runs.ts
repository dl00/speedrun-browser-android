import * as _ from 'lodash';

import { GameDao } from '../lib/dao/games';
import { RunDao } from '../lib/dao/runs';
import { load_db, close_db, DB } from '../lib/db';
import { load_config } from '../lib/config';

import { expect } from 'chai';

import 'mocha';

describe('RunDao', () => {
    var db: DB;

    before(async () => {
        db = await load_db(load_config());
        await db.mongo.dropDatabase();
        await db.mongo.setProfilingLevel('all');
        await db.redis.flushall();
    });

    after(async () => {
        close_db(db);
    });

    it('should index latest runs', async () => {
        let game_dao = new GameDao(db);
        let run_dao = new RunDao(db);

        await game_dao.save([
            {
                id: 'a_game',
                names: {international: 'A Game'},
                abbreviation: 'agame',
                weblink: 'https://speedrun.com/agame',
                platforms: [],
                regions: [],
                genres: [],
                released: 2019,
                developers: [],
                publishers: [],
                created: '2019-01-01',
                "release-date": '2019-01-01',
                assets: {}
            },
            {
                id: 'a_game_with_genre',
                names: {international: 'A Game'},
                abbreviation: 'agamewithg',
                weblink: 'https://speedrun.com/agamewithg',
                platforms: [],
                regions: [],
                genres: [{ id: 'my_genre', name: 'testing genre'}],
                released: 2019,
                developers: [],
                publishers: [],
                created: '2019-01-01',
                "release-date": '2019-01-01',
                assets: {}
            }
        ]);

        await run_dao.save([
            {
                place: 3,
                run: {
                    id: 'another_run',
                    date: '2018-04-30',
                    status: {'verify-date': '2018-04-30'},
                    players: [],
                    times: { primary: '100' },
                    system: {},
                    values: {},
                    game: 'a_game_with_genre'
                }
            },
            {
                place: 1,
                run: {
                    id: 'one_run',
                    date: '2018-05-05',
                    status: {'verify-date': '2018-05-05'},
                    players: [],
                    times: { primary: '135' },
                    system: {},
                    values: {},
                    game: 'a_game'
                }
            }
        ]);

        // make sure these two runs come back, and they are in the correct order
        let runs = await run_dao.load_latest_runs();

        expect(runs[0]).to.have.property('place', 1);
        expect(runs[1]).to.have.property('place', 3);

        runs = await run_dao.load_latest_runs(0, 'my_genre');

        expect(runs.length).to.eql(1);
        expect(runs[0]).to.have.property('place', 3);

        // add another run after the fact, should still be in order
        await run_dao.save({
            place: 2,
            run: {
                id: 'yet_another_run',
                date: '2018-05-01',
                status: {'verify-date': '2018-05-01'},
                players: [],
                times: { primary: '135' },
                system: {},
                values: {},
                game: 'a_game_with_genre'
            }
        });

        runs = await run_dao.load_latest_runs(1);

        expect(runs[0]).to.have.property('place', 2);
        expect(runs[1]).to.have.property('place', 3);

        runs = await run_dao.load_latest_runs(0, 'my_genre');

        expect(runs[0]).to.have.property('place', 2);
        expect(runs[1]).to.have.property('place', 3);
    });
});
