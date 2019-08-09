import * as _ from 'lodash';

import { RunDao, Run, LeaderboardRunEntry, NewRecord } from './';
import { IndexDriver, DaoConfig } from '../';
import { CategoryDao, Category, BulkCategory } from '../categories';
import { BulkLevel } from '../levels';
import { LeaderboardDao, Leaderboard, correct_leaderboard_run_places } from '../leaderboards';
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

        for(let leaderboard_id in leaderboards) {

            if(!leaderboards[leaderboard_id]) {
                // new leaderboard
                let category_id = leaderboard_id.split('_')[0];
                let level_id = leaderboard_id.split('_')[1];

                if(!categories[category_id])
                    continue;

                leaderboards[leaderboard_id] = {
                	game: <string>categories[category_id]!.game,
                    weblink: '',
                	category: category_id,
                    players: {},
                    runs: []
                };

                if(level_id)
                    leaderboards[leaderboard_id].level = level_id;
            }

            let category = categories[<string>leaderboards[leaderboard_id].category];
            if(!category)
                continue;

            leaderboards[leaderboard_id].runs = await (<RunDao>conf).calculate_leaderboard_runs(<string>leaderboards[leaderboard_id].game, <string>leaderboards[leaderboard_id].category, <string|undefined>leaderboards[leaderboard_id].level);

            correct_leaderboard_run_places(leaderboards[leaderboard_id], <Variable[]>category.variables);
        }

        let clean_leaderboards = _.reject(_.values(leaderboards), _.isNil);

        if(clean_leaderboards.length)
            await new LeaderboardDao(conf.db).save(clean_leaderboards);
    }

    async update_player_pbs(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[], _categories: {[key: string]: Category|null}) {
        this.new_records.push(...await new UserDao(conf.db).apply_runs(runs));
    }

    async update_obsoletes(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[], categories: {[key: string]: Category|null}) {

        let filter: any = {
            $or: _.filter(runs, 'run.category.id').map(run => {

                let filter: any = {
                    'run.game.id': run.run.game.id,
                    'run.category.id': run.run.category.id,
                    'run.date': {$lt: run.run.date},
                };

                if(run.run.level)
                    filter['run.level.id'] = run.run.level.id;

                // matching players
                filter['run.players'] = {$size: run.run.players.length};
                run.run.players.forEach((player, i) => {
                    filter[`run.players.${i}.id`] = player.id
                });


	        // return early if we dont have a category with variables to pull
                if(!categories[run.run.category.id])
                    return filter;

                let subcategory_var_ids = _.chain(categories[run.run.category.id]!.variables)
                    .filter('is-subcategory')
                    .map('id')
                    .value();

                // matching subcategories
                for(let id of subcategory_var_ids) {
                    if(run.run.values[id])
                        filter[`run.values.${id}`] = run.run.values[id];
                }

                // we check if its false here because the behavior of `obsoletes` appears to be as follows:
                // if false, only mark as obsolete if the subcategory variable remains the same
                // if true, always mark as obsolete regardless of filter value (aka no filter needed)
                let obsoletes_var_ids = _.chain(categories[run.run.category.id]!.variables)
                    .reject('obsoletes')
                    .map('id')
                    .value();

                // matching "obsoletes" var ids
                for(let id of obsoletes_var_ids) {
                    if(run.run.values[id])
                        filter[`run.values.${id}`] = run.run.values[id];
                }

                return filter;
            })
        };

        await conf.db.mongo.collection(conf.collection).updateMany(filter, {
            $set: {'obsolete': true}
        });
    }

    async load(_conf: DaoConfig<LeaderboardRunEntry>, _keys: string[]): Promise<(LeaderboardRunEntry|null)[]> {
        throw new Error('cannot load data from supporting structures');
    }

    async apply(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[]) {
        let category_ids = <string[]>_.uniq(_.map(runs, 'run.category.id'));
        let categories = _.zipObject(category_ids, await new CategoryDao(conf.db!).load(category_ids));

        await this.update_obsoletes(conf, _.cloneDeep(runs), categories);

        await Promise.all([
            this.update_leaderboard(conf, runs, categories),
            this.update_player_pbs(conf, runs, categories),
        ]);
    }

    async clear(conf: DaoConfig<LeaderboardRunEntry>, runs: LeaderboardRunEntry[]) {
        await this.apply(conf, runs);
    }

    has_changed(_old_obj: LeaderboardRunEntry, _new_obj: LeaderboardRunEntry): boolean {
        return true//!_.isEqual(old_obj, new_obj);
    }
}
