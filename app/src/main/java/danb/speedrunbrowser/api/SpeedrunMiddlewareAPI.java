package danb.speedrunbrowser.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.GameAssets;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Platform;
import danb.speedrunbrowser.api.objects.Region;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.Observable;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class SpeedrunMiddlewareAPI {
    public static final int MIN_AUTOCOMPLETE_LENGTH = 3;

    public static Gson getGson() {
        GsonBuilder gson = new GsonBuilder();

        gson.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES);

        gson.registerTypeAdapter(GameAssets.class, new GameAssets.JsonConverter());
        gson.registerTypeAdapter(MediaLink.class, new MediaLink.JsonConverter());
        gson.registerTypeAdapter(Platform.class, new Platform.JsonConverter());
        gson.registerTypeAdapter(Region.class, new Region.JsonConverter());
        gson.registerTypeAdapter(Variable.class, new Variable.JsonConverter());

        gson.registerTypeAdapter(List.class, new NestedListDeserializer());

        // type adapters go here

        return gson.create();
    }

    public static Endpoints make() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://sr-browser.dbeal.dev/api/v1/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(getGson()))
                .client(Util.getHTTPClient())
                .build();

        return retrofit.create(Endpoints.class);
    }

    public static class Error {
        public String msg;
    }

    public static class APIResponse<T> {
        public List<T> data;
        public Error error;
    }

    public static class APISearchData {
        public List<Game> games;
    }

    public static class APISearchResponse {
        public APISearchData search;
        public Error error;
    }

    public interface Endpoints {

        // Autocomplete
        @GET("autocomplete")
        Observable<APISearchResponse> autocomplete(@Query("q") String query);

        // Games
        @GET("games")
        Observable<APIResponse<Game>> listGames(@Query("start") int offset);

        @GET("games/{ids}")
        Observable<APIResponse<Game>> listGames(@Path("ids") String ids);

        // Leaderboards
        @GET("leaderboards/{leaderboardId}")
        Observable<APIResponse<Leaderboard>> listLeaderboards(@Path("leaderboardId") String categoryId);
    }
}
