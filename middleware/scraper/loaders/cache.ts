// Load the contents of a particular request and store it in the database as is

import * as _ from 'lodash';

import request from '../../lib/request';

import * as speedrun_db from '../../lib/speedrun-db';

import * as scraper from '../index';

export async function load(_runid: string, options: any) {
    let d;

    try {
        let res = await request(options.url);

        d = res.data;

        for(let loc of options.db_loc) {
            let h = speedrun_db.locs[loc.hash];

            if(!h)
                throw {statusCode: 600};
            
            let val = d;
            if(loc.sub) {
                // store as a subitem along with the existing data
                let raw = await scraper.storedb!.hget(h, options.id);
                val = {};
                if(raw)
                    val = JSON.parse(raw);
                
                _.set(val, loc.sub, d);
            }

            await scraper.storedb!.hset(h, options.id, JSON.stringify(val));
        }
    }
    catch(err) {
        console.error('loader/cache: Could not retrieve/store data:', options, err.statusCode);
        throw 'reschedule';
    }

    if(!options.chain)
        return;

    try {
        // is this an array? if so, we have to process it differently.
        if(d.length) {
            for(let item of d) {
                for(let link of options.chain) {
                    await scraper.push_call(scraper.template_call(link.call, item), link.priority);
                }
            }
        }
        else {
            for(let link of options.chain) {
                await scraper.push_call(scraper.template_call(link.call, d), link.priority);
            }
        }
    }
    catch(err) {
        console.error('loader/cache: Could not schedule resultant post-cache hooks:', options, err);

        // best way to solve this problem is to leave this running, let it get picked up later on better terms. although an error like this is extremely unlikely
        throw 'leave-open';
    }
}