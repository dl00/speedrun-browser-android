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

export interface Game extends BulkGame, BaseMiddleware {
    released: number
    'release-date': string
    romhack: boolean
    platforms: string[]|UpstreamData<Platform>|Platform[]
    regions: string[]|UpstreamData<Region>|Region[]
    genres: string[]
    developers: string[]
    publishers: string[]|Publisher[]
    created: string
    assets: GameAssets
}

export function normalize_game(d: Game) {
    normalize(d);

    if(d.platforms && (<UpstreamData<Platform>>d.platforms).data) {
        d.platforms = (<UpstreamData<Platform>>d.platforms).data;
    }

    if(d.regions && (<UpstreamData<Region>>d.regions).data) {
        d.regions = (<UpstreamData<Region>>d.regions).data;
    }

    for(let platform in d.platforms) {
        normalize((<any>d.platforms)[platform]);
    }
    for(let region in d.regions) {
        normalize((<any>d.regions)[region]);
    }
}

export interface Category extends BaseMiddleware {
    id: string
    name: string
    type: string
    weblink: string
    rules: string
    miscellaneous: boolean

    variables: UpstreamData<Variable>|Variable[]
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
    
}

export interface Level extends BaseMiddleware {
    id: string
    name: string
}

export interface LeaderboardRunEntry {
    place: number
    run: Run 
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

export function normalize_leaderboard(d: Leaderboard) {

    normalize(d);

    if(d.players && _.isArray((<any>d.players).data)) {
        d.players = _.keyBy((<any>d.players).data, 'id');
    }

    for(let player in d.players) {
        normalize((<any>d.players)[player]);
    }
}

export interface Run extends BaseMiddleware {
    id: string
    weblink: string
    game: Game|string
    level: string
    category: Category|string
    date: string
    submitted: string
    videos: {
        text: string
        links: {
            uri: string
        }[]
    },

    players: User[]

    comment: string
    status: {
        status: 'new'|'verified'|'rejected'
        examiner?: User|string
        'verify-date': string
    }
}

export interface GamePersonalBests {
    id: string
    names: Names
    assets: GameAssets

    categories: {[id: string]: CategoryPersonalBests}
}

export interface CategoryPersonalBests {
    id: string
    name: string
    type: string

    levels?: {[id: string]: LevelPersonalBests}

    run?: { place: number, run_id: string }
}

export interface LevelPersonalBests {
    id: string
    name: string
    
    run: { place: number, run_id: string }
}

export interface User extends BaseMiddleware {
    id: string
    names: Names
    weblink: string
    'name-style': {
        style: 'solid'|''
        color: {
            light: string
            dark: string
        }
    }
    role: 'banned'|'user'|'trusted'|'moderator'|'admin'|'programmer'
    signup?: string

    bests: {[id: string]: GamePersonalBests}
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
    id: string,
    name: string
}