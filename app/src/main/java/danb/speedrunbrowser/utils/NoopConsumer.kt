package danb.speedrunbrowser.utils

import io.reactivex.functions.Consumer

class NoopConsumer<T> : Consumer<T> {
    @Throws(Exception::class)
    override fun accept(t: T) {}
}
