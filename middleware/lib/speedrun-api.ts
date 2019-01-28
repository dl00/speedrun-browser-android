/// Constants and type definitions for the speedrun.com api
/// Docs: https://github.com/speedruncomorg/api/blob/master/version1/games.md#bulkaccess

export const API_PREFIX = 'https://www.speedrun.com/api/v1'

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

export interface Game extends BulkGame {
    released: number
    'release-date': string
    romhack: boolean
    platforms: string[]|Platform[]
    regions: string[]|Region[]
    genres: string[]
    developers: string[]
    publishers: string[]|Publisher[]
    created: string
    assets: {
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
}

export interface Category {
    id: string
    name: string
    weblink: string
    rules: string
    miscellaneous: boolean
}

export interface Leaderboard {
    weblink: string
    game: Game|string
    category: Category|string
    level: string
    platform: string
    region: string
    emulators: string
    'video-only': boolean
    runs: {
       place: number
       run: string|Run 
    }[]
}

export interface Run {
    id: string
    weblink: string
    game: Game|string
    level: string
    category: Category|string
    videos: {
        text: string
        links: {
            uri: string
        }[]
    }

    comment: string
    status: {
        status: 'new'|'verified'|'rejected'
        examiner?: User|string
        'verify-date': string
    }
}

export interface User {
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