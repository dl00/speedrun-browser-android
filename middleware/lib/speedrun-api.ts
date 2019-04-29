import * as _ from 'lodash';

/// Constants and type definitions for the speedrun.com api
/// Docs: https://github.com/speedruncomorg/api/blob/master/version1/games.md#bulkaccess

export const API_PREFIX = 'https://www.speedrun.com/api/v1'

export interface BaseUpstream {
    links: Asset[]
}

export interface UpstreamData<T> {
    data: T[]
}

export function normalize(d: BaseUpstream) {
    // remove unnecessary links which we do not use
    delete d.links;
}

export interface BaseMiddleware extends BaseUpstream {
    updated?: string
}

export interface Names {
    international: string
    [index: string]: string
}

export interface Asset {
    uri: string,
    width: number,
    height: number
}

export interface BulkGame {
    id: string
    names: Names
    abbreviation: string
    weblink: string
    assets: BulkGameAssets
    platforms: string[]|UpstreamData<Platform>|Platform[]
    regions: string[]|UpstreamData<Region>|Region[]
    genres: string[]|UpstreamData<Genre>|Genre[]
    'release-date': string
}

export interface GameAssets {
    logo: Asset
    'cover-tiny': Asset
    'cover-small': Asset
    'cover-medium': Asset
    'cover-large': Asset
    icon: Asset
    'trophy-1st': Asset
    'trophy-2nd': Asset
    'trophy-3rd': Asset
    'trophy-4th': Asset
    background: Asset
    foreground: Asset
}

export interface BulkGameAssets {
    'cover-large': Asset
    'trophy-1st': Asset
    'trophy-2nd': Asset
    'trophy-3rd': Asset
    'trophy-4th': Asset
}

export function game_assets_to_bulk(game_assets: GameAssets): BulkGameAssets {
    return _.pick(game_assets, 'cover-large', 'trophy-1st', 'trophy-2nd', 'trophy-3rd', 'trophy-4th');
}

export interface Game extends BulkGame, BaseMiddleware {
    released: number
    romhack: boolean
    developers: string[]
    publishers: string[]|Publisher[]
    created: string
    assets: GameAssets
}

/// TODO: Use decorators
export function game_to_bulk(game: Game): BulkGame {
    let bulkGame: BulkGame = _.pick(game, 'id', 'names', 'abbreviation', 'weblink',
        'assets', 'platforms', 'regions', 'genres', 'release-date');

    bulkGame.assets = game_assets_to_bulk(game.assets);

    return bulkGame
}

export function normalize_game(d: Game) {
    normalize(d);

    if(d.platforms && (<UpstreamData<Platform>>d.platforms).data) {
        d.platforms = (<UpstreamData<Platform>>d.platforms).data;
    }

    if(d.regions && (<UpstreamData<Region>>d.regions).data) {
        d.regions = (<UpstreamData<Region>>d.regions).data;
    }

    if(d.genres && (<UpstreamData<Genre>>d.genres).data) {
        d.genres = (<UpstreamData<Genre>>d.genres).data;
    }

    for(let platform in d.platforms) {
        normalize((<any>d.platforms)[platform]);
    }
    for(let region in d.regions) {
        normalize((<any>d.regions)[region]);
    }

    for(let genre in d.genres) {
        normalize((<any>d.genres)[genre]);
    }
}

export interface BulkCategory {
    id: string
    name: string
    type: string

    variables: UpstreamData<Variable>|Variable[]
}

export interface Category extends BulkCategory, BaseMiddleware {
    weblink: string
    rules: string
    miscellaneous: boolean
}

export function category_to_bulk(category: Category): BulkCategory {
    return _.pick(category, 'id', 'name', 'type', 'variables');
}

export function normalize_category(d: Category) {
    normalize(d);

    if(d.variables && (<UpstreamData<Variable>>d.variables).data) {
        d.variables = (<UpstreamData<Variable>>d.variables).data;
    }

    for(let variable in d.variables) {
        normalize((<any>d.variables)[variable]);
    }
}

export interface Variable extends BaseMiddleware {
    id: string
    values: any[]
}

export interface BulkLevel {
    id: string
    name: string
}

export interface Level extends BulkLevel, BaseMiddleware {}

export function level_to_bulk(level: Level): BulkLevel {
    return _.pick(level, 'id', 'name');
}

export interface LeaderboardRunEntry {
    place: number
    run: BulkRun
}

export interface Leaderboard extends BaseMiddleware {
    weblink: string
    game: Game|string
    category: Category|string
    level: string
    platform: string
    region: string
    emulators: string
    'video-only': boolean
    runs: LeaderboardRunEntry[]

    players: {[id: string]: User}|{data: User[]}
}

// leaderboards can have subcategories. correct the places returned by the speedrun
// api to take these subcategories into account
export function correct_leaderboard_run_places(d: Leaderboard, vars: Variable[]) {

    let subcategory_vars = _.filter(vars, 'is-subcategory');

    let last_places: {[key: string]: number} = {};

    if(d.runs) {
        for(let run of d.runs) {
            let subcategory_id = '';

            for(let v of subcategory_vars) {
                subcategory_id += run.run.values[v.id];
            }

            last_places[subcategory_id] = last_places[subcategory_id] ?
                last_places[subcategory_id] + 1 : 1;

            run.place = last_places[subcategory_id];
        }
    }
}

export function normalize_leaderboard(d: Leaderboard) {

    normalize(d);

    if(d.runs) {
        d.runs.map((v) => {
            v.run = run_to_bulk(<Run>v.run);

            return v;
        });
    }

    delete d.players;
}

export interface RunTimes {
    primary: string
    realtime: string
    realtimeNoloads: string
    ingame: string
}

export interface RunSystem {
    platform: string
    emulated: boolean
    region: string
}

export interface BulkRun {
    id: string
    date: string
    players: BulkUser[]
    times: RunTimes
    system: RunSystem
    values: {[key: string]: string}
}

export interface Run extends BulkRun, BaseMiddleware {
    weblink: string
    game: BulkGame|string
    level: BulkLevel|string
    category: BulkCategory|string
    submitted: string
    videos: {
        text: string
        links: {
            uri: string
        }[]
    },

    comment: string
    status: {
        status: 'new'|'verified'|'rejected'
        examiner?: User|string
        'verify-date': string
    }

    values: {[key: string]: string}
}

export function normalize_run(d: Run) {
    normalize(d);

    if(d.players) {
        d.players = d.players.map(<any>user_to_bulk);
    }

    if(typeof d.game === 'object')
        d.game = game_to_bulk(<Game>d.game);
    if(typeof d.category === 'object')
        d.category = category_to_bulk(<Category>d.category);
    if(typeof d.level === 'object')
        d.level = level_to_bulk(<Level>d.level);
}

/// TODO: Use decorators
export function run_to_bulk(run: Run): BulkRun {
    let newr = _.pick(run, 'id', 'date', 'players', 'times', 'system', 'values');

    newr.players = newr.players.map(v => user_to_bulk(<User>v));

    return newr;
}

export interface GamePersonalBests {
    id: string
    names: Names
    assets: BulkGameAssets

    categories: {[id: string]: CategoryPersonalBests}
}

export interface CategoryPersonalBests {
    id: string
    name: string
    type: string

    levels?: {[id: string]: LevelPersonalBests}

    run?: LeaderboardRunEntry
}

export interface LevelPersonalBests {
    id: string
    name: string

    run: LeaderboardRunEntry
}

export interface BulkUser {
    id: string,
    names?: Names,
    name?: string,
    'name-style'?: {
        style: 'solid'|''
        color: {
            light: string
            dark: string
        }
    }
}

export interface User extends BulkUser, BaseMiddleware {
    weblink?: string
    role?: 'banned'|'user'|'trusted'|'moderator'|'admin'|'programmer'
    signup?: string

    bests: {[id: string]: GamePersonalBests}
}

export function user_to_bulk(user: User) {
    return _.pick(user, 'id', 'names', 'name', 'name-style');
}

export interface Platform {
    id: string
    name: string
    released: number
}

export interface Region {
    id: string
    name: string
}

export interface Publisher {
    id: string
    name: string
}

export interface Genre {
    id: string
    name: string
}
