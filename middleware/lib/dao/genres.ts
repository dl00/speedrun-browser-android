import * as _ from 'lodash';

import * as assert from 'assert';

import { DB } from '../db';

import {
    BaseUpstream, normalize,
} from '../speedrun-api';

import { Dao, DaoConfig, IndexDriver, IndexerIndex } from './';

import { GameDao } from './games';

export interface Genre extends BaseUpstream {
    id: string;
    name: string;
    game_count?: number;
}

function get_genre_search_indexes(genre: Genre) {
    const indexes: Array<{ text: string, score: number, namespace?: string }> = [];
    indexes.push({ text: genre.name.toLowerCase(), score: genre.game_count || 1 });

    return indexes;
}

const POPULAR_GENRES_KEY = 'genre_rank';

class PopularGenresIndex implements IndexDriver<Genre> {
    public name: string;
    private max_return: number;

    constructor(name: string, max_return: number) {
        this.name = name;
        this.max_return = max_return;
    }

    public async load(conf: DaoConfig<Genre>, keys: string[]): Promise<Array<Genre|null>> {
        assert.equal(keys.length, 1, 'PopularGenresIndex only supports reading from a single key at a time');

        // we only read the first
        let offset;
        if (keys[0]) {
            offset = parseInt(keys[0]);
        }
        else {
            offset = 0;
        }

        const popular_game_ids: string[] = await conf.db.redis.zrevrange(POPULAR_GENRES_KEY,
            offset, offset + this.max_return - 1);

        if (!popular_game_ids.length) {
            return [];
        }

        return await conf.load(popular_game_ids);
    }

    public async apply(conf: DaoConfig<Genre>, genres: Genre[]) {
        // have to get games to deal with genre data
        const m = conf.db.redis.multi();

        for (const genre of genres) {
            const genre_score = genre.game_count || 0;

            // install master rank list
            m.zadd(POPULAR_GENRES_KEY, genre_score.toString(), genre.id);
        }

        await m.exec();
    }

    public async clear(conf: DaoConfig<Genre>, objs: Genre[]) {
        const keys = _.map(objs, conf.id_key);

        await conf.db.redis.zrem(POPULAR_GENRES_KEY,
            ...keys);
    }

    public has_changed(old_obj: Genre, new_obj: Genre): boolean {
        return old_obj.game_count != new_obj.game_count;
    }
}

export interface GenreDaoOptions {
    max_items?: number;
}

export class GenreDao extends Dao<Genre> {
    constructor(db: DB, options?: GenreDaoOptions) {
        super(db, 'levels', 'redis');

        this.id_key = _.property('id');

        this.indexes = [
            new IndexerIndex('genres', get_genre_search_indexes),
            new PopularGenresIndex('popular_genres', options && options.max_items ? options.max_items : 100),
        ];
    }

    // refreshes the score for a game by counting the number of games in that genre
    public async rescore_genre(ids: string|string[]) {

        if (!_.isArray(ids)) {
            ids = [ids];
        }

        const scores = await new GameDao(this.db).get_genre_count(ids);

        const genres = await this.load(ids);

        for (let i = 0; i < genres.length; i++) {
            if (genres[i]) {
                genres[i]!.game_count = scores[i];
            }
        }

        await this.save(_.reject(genres, _.isNil) as Genre[]);

        return genres;
    }

    public async load_popular(offset?: number) {
        return await this.load_by_index('popular_genres', `${offset || ''}`);
    }

    protected async pre_store_transform(genre: Genre): Promise<Genre> {
        normalize(genre);
        return genre;
    }
}
