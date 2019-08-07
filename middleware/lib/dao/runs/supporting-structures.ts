import * as _ from 'lodash';

import { Run, LeaderboardRunEntry, NewRecord } from './';
import { IndexDriver, DaoConfig } from '../';
import { CategoryDao, Category, BulkCategory } from '../categories';
import { BulkLevel } from '../levels';
import { LeaderboardDao, Leaderboard, add_leaderboard_run } from '../leaderboards';
import { UserDao } from '../users';
import { Variable } from '../../speedrun-api';

function get_leaderboard_id_for_run(run: Run) {

    if(!run.category || !(<BulkCategory>run.category).id)
        return;

    return (<BulkCategory>run.category).id +
        (run.level ? '_' + (<BulkLevel>run.level).id : '');
}

export class SupportingStructuresIndex implements IndexDriver<LeaderboardRunEntry> {
    name: string;

    new_records: NewRecord[];

    constructor(name: string) {
        this.name = name;

        this.new_records = [];
    }

    async update_leaderboard(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[], categories: {[key: string]: Category|null}) {
        let leaderboard_ids = <string[]>_.map(runs, run => get_leaderboard_id_for_run(<Run>run.run));

        let leaderboard_ids_deduped = _.uniq(leaderboard_ids);
        let leaderboards = <{[id: string]: Leaderboard}>_.zipObject(leaderboard_ids_deduped,
            await new LeaderboardDao(conf.db).load(leaderboard_ids_deduped));

        for(let run of runs) {

            if(!run.run.category || !run.run.category.id || !categories[(<BulkCategory>run.run.category).id])
                continue;

            let leaderboard = leaderboards[<string>get_leaderboard_id_for_run(<Run>run.run)];

            if(!leaderboard || !_.keys(leaderboard).length) {
                // new leaderboard
                leaderboard = {
                	game: run.run.game.id,
                    weblink: '',
                	category: run.run.category.id,
                    players: {},
                    runs: []
                };

                if(run.run.level && run.run.level.id)
                    leaderboard.level = run.run.level.id;

                leaderboards[<string>get_leaderboard_id_for_run(<Run>run.run)] = leaderboard;
            }

            add_leaderboard_run(
                leaderboard,
                <Run>run.run,
                <Variable[]>categories[(<BulkCategory>run.run.category).id]!.variables);
        }

        let clean_leaderboards = _.reject(_.values(leaderboards), _.isNil);

        if(clean_leaderboards.length)
            await new LeaderboardDao(conf.db).save(clean_leaderboards);
    }

    async update_player_pbs(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[], _categories: {[key: string]: Category|null}) {
        this.new_records.push(...await new UserDao(conf.db).apply_runs(runs));
    }

    async update_obsoletes(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[], categories: {[key: string]: Category|null}) {

        let subcategory_var_ids = _.chain(categories.variables)
            .filter('is-subcategory')
            .map('id')
            .value();

        await conf.db.mongo.collection(conf.collection).updateMany({
            $or: _.filter(runs, 'run.category.id').map(run => {
                let filter: any = {
                    'run.category.id': run.run.category.id,
                    'run.submitted': {$lt: run.run.submitted},
                };

                if(run.run.level)
                    filter['run.level.id'] = run.run.level.id;

                // matching subcategories
                for(let id of subcategory_var_ids) {
                    filter[`run.values.${id}`] = run.run.values[id];
                }

                // matching players
                filter['run.players'] = {$size: run.run.players.length};
                run.run.players.forEach((player, i) => {
                    filter[`run.players.${i}.id`] = player.id
                });

                return filter;
            })
        }, {
            $set: {'obsolete': true}
        });
    }

    async load(_conf: DaoConfig<LeaderboardRunEntry>, _keys: string[]): Promise<(LeaderboardRunEntry|null)[]> {
        throw new Error('cannot load data from supporting structures');
    }

    async apply(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[]) {
        let category_ids = <string[]>_.uniq(_.map(runs, 'run.category.id'));
        let categories = _.zipObject(category_ids, await new CategoryDao(conf.db!).load(category_ids));

        await Promise.all([
            this.update_leaderboard(conf, runs, categories),
            this.update_player_pbs(conf, runs, categories),
            this.update_obsoletes(conf, _.cloneDeep(runs), categories)
        ]);
    }

    async clear(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[]) {
        await this.apply(conf, runs);
    }

    has_changed(_old_obj: LeaderboardRunEntry, _new_obj: LeaderboardRunEntry): boolean {
        return true//!_.isEqual(old_obj, new_obj);
    }
}
