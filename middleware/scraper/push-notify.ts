import * as _ from 'lodash';

import * as fb_admin from 'firebase-admin';

import { load_config } from '../lib/config';
import * as speedrun_api from '../lib/speedrun-api';
import { NewRecord } from '../lib/speedrun-db';

const config = load_config();

if(config.scraper.pushNotify.enabled) {
    fb_admin.initializeApp({
        credential: fb_admin.credential.cert(require(config.scraper.pushNotify.credentialFile))
    });
}

interface RecordNotificationData {
    new_run: string
    old_run?: string
    game: string
    category: string
    level?: string
    [key: string]: string|undefined
}

function build_record_notification_data(record: NewRecord, game: speedrun_api.Game, category: speedrun_api.Category, level?: speedrun_api.Level): RecordNotificationData {

    record.new_run.run = speedrun_api.run_to_bulk(<speedrun_api.Run>record.new_run.run);

    let ret: RecordNotificationData = {
        new_run: JSON.stringify(record.new_run),
        game: JSON.stringify(speedrun_api.game_to_bulk(game)),
        category: JSON.stringify(speedrun_api.category_to_bulk(category))
    };

    if(record.old_run) {
        record.old_run.run = speedrun_api.run_to_bulk(<speedrun_api.Run>record.old_run.run);
        ret.old_run = JSON.stringify(record.old_run);
    }

    if(level)
        ret.level = JSON.stringify(speedrun_api.level_to_bulk(level));

    return ret;
}

export async function notify_game_record(record: NewRecord, game: speedrun_api.Game, category: speedrun_api.Category, level?: speedrun_api.Level) {
    if(!config.scraper.pushNotify.enabled)
        return false;

    const GAME_RECORD_TOPIC = `${config.stackName}_game_${game.id}_${category.id}${level ? '_' + level.id : ''}`;

    let run_notification_data: RecordNotificationData = build_record_notification_data(record, game, category, level);
    console.log('[NOTIF]', GAME_RECORD_TOPIC);

    try {
        await fb_admin.messaging().sendToTopic(GAME_RECORD_TOPIC, {
            data: <{[key: string]: string}>run_notification_data
        });
    } catch(e) {
        console.error('[ERROR] Failed to send push notification:', e);
    }

    return true;
}

export async function notify_player_record(record: NewRecord, player: speedrun_api.User, game: speedrun_api.Game, category: speedrun_api.Category, level?: speedrun_api.Level) {
    if(!config.scraper.pushNotify.enabled)
        return false;
    
    const PLAYER_RECORD_TOPIC = `${config.stackName}_player_${player.id}`;

    let run_notification_data: RecordNotificationData = build_record_notification_data(record, game, category, level);
    console.log('[NOTIF]', PLAYER_RECORD_TOPIC);

    try {
        let res = await fb_admin.messaging().sendToTopic(PLAYER_RECORD_TOPIC, {
            data: <{[key: string]: string}>run_notification_data
        });

        console.log(res.messageId);
    }
    catch(e) {
        console.error('[ERROR] Failed to send push notification:', e, run_notification_data);
    }
}