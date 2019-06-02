import * as _ from 'lodash';

import * as dao from '../lib/dao';
import { RedisMapIndex } from '../lib/dao/backing/redis';
import { load_db, close_db, DB } from '../lib/db';
import { load_config } from '../lib/config';

import { expect } from 'chai';

import 'mocha';

describe('Dao', () => {

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

    function saveLoad(backing_db: 'redis'|'mongo') {
        return async function() {
            let ao = new dao.Dao(db, 'tests');
            ao.backing = backing_db;
            ao.id_key = _.property('name');

            let objs = [
                {
                    test: '1',
                    name: 'something'
                },
                {
                    test: '2',
                    name: 'else'
                }
            ];

            await ao.save(objs);

            // test that returned documents are both there and preserve order
            var returned: any = await ao.load(['something', 'else']);
            expect(returned).to.eql(objs);
            returned = await ao.load(['else', 'something']);
            expect(returned).to.eql(objs.reverse());

            // update an object
            await ao.save({
                name: 'something',
                test: 'changed'
            });

            returned = await ao.load('something');
            expect(returned[0].test).to.eql('changed');

            await ao.remove('something');

            returned = await ao.load('something');
            expect(returned[0]).to.eql(null);
        }
    }

    it('should be able to save and load documents from redis', saveLoad('redis'));

    it('should be able to save and load documents from mongo', saveLoad('mongo'));

    it('should be able to set indexes', async () => {
        let ao = new dao.Dao(db, 'indexTests');
        ao.backing = 'mongo';
        ao.id_key = _.property('name');
        ao.indexes = [
            new RedisMapIndex('test-shorts', 'short')
        ];

        let objs = [
            {
                test: '1',
                name: 'something',
                short: 'one',
                group: 'fiesta'
            },
            {
                test: '2',
                name: 'else',
                short: 'two',
                group: 'waffle'
            },
            {
                test: '2',
                name: 'another',
                short: 'three',
                group: 'fiesta'
            }
        ];

        await ao.save(objs);

        let res = await ao.load_by_index('test-shorts', 'two');
        expect(res[0]).to.eql(objs[1]);

        // check for error on adding duplicate index
    });

    it('should be able to update indexes', async () => {
        let ao = new dao.Dao(db, 'indexTests');
        ao.backing = 'mongo';
        ao.id_key = _.property('name');
        ao.indexes = [
            new RedisMapIndex('test-shorts', 'short')
        ];

        await ao.save({
            test: 'changed2',
            name: 'another',
            short: 'foobar',
            group: 'fiesta'
        });

        let res = <any>await ao.load_by_index('test-shorts', 'foobar');
        expect(res[0].name).to.eql('another');
        res = await ao.load_by_index('test-shorts', 'three');
        expect(res[0]).to.not.exist;
    });
});
