// Does hacky things with DB that need to happen periodically
// TODO: this should be replaced with a real scheduler soon

import { ChartDao, Chart } from '../../lib/dao/charts';
import { RunDao } from '../../lib/dao/runs';
import { GameGroupDao, GameGroup } from '../../lib/dao/game-groups';

import * as scraper from '../index';

export async function gen_total_runs(_runid: string, _options: any) {
    let run_dao = await new RunDao(scraper.storedb!);
    let chart_dao = new ChartDao(scraper.storedb!);

    try {
        console.log('[CHART] total site');
        const chart: Chart = {
            item_id: 'site',
            parent_type: 'game-groups',
            item_type: 'runs',
            aggr: 'count',
            chart_type: 'bar',
            timestamp: new Date(),
            data: { 'main': await run_dao.get_historical_run_count({}) }
        };

        await chart_dao.save(chart);
    } catch (err) {
        console.error('loader/hack: could not generate site total runs chart:', err.statusCode || err);
        throw new Error('permanent');
    }

    try {
        await new GameGroupDao(scraper.storedb!).scan({batchSize: 100}, async (ggs: GameGroup[]) => {
            for(let gg of ggs) {
                console.log('[CHART] total', gg.id);
                const chart: Chart = {
                    item_id: gg.id,
                    parent_type: 'game-groups',
                    item_type: 'runs',
                    aggr: 'count',
                    chart_type: 'bar',
                    timestamp: new Date(),
                    data: { 'main': await run_dao.get_historical_run_count({
                        gameGroups: gg.id
                    }) }
                };

                await chart_dao.save(chart);
            }
        });
    } catch (err) {
        console.error('loader/hack: could not generate total runs chart for a game group:', err.statusCode || err);
        throw new Error('permanent');
    }

    console.log('[CHART] total Done')
}

export async function gen_volume(_runid: string, _options: any) {

    let run_dao = await new RunDao(scraper.storedb!);
    let chart_dao = new ChartDao(scraper.storedb!);

    try {
        console.log('[CHART] volume site');

        const chart = await run_dao.get_group_submission_volume();
        await chart_dao.save(chart);
    } catch (err) {
        console.error('loader/hack: could not generate site volume chart:', err.statusCode || err);
        throw new Error('permanent');
    }

    try {
        await new GameGroupDao(scraper.storedb!).scan({batchSize: 100}, async (ggs: GameGroup[]) => {
            for(let gg of ggs) {
                console.log('[CHART] volume', gg.id);

                const chart = await run_dao.get_group_submission_volume(gg.id);
                await chart_dao.save(chart);
            }
        });
    } catch (err) {
        console.error('loader/hack: could not generate volume chart for game group:', err.statusCode || err);
        throw new Error('permanent');
    }

    console.log('[CHART] volume Done');
}
