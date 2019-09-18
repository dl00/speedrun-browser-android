package danb.speedrunbrowser.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.TextUtils
import android.util.Log

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.InvalidObjectException
import java.net.URL

import io.reactivex.Single
import io.reactivex.SingleOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request

class ImageLoader(ctx: Context) {
    private val cacheDir: File = ctx.cacheDir
    private val client: OkHttpClient? = Util.getHTTPClient()

    init {
        cleanCache()
    }

    private fun getCacheFile(url: URL): File {
        val fname = TextUtils.join(
                "~",
                arrayOf(url.host, url.port.toString(), url.path.replace("/".toRegex(), "_"))
        )

        // save a copy to cache before we return
        return File(cacheDir, fname)
    }

    private fun loadFromCache(url: URL): FileInputStream? {
        var fi: FileInputStream? = null
        try {
            val cf = getCacheFile(url)

            fi = FileInputStream(getCacheFile(url))

            // if the tempfile was not written or closed, it may be empty. in which case return null
            if (fi.channel.size() == 0L) {
                Log.w(TAG, "Found empty cache file: $cf")
                return null
            }

            Log.v(TAG, "Had Cached Image Asset: $url")
        } catch (_e: IOException) {
        }

        return fi
    }

    @Throws(IOException::class)
    private fun downloadImage(url: URL): InputStream? {
        Log.v(TAG, "Download Image Asset: $url")

        var bais: ByteArrayInputStream? = null

        try {
            val req = Request.Builder()
                    .url(url)
                    .build()

            val res = client!!.newCall(req).execute()

            if (!res.isSuccessful) {
                // TODO properly throw this error
                //throw new IOException();
                Log.w(TAG, "failed to download image: " + res.body()!!.string())
                return null
            }


            val data = res.body()!!.bytes()

            val fo = FileOutputStream(getCacheFile(url))

            try {
                fo.write(data)
                fo.close()
            } catch (e: Exception) {
                // if something happens, try to delete the cache file. otherwise it could load invalid next time
                Log.w(TAG, "Could not save cache file for downloaded image", e)
                getCacheFile(url).delete()
            }

            bais = ByteArrayInputStream(data)
        } catch (_e: InterruptedIOException) {
        }

        return bais
    }

    fun loadImage(url: URL): Single<Bitmap> {
        return Single.create(SingleOnSubscribe<Bitmap> { emitter ->
            try {
                // try to load from cache first
                var strm: InputStream? = loadFromCache(url)

                if (strm == null) {
                    strm = downloadImage(url)
                }

                val b = BitmapFactory.decodeStream(strm) // will trigger an IOException to log the message otherwise
                strm?.close()

                if (b == null) {
                    throw InvalidObjectException("could not decode bitmap")
                }

                emitter.onSuccess(b)
            } catch (e: Exception) {
                Log.w(TAG, "Could not download image: ", e)
                emitter.onError(e)
            }
        })
                .onErrorReturn { Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun cleanCache() {
        //TODO("Not implemented")
    }

    companion object {
        val TAG: String = ImageLoader::class.java.simpleName
    }
}
