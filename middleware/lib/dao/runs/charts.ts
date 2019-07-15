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

        let category = (await new CategoryDao(conf.db).load(category_id))[0];

        if(!category) {
            throw new Error('cannot generate chart: no category found for run!');
        }

        let subcategory_vars = _.map(
            _.filter(<Variable[]>category.variables, 'is-subcategory'),
            'id'
        );

        let chart: Chart = {
            item_id: leaderboard_id,
            parent_type: 'leaderboards',
            item_type: 'runs',
            chart_type: 'line',
            data: {},
            timestamp: new Date()
        };

        let filter: any = {
            'run.category.id': category_id,
            'run.status.verify-date': {$exists: true},
            'run.status.status': 'verified'
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

            let t = new Date(lbr.run.date).getTime() / 1000;

            if(isNaN(t) || !t)
                continue;

            let point: LineChartData = {
                x: t,
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
                'run.status.verify-date': {$exists: true},
                'run.status.status': 'verified'
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

// debug/helper function
export async function make_all_wr_charts(conf: DaoConfig<LeaderboardRunEntry>) {
    let cursor = conf.db.mongo.collection(conf.collection).find({place: 1});

    let rci = new RecordChartIndex('');

    while(await cursor.hasNext()) {
        let lbr = <LeaderboardRunEntry>await cursor.next();

        console.log('Make chart:', lbr.run.game.names.international, lbr.run.category.id, lbr.run.level ? lbr.run.level.id : null);
        try {
            await rci.make_chart(conf, lbr.run.category.id, lbr.run.level ? lbr.run.level.id : null);
        }
        catch(err) {
            // TODO: for right now just print and ignore
            console.error(err);
        }
    }
}

export async function get_player_pb_chart(conf: DaoConfig<LeaderboardRunEntry>, player_id: string, game_id: string) {

    let chart: Chart = {
        item_id: `${player_id}_${game_id}`,
        item_type: 'runs',
        chart_type: 'line',
        data: {},
        timestamp: new Date()
    };

    let filter: any = {
        'run.players.id': player_id,
        'run.game.id': game_id
    };

    let chart_data = await conf.db.mongo.collection(conf.collection)
        .aggregate([
            {
                $match: filter,
            },
            {
                $group: {
                    _id: {category: '$run.category.id', level: '$run.level.id'},
                    data: { $push:
                        {
                            x: '$run.date',
                            y: '$run.times.primary_t',
                            obj: '$$ROOT'
                        }
                    }
                }
            }
        ]).toArray();

    chart.data = _.chain(chart_data)
        .keyBy((v) => v._id.category + (v._id.level ? '_' + v._id.level : ''))
        .mapValues(v => {
            return v.data.map((p: LineChartData) => {
                return {
                    x: p.x,
                    y: p.y,
                    obj: run_to_bulk(p.obj)
                };
            });
        })
        .value();

    return chart;
}
