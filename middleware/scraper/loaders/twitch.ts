// Maintains an updated list of streams from twitch by polling

import * as _ from 'lodash'

import * as request from 'request-promise-native';

import { StreamDao, Stream } from '../../lib/dao/streams';
import { UserDao, User, user_to_bulk } from '../../lib/dao/users';

import * as scraper from '../index';
import { GameDao, Game, extract_game_group_ids, game_to_bulk } from '../../lib/dao/games';

const TWITCH_STREAMS_URL = 'https://api.twitch.tv/helix/streams'
const TWITCH_GAMES_URL = 'https://api.twitch.tv/helix/games/top'
const TWITCH_ID_TOKEN_URL = 'https://id.twitch.tv/oauth2/token'

let TWITCH_BEARER_TOKEN = ''

interface TwitchRawStream {
    user_name: string | number;
    title: any;
    viewer_count: any;
    language: any;
    thumbnail_url: any;
    started_at: any;
}

export function extract_user_twitch_login(twitch_url: string|undefined) {
    if(!twitch_url)
        return null

    const m = twitch_url.match(/twitch.(tv|com)\/([^?/]*)/)
    if(!m)
        return null

    if(!m[2].match(/^[a-zA-Z0-9_]{4,25}$/) || m[2][0] === '_')
        return null

    return m[2]
}

async function refresh_token() {
    const twitch_response = await request.post(TWITCH_ID_TOKEN_URL, {
        qs: {
            client_id: scraper.config.scraper.twitchApiToken,
            client_secret: scraper.config.scraper.twitchApiSecret,
            grant_type: 'client_credentials'
        },
        json: true
    })

    TWITCH_BEARER_TOKEN = twitch_response.access_token
}

async function poll_stream_statuses(game_dao: GameDao, user_dao: UserDao, user_logins: { user_id: string, user_login: string}[]): Promise<Stream[]> {
    //const qs = user_logins.map(ul => 'user_login=' + encodeURIComponent(ul.user_login)).join('&') + '&first=100'

    const keyed_logins = _.keyBy(user_logins, v => v.user_login.toLowerCase())

    const raw_streams = (await request.get(TWITCH_STREAMS_URL, {
        json: true,
        qs: {
            first: 100,
            user_login: _.map(user_logins, 'user_login')
        },
        auth: {
            bearer: TWITCH_BEARER_TOKEN
        }
    })).data

    if(!raw_streams.length)
        return []

    // get the games corresponding to all these streams
    const mapped_games = await game_dao.load_by_index('twitch_id', _.map(raw_streams, 'game_id'))
    const mapped_users = await user_dao.load(_.map(raw_streams, (rs) => {
        const kl = keyed_logins[(<string>rs.user_name).toLowerCase()]
        if(!kl)
            // this can cometimes happen
            return 'bogus';
        
        return kl.user_id;
    }))

    return _.filter(_.zipWith(raw_streams, mapped_games, mapped_users, (rs: TwitchRawStream, game: Game|null, user: User|null) => {
        if(!user)
            return null;

        return {
            user: user ? user_to_bulk(user) : null,
            user_name: rs.user_name,
            game: game ? game_to_bulk(game) : null,
            gg_ids: game ? extract_game_group_ids(game) : [],
            title: rs.title,
            viewer_count: rs.viewer_count,
            language: rs.language,
            thumbnail_url: rs.thumbnail_url,
            started_at: rs.started_at
        }
    }), _.isObject) as Stream[]
}

// maps twitch game ids to SRC game ids using text match
export async function pull_all_games() {
    await refresh_token()

    let game_dao = await new GameDao(scraper.storedb!);
    let nextPage: string = '';
    let doneCount = 0;
    let maxDone = await game_dao.count()

    try {
        let twitch_games: any[] = []
        do {
            const twitch_response = await request.get(TWITCH_GAMES_URL, {
                qs: { after: nextPage || undefined, first: 100 },
                json: true,
                auth: {
                    bearer: TWITCH_BEARER_TOKEN
                }
            })

            twitch_games = twitch_response.data
            doneCount += twitch_games.length
            nextPage = twitch_response.pagination.cursor

            const save_games: Game[] = []

            for(const game of twitch_games) {
                // find the game in our db by name
                const best_game = (await game_dao.load_by_index('autocomplete', game.name.toLowerCase()))[0]

                if(!best_game) {
                    console.log('[TWITCH] cannot map twitch game:', game.name, game.id)
                    continue
                }

                //console.log(`[TWITCH] ${best_game.id} is ${game.id} (${game.name})`)
                best_game.twitch_id = game.id
                save_games.push(best_game)
            }

            if(save_games.length)
                await game_dao.save(save_games)
        } while (nextPage && doneCount < maxDone)
    } catch (err) {
        console.error('loader/twitch: could not load game mapping for streams:', err);
        throw new Error('permanent');
    }
}

export async function pull_all_players() {
    // first, pull games
    await refresh_token()
    await pull_all_games()

    const stream_dao = await new StreamDao(scraper.storedb!);
    const game_dao = await new GameDao(scraper.storedb!);
    const user_dao = await new UserDao(scraper.storedb!);

    try {
        await user_dao.scan({batchSize: 1000}, async (users: User[]) => {
            let streams: Stream[] = [];

            let user_logins: { user_id: string, user_login: string}[] = []

            for(let user of users) {
                const user_login = extract_user_twitch_login(user.twitch ? user.twitch!.uri : undefined)
                if(!user_login)
                    continue;
                
                user_logins.push({ user_login, user_id: user.id })

                if(user_logins.length == 100) {
                    streams = streams.concat(await poll_stream_statuses(game_dao, user_dao, user_logins))
                    user_logins = []
                }
            }

            if(streams.length) {
                await stream_dao.save(streams);
            }

        });
    } catch (err) {
        console.error('loader/twitch: could not load all streams:', err);
        throw new Error('permanent');
    }

    console.log('[TWITCH] Done')
}

export async function pull_all_active_streams() {
    await refresh_token()

    const stream_dao = await new StreamDao(scraper.storedb!);
    const game_dao = await new GameDao(scraper.storedb!);
    const user_dao = await new UserDao(scraper.storedb!);

    try {
        let stream_count = 0;

        await stream_dao.scan({batchSize: 80}, async (streams: Stream[]) => {
            const new_streams: Stream[] = 
                await poll_stream_statuses(game_dao, user_dao, _.filter(_.map(streams, s => {
                    return {
                        user_login: s.user_name,
                        user_id: s.user.id
                    }
                })))

            // remove streams which are not a thing anymore
            // those are streams not returned by the func
            _.remove(streams, (os: Stream) => {
                    return _.find(new_streams, ns => ns.user.id === os.user.id)
                }
            )

            if(streams.length) {
                console.log('[TWITCH] end', streams.length, 'streams')
                await stream_dao.remove(_.map(streams, 'user.id'))
            }

            // save updated stream info the streams which are still a thing
            if(new_streams.length)
                await stream_dao.save(new_streams);

            stream_count += new_streams.length;
        });

        console.log('[TWITCH] updated', stream_count)
    } catch (err) {
        console.error('loader/twitch: could not load active streams:', err);
        throw new Error('permanent');
    }

    console.log('[TWITCH] Done')
}