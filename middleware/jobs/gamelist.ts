// Generates an effective, searchable index of games to browse. Takes into account:
// * Game popularity (according to wikipedia data)
// * Autocomplete (indexer) indexing

import * as _ from 'lodash';

import * as puller from '../lib/puller';

import { Category, CategoryDao } from '../lib/dao/categories';
import { Game, GameDao, BulkGame } from '../lib/dao/games';
import { GameGroup, GameGroupDao } from '../lib/dao/game-groups';
import { Level, LevelDao } from '../lib/dao/levels';

import { CursorData, Sched } from '../sched/index';

const GAME_BATCH_COUNT = 50;

export interface SRCGame extends BulkGame {
    categories: { data: Category[] },
    levels: { data: Level[] },

    released: number,

    created: string
}

export async function generate_games(sched: Sched, cur: CursorData<SRCGame>|null): Promise<CursorData<SRCGame>|null> {
    const res = await puller.do_pull(sched.storedb, `/games?embed=levels,categories,platforms,regions,developers,publishers,genres&max=${GAME_BATCH_COUNT}&offset=${cur?.pos || 0}`);

    const nextPos = cur ? parseInt(cur.pos!) : GAME_BATCH_COUNT;

    return {
        items: res.data.data,
        asOf: Date.now(),
        desc: `games ${nextPos}..${nextPos + GAME_BATCH_COUNT}`,
        done: (cur?.done || 0) + res.data.data.length,
        total: 0,
        pos: res.data.pagination.max == res.data.pagination.size ? (nextPos + GAME_BATCH_COUNT).toString() : null
    }
}

export async function apply_games(sched: Sched, cur: CursorData<SRCGame>) {
    const games: Game[] = [];
    const categories: Category[] = [];
    const levels: Level[] = [];
    const gameGroups: GameGroup[] = [];

    for(const g of cur.items) {
        categories.push(...g.categories.data.map(c => {
            c.game = g.id;
            return c;
        }));
        levels.push(...g.levels.data.map(l => {
            l.game = g.id;
            return l;
        }));

        for(let groupable of ['genres', 'platforms', 'developers', 'publishers']) {
            let ggg: GameGroup[] = (g as { [key: string]: any })[groupable].data;

            if (ggg && ggg.length) {
                let type = groupable.substr(0, groupable.length - 1);
                let moreGameGroups = _.map(
                    _.cloneDeep(ggg), v => {
                        v.type = type;
                        return v;
                    });
                
                gameGroups.push(...moreGameGroups);
            }
        }

        games.push(g as any);
    }

    // write everything to the db
    await Promise.all([
        new GameDao(sched.storedb).save(games),
        new CategoryDao(sched.storedb).save(categories),
        new LevelDao(sched.storedb).save(categories),
        new GameGroupDao(sched.storedb).save(gameGroups)
    ]);

    // TODO: rescore for game, game groups
}