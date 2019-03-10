package danb.speedrunbrowser.utils;

import io.reactivex.functions.Consumer;

public class NoopConsumer<T> implements Consumer<T> {
    @Override
    public void accept(T t) throws Exception {}
}
