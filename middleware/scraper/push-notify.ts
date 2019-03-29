import * as _ from 'lodash';

import * as fb_admin from 'firebase-admin';

import { load_config } from '../lib/config';
import { Game, Category, Level, User } from '../lib/speedrun-api';
import { NewRecord } from '../lib/speedrun-db';

const config = load_config();

if(config.scraper.push_notify.enabled) {
    fb_admin.initializeApp({
        credential: require(config.scraper.push_notify.credentialFile)
    });
}

interface RecordNotificationData {
    new_run: string
    old_run: string
    game: string
    category: string
    level?: string
    [key: string]: string|undefined
}

function build_record_notification_data(record: NewRecord, game: Game, category: Category, level?: Level): RecordNotificationData {
    let ret: RecordNotificationData =  {
        new_run: JSON.stringify(record.new_run),
        old_run: JSON.stringify(record.old_run),
        game: JSON.stringify(_.omit(game)),
        category: JSON.stringify(category)
    };

    if(level)
        ret.level = JSON.stringify(level);

    return ret;
}

export async function notify_game_record(record: NewRecord, game: Game, category: Category, level?: Level) {
    if(!config.scraper.push_notify.enabled)
        return false;

    const GAME_RECORD_TOPIC = `new_record_game_${game.id}_${category.id}${level ? '_' + level.id : ''}`;

    let run_notification_data: RecordNotificationData = build_record_notification_data(record, game, category, level);

    await fb_admin.messaging().sendToTopic(GAME_RECORD_TOPIC, {
        data: <{[key: string]: string}>run_notification_data
    });

    return true;
}

export async function notify_player_record(record: NewRecord, player: User, game: Game, category: Category, level?: Level) {
    if(!config.scraper.push_notify.enabled)
        return false;
    
    const PLAYER_RECORD_TOPIC = `new_record_player_${player.id}`;

    let run_notification_data: RecordNotificationData = build_record_notification_data(record, game, category, level);
    fb_admin.messaging().sendToTopic(PLAYER_RECORD_TOPIC, {
        data: <{[key: string]: string}>run_notification_data
    });
}