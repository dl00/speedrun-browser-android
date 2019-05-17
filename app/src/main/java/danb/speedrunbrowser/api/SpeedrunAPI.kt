package danb.speedrunbrowser.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

import danb.speedrunbrowser.api.objects.GameAssets
import danb.speedrunbrowser.api.objects.Genre
import danb.speedrunbrowser.api.objects.MediaLink
import danb.speedrunbrowser.api.objects.Platform
import danb.speedrunbrowser.api.objects.Region
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.utils.Util
import io.reactivex.Observable
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

object SpeedrunAPI {

    // type adapters go here
    val gson: Gson
        get() {
            val gson = GsonBuilder()

            gson.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)

            gson.registerTypeAdapter(GameAssets::class.java, GameAssets.JsonConverter())
            gson.registerTypeAdapter(MediaLink::class.java, MediaLink.JsonConverter())
            gson.registerTypeAdapter(Platform::class.java, Platform.JsonConverter())
            gson.registerTypeAdapter(Region::class.java, Region.JsonConverter())
            gson.registerTypeAdapter(Genre::class.java, Genre.JsonConverter())
            gson.registerTypeAdapter(Run::class.java, Run.JsonConverter())
            gson.registerTypeAdapter(Variable::class.java, Variable.JsonConverter())

            gson.registerTypeAdapter(List::class.java, NestedListDeserializer())

            return gson.create()
        }

    fun make(): Endpoints {
        val retrofit = Retrofit.Builder()
                .baseUrl("https://www.speedrun.com/api/v1/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(Util.getHTTPClient()!!)
                .build()

        return retrofit.create(Endpoints::class.java)
    }

    class APIResponse<T> {
        var data: T? = null
    }

    interface Endpoints {
        // Runs
        @GET("runs/{id}?embed=game,game.platforms,category,level,players")
        fun getRun(@Path("id") runId: String): Observable<APIResponse<Run>>
    }
}
