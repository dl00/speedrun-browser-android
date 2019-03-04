package danb.speedrunbrowser.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.Observable;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class SpeedrunAPI {

    public static Gson getGson() {
        GsonBuilder gson = new GsonBuilder();

        gson.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES);

        // type adapters go here

        return gson.create();
    }

    public static Endpoints make() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.speedrun.com/api/v1/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(getGson()))
                .client(Util.getHTTPClient())
                .build();

        return retrofit.create(Endpoints.class);
    }

    public static class APIResponse<T> {
        public T data;
    }

    public interface Endpoints {

        // Games
        @GET("games?_bulk=yes&max=1000")
        Observable<APIResponse<List<Game>>> bulkListGames(@Query("offset") int offset);

        @GET("games")
        Observable<APIResponse<List<Game>>> listGames(@Query("name") String name);

        @GET("games/{id}?embed=categories.variables,levels.variables")
        Observable<APIResponse<Game>> getGame(@Path("id") String gameId);

        // Leaderboards
        @GET("leaderboards/{game}/category/{category}")
        Observable<APIResponse<Leaderboard>> getLeaderboard(@Path("game") String gameId, @Path("category") String categoryId);
    }
}
