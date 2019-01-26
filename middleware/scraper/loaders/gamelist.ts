// Generates an effective, searchable index of games to browse. Takes into account:
// * Game popularity (according to wikipedia data)
// * Autocomplete (indexer) indexing

import * as speedrun_api from '../../lib/speedrun-api';

import * as wikipedia from '../../lib/wikipedia';

import request from 'request-promise-native';

export async function load(runid: string, options: any) {

    let res = await request(speedrun_api.API_PREFIX + '/games', {
        qs: {
            _bulk: 'yes',
            max: 1000,
            offset: options ? options.offset : 0
        }
    });

    
}