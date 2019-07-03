import * as _ from 'lodash';

import { DaoConfig, IndexDriver } from '../';

import { ChartDao, Chart, LineChartData } from '../charts';

import { CategoryDao } from '../categories';

import { Variable } from '../../../lib/speedrun-api';

import { LeaderboardRunEntry, run_to_bulk } from './';

export class RecordChartIndex implements IndexDriver<LeaderboardRunEntry> {
    name: string;

    constructor(name: string) {
        this.name = name;
    }

    async make_chart(conf: DaoConfig<LeaderboardRunEntry>, category_id: string, level_id: string|null) {

        let leaderboard_id = category_id + (level_id ? '_' + level_id : '');

        let category = (await new CategoryDao(conf.db).load(category_id))[0]!;

        let subcategory_vars = _.map(
            _.filter(<Variable[]>category.variables, 'is-subcategory'),
            'id'
        );

        let chart: Chart = {
            item_id: leaderboard_id,
            item_type: 'leaderboards',
            chart_type: 'line',
            data: {},
            timestamp: new Date()
        };

        let filter: any = {
            'run.category.id': category_id
        }

        if(level_id)
            filter['run.level.id'] = level_id;

        let cursor = await conf.db.mongo.collection(conf.collection)
            .find(filter)
            .sort({ 'run.date': 1 });

        while(await cursor.hasNext()) {
            let lbr = await cursor.next();

            let subcategory_id;
            if(!subcategory_vars.length)
                subcategory_id = 'main';
            else
                subcategory_id = _.chain(lbr.run.values)
                    .pick(...subcategory_vars)
                    .toPairs()
                    .flatten()
                    .join('_')
                    .value();

            let point: LineChartData = {
                x: new Date(lbr.run.date),
                y: lbr.run.times.primary_t,
                obj: run_to_bulk(lbr.run)
            };

            if(!chart.data[subcategory_id])
                chart.data[subcategory_id] = [point];
            else if(point.y < (<LineChartData>chart.data[subcategory_id][chart.data[subcategory_id].length - 1]).y)
                chart.data[subcategory_id].push(point);
        }

        await new ChartDao(conf.db).save(chart);
    }

    async load(_conf: DaoConfig<LeaderboardRunEntry>, _keys: string[]): Promise<(LeaderboardRunEntry|null)[]> {
        throw new Error('cannot load runs from charts, use the chart directly');
    }

    async apply(conf: DaoConfig<LeaderboardRunEntry>, objs: LeaderboardRunEntry[]) {
        // only need to update if the db contains to faster times earlier than this date in this category

        let chart_gens = [];
        let chart_ids_genned = new Set();

        for(let lbr of objs) {

            if(!lbr.run.category || !lbr.run.category.id)
                continue;

            let leaderboard_id = lbr.run.category.id + (lbr.run.level ? '_' + lbr.run.level.id : '');

            let filter: {[key: string]: any} = {
                'run.category.id': lbr.run.category.id,
                'run.date': {$lt: lbr.run.date},
                'run.times.primary_t': {$lt: lbr.run.times.primary_t},
                'run.status.verify-date': {$exists: true}
            };

            if(lbr.run.level && lbr.run.level.id)
                filter['run.level.id'] = lbr.run.level.id;

            let count: number = await conf.db.mongo.collection(conf.collection)
                .countDocuments(filter);

            if(!count && !chart_ids_genned.has(leaderboard_id)) {
                chart_gens.push(this.make_chart(conf, lbr.run.category.id, lbr.run.level ? lbr.run.level.id : null));
                chart_ids_genned.add(leaderboard_id);
            }
        }

        await Promise.all(chart_gens);
    }

    async clear(conf: DaoConfig<LeaderboardRunEntry>, objs: LeaderboardRunEntry[]) {
        await this.apply(conf, objs);
    }

    has_changed(old_obj: LeaderboardRunEntry, new_obj: LeaderboardRunEntry): boolean {
        return old_obj.run.date !== new_obj.run.date ||
            old_obj.run.times.primary_t !== new_obj.run.times.primary_t;
    }
}
