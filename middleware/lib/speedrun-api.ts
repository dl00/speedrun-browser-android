import * as _ from 'lodash';

import { LeaderboardRunEntry } from './dao/runs';
import { BulkGameAssets } from './dao/games';

/// Constants and type definitions for the speedrun.com api
/// Docs: https://github.com/speedruncomorg/api/blob/master/version1/games.md#bulkaccess

export const API_PREFIX = 'https://www.speedrun.com/api/v1'

export interface BaseUpstream {
    links?: Asset[]
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

export interface BulkCategory {
    id: string
    name: string
    type: string

    variables?: UpstreamData<Variable>|Variable[]
}

export interface Category extends BulkCategory, BaseMiddleware {
    weblink: string
    rules?: string
    miscellaneous: boolean
}

export function category_to_bulk(category: Category): BulkCategory {
    let ret = _.pick(category, 'id', 'name', 'type', 'variables');

    if(category.variables) {
        for(let v of <Variable[]>category.variables) {
            // remove any rules embedded in the values which can be annoyingly large
            for(let val in v.values.values) {
                delete (<any>v.values.values)[val].rules;
            }
        }
    }

    return ret;
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
