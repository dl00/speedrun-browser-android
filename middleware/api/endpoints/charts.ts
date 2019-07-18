import * as _ from 'lodash';

import { Router } from 'express';

import * as api from '../';
import * as api_response from '../response';

import { ChartDao, Chart, LineChartData } from '../../lib/dao/charts';
import { LeaderboardDao, make_distribution_chart } from '../../lib/dao/leaderboards';
import { GameDao } from '../../lib/dao/games';
import { CategoryDao } from '../../lib/dao/categories';
import { LevelDao } from '../../lib/dao/levels';
import { UserDao } from '../../lib/dao/users';
import { RunDao, Run } from '../../lib/dao/runs';
import { Variable } from '../../lib/speedrun-api';

const router = Router();

export function get_wr_chart_longest_holders(wr_chart: Chart): Chart {
    let chart: Chart = {
        item_id: wr_chart.item_id + '_holders',
        item_type: 'users',
        chart_type: 'list',
        data: {},
        timestamp: new Date()
    };

    for(let subcategory in wr_chart.data) {

        let holders: {[player_id: string]: any} = {};

        for(let i = 0;i < wr_chart.data[subcategory].length - 1;i++) {
            let run = <Run>(<LineChartData>wr_chart.data[subcategory][i + 1]).obj;

            let dt = run.times.primary_t -
                (<LineChartData>wr_chart.data[subcategory][i]).obj.times.primary_t;

            if(holders[run.players[0].id])
                holders[run.players[0].id].score += dt;
            else
                holders[run.players[0].id] = {
                    score: dt,
                    player: run.players[0]
                };
        }

        chart.data[subcategory] = _.reverse(_.sortBy(_.values(holders), 'score'));
    }

    return chart;
}

router.get('/site', async (_req, res) => {

    let run_dao = new RunDao(api.storedb!);

    // total run count over time
    let count_over_time_chart = await run_dao.get_historical_run_count();

    // submitted run volume
    let volume_chart = await run_dao.get_site_submission_volume();

    // metric: hours recorded in PBs by speedrunners

    // metric: game with the most speedruns this month

    api_response.complete_single(res, {
        charts: {
            count_over_time: count_over_time_chart,
            volume: volume_chart
        }
    });
});

router.get('/games/:id', async (req, res) => {

    let game_id = req.params.id;

    let game_dao = new GameDao(api.storedb!);
    let run_dao = new RunDao(api.storedb!);

    let game = (await game_dao.load(game_id))[0];

    if(!game)
        return api_response.error(res, api_response.err.NOT_FOUND());

    /// run submission volume over the last 12 months
    let volume_chart = await run_dao.get_game_submission_volume(game_id);

    /// all time longest world record holders overall


    /// fastest improving runners, runners who have come out of nowhere/improved/relevant recently


    api_response.complete_single(res, {
        game: game,
        charts: {
            volume: volume_chart
        }
    });
});

router.get('/leaderboards/:id', async(req, res) => {

    let leaderboard_id = req.params.id;

    let category_id = leaderboard_id.split('_')[0];
    let level_id = leaderboard_id.split('_')[1];

    let chart_dao = new ChartDao(api.storedb!);
    let leaderboard_dao = new LeaderboardDao(api.storedb!);
    let game_dao = new GameDao(api.storedb!);
    let category_dao = new CategoryDao(api.storedb!);
    let level_dao = new LevelDao(api.storedb!);
    let run_dao = new RunDao(api.storedb!);

    let leaderboard = (await leaderboard_dao.load(leaderboard_id))[0]!;
    let category = (await category_dao.load(category_id))[0]!;

    if(!category)
        return api_response.error(res, api_response.err.NOT_FOUND());

    let game = (await game_dao.load(category.game!))[0]!;

    let level = null;
    if(level_id)
        level = (await level_dao.load(level_id))[0];

    if(!game)
        return api_response.error(res, api_response.err.NOT_FOUND());

    // word records chartify
    let wr_chart = (await chart_dao.load(`leaderboards_${leaderboard_id}`))[0];

    // show the run time distributions
    let distrib_chart = make_distribution_chart(leaderboard, <Variable[]>category.variables);

    // run submission volume over the last 12 months
    let volume_chart = await run_dao.get_leaderboard_submission_volume(category_id, level_id);

    // all time longest world record holders for each category (just a repackaging of wr_chart)

    // fastest improving runners, runners who have come out of nowhere/improved/relevant recently (repackaging of player pb chart)

    api_response.complete_single(res, {
        game: game,
        category: category,
        level: level,
        charts: {
            wrs: wr_chart,
            distribution: distrib_chart,
            volume: volume_chart
        }
    });
});

router.get('/users/:id', async (req, res) => {
    let player_id = req.params.id;

    let user_dao = new UserDao(api.storedb!);
    let run_dao = new RunDao(api.storedb!);

    let player = (await user_dao.load(player_id))[0]!;

    /// calculate "favorite" games by count of runs
    let favorite_games_chart = await run_dao.get_player_favorite_runs(player_id);
    let volume_chart = await run_dao.get_player_submission_volume(player_id);

    api_response.complete_single(res, {
        player: player,
        charts: {
            favorite_games: favorite_games_chart,
            volume: volume_chart
        }
    });
});

router.get('/games/:game_id/players/:player_id', async (req, res) => {
    let game_id = req.params.game_id;
    let player_id = req.params.player_id;

    let game_dao = new GameDao(api.storedb!);
    let run_dao = new RunDao(api.storedb!);

    let game = (await game_dao.load(game_id))[0]!;

    /// personal bests chartify
    let pb_chart = await run_dao.get_player_pb_chart(player_id, game_id);

    api_response.complete_single(res, {
        game: game,
        charts: {
            pbs: pb_chart
        }
    });
})

module.exports = router;
