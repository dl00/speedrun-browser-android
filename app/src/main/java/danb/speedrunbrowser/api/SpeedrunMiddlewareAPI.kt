package danb.speedrunbrowser.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

import java.io.Serializable
import java.util.ArrayList
import java.util.Objects

import danb.speedrunbrowser.BuildConfig
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.GameAssets
import danb.speedrunbrowser.api.objects.Genre
import danb.speedrunbrowser.api.objects.Leaderboard
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.MediaLink
import danb.speedrunbrowser.api.objects.Platform
import danb.speedrunbrowser.api.objects.Region
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.utils.Util
import io.reactivex.Observable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

object SpeedrunMiddlewareAPI {
    val MIN_AUTOCOMPLETE_LENGTH = 3

    val baseUrl: String
        get() = if (BuildConfig.DEBUG) {
            "https://sr-browser-develop.dbeal.dev/api/v1/"
        } else {
            "https://sr-browser.dbeal.dev/api/v1/"
        }

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

            return gson.create()
        }

    fun make(): Endpoints {
        val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(Objects.requireNonNull<OkHttpClient>(Util.getHTTPClient()))
                .build()

        return retrofit.create(Endpoints::class.java)
    }

    class Error {
        var msg: String? = null
    }

    data class APIResponse<T>(
        val data: List<T?>? = listOf(),
        val error: Error? = null,

        val more: MoreInfo? = null
    )

    data class MoreInfo(
        val code: String? = null,
        val count: Int = 0
    ) {

        val hasMore
        get() = code == "0"
    }

    data class APISearchData(
        val games: List<Game> = listOf(),
        val players: List<User> = listOf()
    )

    data class APISearchResponse(
        var search: APISearchData? = APISearchData(),
        var error: Error? = null
    )

    interface Endpoints {

        // Autocomplete
        @GET("autocomplete")
        fun autocomplete(@Query("q") query: String): Observable<APISearchResponse>

        // Genres
        @GET("genres")
        fun listGenres(@Query("q") query: String): Observable<APIResponse<Genre>>

        // Games
        @GET("games")
        fun listGames(@Query("start") offset: Int): Observable<APIResponse<Game>>

        @GET("games/genre/{id}")
        fun listGamesByGenre(@Path("id") genreId: String, @Query("start") offset: Int): Observable<APIResponse<Game>>

        @GET("games/{ids}")
        fun listGames(@Path("ids") ids: String): Observable<APIResponse<Game>>

        // Players
        @GET("users/{ids}")
        fun listPlayers(@Path("ids") ids: String): Observable<APIResponse<User>>

        // Leaderboards
        @GET("leaderboards/{leaderboardId}")
        fun listLeaderboards(@Path("leaderboardId") categoryId: String): Observable<APIResponse<Leaderboard>>

        // Runs
        @GET("runs/latest")
        fun listLatestRuns(@Query("start") offset: Int): Observable<APIResponse<LeaderboardRunEntry>>

        @GET("runs/latest/genre/{id}")
        fun listLatestRunsByGenre(@Path("id") genreId: String, @Query("start") offset: Int): Observable<APIResponse<LeaderboardRunEntry>>

        @GET("runs/{ids}")
        fun listRuns(@Path("ids") runIds: String): Observable<APIResponse<LeaderboardRunEntry>>

        // What is
        @GET("whatis/{ids}")
        fun whatAreThese(@Path("ids") thingIds: String): Observable<APIResponse<WhatIsEntry>>
    }
}
