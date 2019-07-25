import * as _ from 'lodash';

import { correct_leaderboard_run_places, add_leaderboard_run } from '../../lib/dao/leaderboards';

import { expect } from 'chai';

import 'mocha';
import { Run } from '../../lib/dao/runs';
import { Leaderboard } from '../../lib/dao/leaderboards';

describe('correct_leaderboard_run_places', () => {
    it('should have a monotonically increasing list with no ties or subcategories', async () => {

        let lb = {
            weblink: '',
            game: '',
            category: '',
            players: {},
            runs: [
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '13T',
                            primary_t: 13
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '20T',
                            primary_t: 20
                        }
                    }
                }
            ]
        };

        correct_leaderboard_run_places(lb, [])

        expect(lb.runs[0]).to.have.property('place', 1);
        expect(lb.runs[2]).to.have.property('place', 3);
    });

    it('should handle ties', async () => {
        let lb = {
            weblink: '',
            game: '',
            category: '',
            players: {},
            runs: [
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '20T',
                            primary_t: 20
                        }
                    }
                }
            ]
        };

        correct_leaderboard_run_places(lb, [])

        expect(lb.runs[0]).to.have.property('place', 1);
        expect(lb.runs[1]).to.have.property('place', 1);
        expect(lb.runs[2]).to.have.property('place', 3);
    });

    it('should partition subcategories', async () => {
        let lb = {
            weblink: '',
            game: '',
            category: '',
            players: {},
            runs: [
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {subc: 'one', test: 'argo'},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {subc: 'two', test: 'arlo'},
                        times: {
                            primary: '12T',
                            primary_t: 13
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {subc: 'one', test: 'ammo'},
                        times: {
                            primary: '20T',
                            primary_t: 20
                        }
                    }
                },
                {
                    run: {
                        id: 'one',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {subc: 'two', test: 'algo'},
                        times: {
                            primary: '20T',
                            primary_t: 21
                        }
                    }
                }
            ]
        };

        correct_leaderboard_run_places(lb, [
            { id: 'subc', 'is-subcategory': true, values: []},
            { id: 'test', 'is-subcategory': false, values: []}
        ])

        expect(lb.runs[0]).to.have.property('place', 1);
        expect(lb.runs[1]).to.have.property('place', 1);
        expect(lb.runs[2]).to.have.property('place', 2);
        expect(lb.runs[3]).to.have.property('place', 2);
    });
});

describe('add_leaderboard_run', () => {
    it('should add verified run', async () => {
        let lb = {
            weblink: '',
            game: '',
            category: '',
            players: {},
            runs: [
                {
                    run: {
                        id: 'two',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'another',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '12T',
                            primary_t: 12
                        }
                    }
                },
                {
                    run: {
                        id: 'thing',
                        date: '2019-05-05',
                        players: [],
                        system: {},
                        values: {},
                        times: {
                            primary: '20T',
                            primary_t: 20
                        }
                    }
                }
            ]
        };

        let lre = add_leaderboard_run(lb, <Run><unknown>{
            id: 'one',
            date: '2019-05-05',
            players: [{id: 'dummy'}],
            status: {'verify-date': '2019-05-06', status: 'verified'},
            system: {},
            values: {},
            times: {
                primary: '17T',
                primary_t: 17
            }
        }, []);

        expect(lre.place).to.eql(3);
        expect(lb.runs[2].run.times.primary_t).to.eql(17);

        // try to add a non-verified run
        lre = add_leaderboard_run(lb, <Run><unknown>{
            id: 'yet',
            date: '2019-05-05',
            players: [{id: 'dummy'}],
            status: {status: 'rejected'},
            system: {},
            values: {},
            times: {
                primary: '16T',
                primary_t: 16
            }
        }, []);

        expect(lre.place).to.eql(3);
        expect(lb.runs[2].run.times.primary_t).to.eql(17);

        // bug: if the time gets converted to a string then runs could be elevated way higher than they should be!
        lre = add_leaderboard_run(lb, <Run><unknown>{
            id: 'last',
            date: '2019-05-06',
            players: [{id: 'dummy'}],
            status: {'verify-date': '2019-05-06', status: 'verified'},
            system: {},
            values: {},
            times: {
                primary: '18M20T',
                primary_t: 1100
            }
        }, []);

        expect(lre.place).to.not.eql(1);

        expect(lb.runs[2].run.times.primary_t).to.eql(20);


        // bug: this run is "newer", so it should be accepted over the faster run by the same player
        expect(lre.place).to.eql(4);
    });

    it('should add run to empty leaderboard', async () => {
        let lbs: Leaderboard[] = [
            {
                weblink: '',
                game: '',
                category: '',
                players: {},
                runs: []
            },
            <Leaderboard><unknown>{
                weblink: '',
                game: '',
                category: '',
                players: {},
                runs: null
            }
        ];

        let run_to_add = <Run><unknown>{
            id: 'one',
            date: '2019-05-05',
            players: [{id: 'dummy'}],
            status: {status: 'verified', 'verify-date': '2019-05-05'},
            system: {},
            values: {},
            times: {
                primary: '16T',
                primary_t: 16
            }
        };

        for(let lb of lbs) {
            let lre = add_leaderboard_run(lb, run_to_add, []);

            expect(lre.place).to.eql(1);
            expect(lb.runs[0]).to.exist;
            expect(lb.runs).to.have.length(1);
        }
    });
});
