// Does hacky things with DB that need to happen periodically
// TODO: this should be replaced with a real scheduler soon

import { ChartDao } from '../../lib/dao/charts';
import { RunDao } from '../../lib/dao/runs';

import * as scraper from '../index';

export async function gen_site_total_runs(_runid: string, _options: any) {
    try {
        const chart = await new RunDao(scraper.storedb!).get_historical_run_count({});
        await new ChartDao(scraper.storedb!).save(chart);
    } catch (err) {
        console.error('loader/hack: could not generate site total runs chart:', err.statusCode || err);
        throw new Error('permanent');
    }
}

export async function gen_site_volume(_runid: string, _options: any) {
    try {
        const chart = await new RunDao(scraper.storedb!).get_site_submission_volume();
        await new ChartDao(scraper.storedb!).save(chart);
    } catch (err) {
        console.error('loader/hack: could not generate site volume chart:', err.statusCode || err);
        throw new Error('permanent');
    }
}
