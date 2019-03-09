package danb.speedrunbrowser.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.GameAssets;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Platform;
import danb.speedrunbrowser.api.objects.Region;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.api.objects.Variable;
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

        gson.registerTypeAdapter(GameAssets.class, new GameAssets.JsonConverter());
        gson.registerTypeAdapter(MediaLink.class, new MediaLink.JsonConverter());
        gson.registerTypeAdapter(Platform.class, new Platform.JsonConverter());
        gson.registerTypeAdapter(Region.class, new Region.JsonConverter());
        gson.registerTypeAdapter(Run.class, new Run.JsonConverter());
        gson.registerTypeAdapter(Variable.class, new Variable.JsonConverter());

        gson.registerTypeAdapter(List.class, new NestedListDeserializer());

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
        // Runs
        @GET("runs/{id}?embed=game,game.platforms,category,level,players")
        Observable<APIResponse<Run>> getRun(@Path("id") String runId);
    }
}
