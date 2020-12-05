package danb.speedrunbrowser.api

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import danb.speedrunbrowser.api.objects.*
import danb.speedrunbrowser.utils.Util
import io.reactivex.Observable
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*


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

    fun make(context: Context): Endpoints {

        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()


        val retrofit = Retrofit.Builder()
                .baseUrl("https://www.speedrun.com/api/v1/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build()

        return retrofit.create(Endpoints::class.java)
    }

    class APIResponse<T> {
        var data: T? = null
    }

    data class APIRunStatus(
            val status: RunStatus
    )

    interface Endpoints {

        // Profile
        @GET("profile")
        fun getProfile(@Header("X-API-Key") apiKey: String): Observable<APIResponse<User>>

        @PUT("runs/{id}/status")
        fun setRunStatus(@Header("X-API-Key") apiKey: String, @Path("id") runId: String, @Body result: APIRunStatus): Observable<APIResponse<Unit>>
    }
}
