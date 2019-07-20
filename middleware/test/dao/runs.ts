import * as _ from 'lodash';

import { CategoryDao } from '../../lib/dao/categories';
import { GameDao } from '../../lib/dao/games';
import { RunDao } from '../../lib/dao/runs';
import { LeaderboardDao } from '../../lib/dao/leaderboards';
import { ChartDao, BarChartData } from '../../lib/dao/charts';
import { load_db, close_db, DB } from '../../lib/db';
import { load_config } from '../../lib/config';

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

        await new CategoryDao(db).save([
            {
                id: 'a_category_on_genre',
                game: 'a_game_with_genre',
                name: 'Testing',
                type: 'per-game',
                weblink: '',
                miscellaneous: false,
                variables: []
            },
            {
                id: 'a_category',
                game: 'a_game',
                name: 'Testing',
                type: 'per-game',
                weblink: '',
                miscellaneous: false,
                variables: []
            }
        ]);

        await run_dao.save([
            {
                place: 3,
                run: {
                    id: 'another_run',
                    category: {id: 'a_category_on_genre'},
                    date: '2018-04-30',
                    status: {status: 'verified', 'verify-date': '2018-04-30'},
                    players: [],
                    times: { primary: '100', primary_t: 100 },
                    system: {},
                    values: {},
                    game: {id: 'a_game_with_genre'}
                }
            },
            {
                place: 1,
                run: {
                    id: 'one_run',
                    date: '2018-05-05',
                    category: {id: 'a_category'},
                    status: {status: 'verified', 'verify-date': '2018-05-05'},
                    players: [],
                    times: { primary: '135', primary_t: 100 },
                    system: {},
                    values: {},
                    game: {id: 'a_game'}
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
                category: {id: 'a_category_on_genre'},
                status: {status: 'verified', 'verify-date': '2018-05-01'},
                players: [],
                times: { primary: '135', primary_t: 100 },
                system: {},
                values: {},
                game: {id: 'a_game_with_genre'}
            }
        });

        runs = await run_dao.load_latest_runs(1);

        expect(runs[0]).to.have.property('place', 2);
        expect(runs[1]).to.have.property('place', 3);

        runs = await run_dao.load_latest_runs(0, 'my_genre');

        expect(runs[0]).to.have.property('place', 2);
        expect(runs[1]).to.have.property('place', 3);
    });

    it('should not record latest runs to index if they are not real', async () => {

        let run_dao = new RunDao(db);

        await run_dao.save({
            place: 3,
            run: {
                id: 'dummy_run',
                date: '2018-05-18',
                category: {id: 'a_category_on_genre'},
                status: {status: 'verified', 'verify-date': '2018-05-20'},
                players: [],
                times: { primary: '0', primary_t: 0.001 },
                system: {},
                values: {},
                game: {id: 'a_game_with_genre'}
            }
        });

        let runs = await run_dao.load_latest_runs(0);
        expect(runs[0]!.run.id).to.not.eql('dummy_run');
    });

    it('should generate volume submission charts with month gaps filled as necessary', async () => {

        let run_dao = new RunDao(db);

        await run_dao.save({
            place: 3,
            run: {
                id: 'dummy_run2',
                date: '2018-08-18',
                category: {id: 'a_category_on_genre'},
                status: {status: 'verified', 'verify-date': '2018-08-20'},
                players: [],
                times: { primary: '0', primary_t: 0.001 },
                system: {},
                values: {},
                game: {id: 'a_game_with_genre'}
            }
        });

        let chart = await run_dao.get_game_submission_volume('a_game_with_genre');

        expect(chart.data.main).to.have.length(5);
        expect((<BarChartData>chart.data.main[0]).x).to.eql(new Date('2018-04-01').getTime() / 1000);
        expect((<BarChartData>chart.data.main[0]).y).to.eql(1);
        expect((<BarChartData>chart.data.main[1]).x).to.eql(new Date('2018-05-01').getTime() / 1000);
        expect((<BarChartData>chart.data.main[1]).y).to.eql(2);
    });

    it('should set supporting object structures', async () => {
        // there should be a leaderboard with the previously loaded runs
        let leaderboard = (await new LeaderboardDao(db).load('a_category_on_genre'))[0];

        expect(leaderboard).to.exist;
        expect(leaderboard!.runs).to.have.length(1); // players and subcategories are always the same so there will only be 1 entry
        expect(leaderboard!.runs[0].run).to.have.property('id', 'dummy_run2');
    });
});


describe('RecentChartIndex', () => {
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


    it('should create chart data for no subcategories', async () => {

        let run_dao = new RunDao(db);
        let chart_dao = new ChartDao(db);

        await new GameDao(db).save({
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
        });

        await new CategoryDao(db).save({
            id: 'mysubcategory',
            name: 'Testing',
            type: 'per-game',
            weblink: '',
            miscellaneous: false,
            variables: []
        });

        let saved_runs = [
            {
                run: {
                    id: 'invalid_run',
                    date: '2018-04-30',
                    status: {status: 'rejected', 'verify-date': '2018-04-30'},
                    players: [],
                    times: { primary: '50', primary_t: 50 },
                    system: {},
                    values: {},
                    game: {id: 'a_game'},
                    category: {id: 'mysubcategory'}
                }
            },
            {
                run: {
                    id: 'another_run',
                    date: '2018-04-30',
                    status: {status: 'verified', 'verify-date': '2018-04-30'},
                    players: [],
                    times: { primary: '100', primary_t: 100 },
                    system: {},
                    values: {},
                    game: {id: 'a_game'},
                    category: {id: 'mysubcategory'}
                }
            },
            {
                run: {
                    id: 'yet_another_run',
                    date: '2018-04-31',
                    status: {status: 'verified', 'verify-date': '2018-05-03'},
                    players: [],
                    times: { primary: '110', primary_t: 110 },
                    system: {},
                    values: {},
                    game: {id: 'a_game'},
                    category: {id: 'mysubcategory'}
                }
            },
            {
                place: 1,
                run: {
                    id: 'one_run',
                    date: '2018-05-05',
                    status: {status: 'verified', 'verify-date': '2018-05-05'},
                    players: [],
                    times: { primary: '95', primary_t: 95 },
                    system: {},
                    values: {},
                    game: {id: 'a_game'},
                    category: {id: 'mysubcategory'}
                }
            }
        ];

        await run_dao.save(saved_runs);

        // check the generated chart
        let chart = (await chart_dao.load('leaderboards_mysubcategory'))[0];

        expect(chart).to.exist;
        expect(chart!.data.main).to.have.lengthOf(2);
        expect(chart!.data.main[1]).to.have.property('y', 95);
    });

    it('should create chart data for multiple subcategories', async () => {

        let run_dao = new RunDao(db);
        let chart_dao = new ChartDao(db);

        new CategoryDao(db).save({
            id: 'variabled_subcategory',
            name: 'Testing',
            type: 'per-game',
            weblink: '',
            miscellaneous: false,
            variables: [
                {
                    id: 'var_1',
                    'is-subcategory': true,
                    values: {
                        values: {
                            'var_1_a': {label: 'Var 1 A'},
                            'var_1_b': {label: 'Var 1 B'}
                        }
                    },
                },
                {
                    id: 'var_2',
                    'is-subcategory': true,
                    values: {
                        values: {
                            'var_2_a': {label: 'Var 2 A'},
                            'var_2_b': {label: 'Var 2 B'}
                        }
                    }
                },
                {
                    id: 'var_3',
                    'is-subcategory': false,
                    values: {
                        values: {
                            'var_3_a': {label: 'Var 3 A'},
                            'var_3_b': {label: 'Var 3 B'}
                        }
                    }
                }
            ]
        });

        let saved_runs = [
            {
                run: {
                    id: 'another_run_2',
                    date: '2018-04-30',
                    status: {status: 'verified', 'verify-date': '2018-04-30'},
                    players: [],
                    times: { primary: '100', primary_t: 100 },
                    system: {},
                    values: {
                        'var_1': 'var_1_b',
                        'var_2': 'var_2_a'
                    },
                    game: {id: 'a_game'},
                    category: {id: 'variabled_subcategory'}
                }
            },
            {
                run: {
                    id: 'yet_another_run_2',
                    date: '2018-04-31',
                    status: {status: 'verified', 'verify-date': '2018-05-03'},
                    players: [],
                    times: { primary: '110', primary_t: 110 },
                    system: {},
                    values: {
                        'var_1': 'var_1_b',
                        'var_2': 'var_2_b'
                    },
                    game: {id: 'a_game'},
                    category: {id: 'variabled_subcategory'}
                }
            },
            {
                place: 1,
                run: {
                    id: 'one_run_2',
                    date: '2018-05-05',
                    status: {status: 'verified', 'verify-date': '2018-05-05'},
                    players: [],
                    times: { primary: '95', primary_t: 95 },
                    system: {},
                    values: {
                        'var_1': 'var_1_b',
                        'var_2': 'var_2_a'
                    },
                    game: {id: 'a_game'},
                    category: {id: 'variabled_subcategory'}
                }
            }
        ];

        await run_dao.save(saved_runs);

        // check the generated chart
        let chart = (await chart_dao.load('leaderboards_variabled_subcategory'))[0];

        expect(chart).to.exist;
        expect(chart!.data.var_1_var_1_b_var_2_var_2_a).to.have.lengthOf(2);
        expect(chart!.data.var_1_var_1_b_var_2_var_2_a[1]).to.have.property('y', 95);

        expect(chart!.data.var_1_var_1_b_var_2_var_2_b).to.have.lengthOf(1);
    });
});
