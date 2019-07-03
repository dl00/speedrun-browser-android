import * as _ from 'lodash';

import { correct_leaderboard_run_places, add_leaderboard_run } from '../../lib/dao/leaderboards';

import { expect } from 'chai';

import 'mocha';
import { Run } from '../../lib/dao/runs';

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
            id: 'one',
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
    })
});