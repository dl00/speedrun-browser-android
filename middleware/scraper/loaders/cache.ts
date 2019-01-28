// Load the contents of a particular request and store it in the database as is

import * as _ from 'lodash';

import request from '../../lib/request';

import * as speedrun_db from '../../lib/speedrun-db';

import * as scraper from '../index';

export async function load(_runid: string, options: any) {
    try {
        let res = await request(options.url);

        for(let loc of options.db_loc) {
            let h = speedrun_db.locs[loc.hash];

            if(!h)
                throw {statusCode: 600};
            
            let val = res.data;
            if(loc.sub) {
                // store as a subitem along with the existing data
                let raw = await scraper.storedb!.hget(h, options.id);
                val = {};
                if(raw)
                    val = JSON.parse(raw);
                
                _.set(val, loc.sub, res.data);
            }

            await scraper.storedb!.hset(h, options.id, JSON.stringify(val));
        }
    }
    catch(err) {
        console.error('loader/cache: Could not retrieve/store data:', options, err.statusCode);
        throw 'reschedule';
    }
}