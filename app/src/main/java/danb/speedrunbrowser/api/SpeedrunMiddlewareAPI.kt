package danb.speedrunbrowser.api

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

import java.util.Objects

import danb.speedrunbrowser.BuildConfig
import danb.speedrunbrowser.api.objects.*
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
    const val MIN_AUTOCOMPLETE_LENGTH = 3

    private val baseUrl: String
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
            gson.registerTypeAdapter(Chart::class.java, Chart.JsonConverter())
            gson.registerTypeAdapter(GameMaker::class.java, GameMaker.JsonConverter())

            //gson.registerTypeAdapter(List::class.java, NestedListDeserializer())

            return gson.create()
        }

    fun make(context: Context): Endpoints {
        val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(Objects.requireNonNull<OkHttpClient>(Util.getHTTPClient(context)))
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
        val players: List<User> = listOf(),
        val game_groups: List<GameGroup> = listOf()
    )

    data class APISearchResponse(
        var search: APISearchData? = APISearchData(),
        var error: Error? = null
    )

    data class APIChartData(
        val category: Category?,
        val level: Level?,
        val game: Game?,
        val player: User?,
        val metrics: Map<String, Metric>,
        val charts: Map<String, Chart>
    )

    data class APIChartResponse(
        var data: APIChartData,
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
        fun listGames(@Query("mode") mode: String, @Query("start") offset: Int): Observable<APIResponse<Game>>

        @GET("games/genre/{id}")
        fun listGamesByGenre(@Path("id") genreId: String, @Query("mode") mode: String, @Query("start") offset: Int): Observable<APIResponse<Game>>

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
        fun listLatestRuns(@Query("start") offset: Int,
                           @Query("verified") verified: Boolean): Observable<APIResponse<LeaderboardRunEntry>>

        @GET("runs/latest")
        fun listLatestRunsByGenre(
                @Query("id") genreId: String,
                @Query("start") offset: Int,
                @Query("verified") verified: Boolean): Observable<APIResponse<LeaderboardRunEntry>>

        @GET("runs/{ids}")
        fun listRuns(@Path("ids") runIds: String): Observable<APIResponse<LeaderboardRunEntry>>

        // What is
        @GET("whatis/{ids}")
        fun whatAreThese(@Path("ids") thingIds: String): Observable<APIResponse<WhatIsEntry>>

        // Charts
        @GET("charts/game-groups/{id}")
        fun getGameGroupMetrics(@Path("id") ggId: String): Observable<APIChartResponse>

        @GET("charts/games/{id}")
        fun getGameMetrics(@Path("id") gameId: String): Observable<APIChartResponse>

        @GET("charts/leaderboards/{id}")
        fun getLeaderboardMetrics(@Path("id") gameId: String): Observable<APIChartResponse>

        @GET("charts/users/{id}")
        fun getUserMetrics(@Path("id") gameId: String): Observable<APIChartResponse>

        @GET("charts/games/:gameId/players/{playerId}")
        fun getUserGameMetrics(@Path("gameId") gameId: String,
                               @Path("playerId") playerId: String): Observable<APIChartResponse>

        @GET("streams/game-groups/{ggId}")
        fun listStreamsByGameGroup(@Path("ggId") ggId: String): Observable<APIResponse<Stream>>

        @GET("streams/games/{gameId}")
        fun listStreamsByGame(@Path("gameId") gameId: String): Observable<APIResponse<Stream>>
    }
}
