import * as request from 'request-promise-native';

export default request.defaults({
    headers: {
        'User-Agent': 'SpeedrunAndroidMiddlewareScrapebot (report@danb.email)',
    },

    // sets the content type to json, and the accepts header. Auto parsing!
    json: true,
});
