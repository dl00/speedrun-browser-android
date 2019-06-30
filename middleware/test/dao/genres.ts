import * as _ from 'lodash';

import { GenreDao } from '../../lib/dao/genres';
import { GameDao } from '../../lib/dao/games';
import { load_db, close_db, DB } from '../../lib/db';
import { load_config } from '../../lib/config';

import { expect } from 'chai';

import 'mocha';

describe('GenreDao', () => {
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

    it('should index popular genres', async () => {
        let genre_dao = new GenreDao(db);
        let game_dao = new GameDao(db);

        await game_dao.save([
            {
                id: 'a_game_genre',
                names: {international: 'A Game'},
                abbreviation: 'agame',
                weblink: 'https://speedrun.com/agame',
                platforms: [],
                regions: [],
                genres: [{ id: 'a_genre', name: 'A Genre' }],
                released: 2019,
                developers: [],
                publishers: [],
                created: '2019-01-01',
                "release-date": '2019-01-01',
                assets: {},
                categories: [{id: 'dummy', name: 'test', weblink: '', miscellaneous: false, type: 'per-game'}]
            },
            {
                id: 'a_game_with_genre_2',
                names: {international: 'A Game'},
                abbreviation: 'agamewithg',
                weblink: 'https://speedrun.com/agamewithg',
                platforms: [],
                regions: [],
                genres: [{ id: 'a_genre_2', name: 'A Genre 2' }],
                released: 2019,
                developers: [],
                publishers: [],
                created: '2019-01-01',
                "release-date": '2019-01-01',
                assets: {},
                categories: [{id: 'dummy2', name: 'test', weblink: '', miscellaneous: false, type: 'per-game'}]
            },
            {
                id: 'another_game_with_genre_2',
                names: {international: 'A Game'},
                abbreviation: 'agamewithg',
                weblink: 'https://speedrun.com/agamewithg',
                platforms: [],
                regions: [],
                genres: [{ id: 'a_genre_2', name: 'A Genre 2' }],
                released: 2019,
                developers: [],
                publishers: [],
                created: '2019-01-01',
                "release-date": '2019-01-01',
                assets: {},
                categories: [{id: 'dummy2', name: 'test', weblink: '', miscellaneous: false, type: 'per-game'}]
            }
        ]);

        await genre_dao.save([
            {
                id: 'a_genre',
                name: 'a genre',
                game_count: 5
            },
            {
                id: 'a_genre_2',
                name: 'a second genre',
                game_count: 4,
            }
        ]);

        // make sure these two runs come back, and they are in the correct order
        let genres = await genre_dao.load_popular();

        expect(genres[0]).to.have.property('id', 'a_genre');
        expect(genres[1]).to.have.property('id', 'a_genre_2');

        genres = await genre_dao.load_popular(1);

        expect(genres.length).to.eql(1);
        expect(genres[0]).to.have.property('id', 'a_genre_2');

        await genre_dao.rescore_genre(['a_genre', 'a_genre_2']);

        // order of the leaderboards should be switched now
        genres = await genre_dao.load_popular();

        expect(genres[0]).to.have.property('id', 'a_genre_2')
        expect(genres[0]).to.have.property('game_count', 2);
    });
});
