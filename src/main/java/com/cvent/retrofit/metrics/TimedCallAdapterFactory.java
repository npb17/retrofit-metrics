package com.cvent.retrofit.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * A Retrofit call adapter which produces timer metrics on client methods annotated with
 * {@link com.codahale.metrics.annotation.Timed}. The {@link com.codahale.metrics.annotation.Timed} annotation requires
 * a value for the name field.
 */
public class TimedCallAdapterFactory extends CallAdapter.Factory {

    private final MetricRegistry metrics;

    public TimedCallAdapterFactory(MetricRegistry metrics) {
        Objects.requireNonNull(metrics);
        this.metrics = metrics;
    }

    @Override
    public CallAdapter<?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        Timed timed = getTimedAnnotation(annotations);
        if (timed == null) {
            return null;
        }

        String name = timed.name();
        if (name.isEmpty()) {
            // Unfortunately retrofit does not provide the Method for the annotation therefore require a name
            throw new IllegalArgumentException("Timer annotation requires a non-empty name");
        }

        CallAdapter<?> nextCallAdapter = retrofit.nextCallAdapter(this, returnType, annotations);
        return new TimedCallAdapter(metrics.timer(name), nextCallAdapter);
    }

    private Timed getTimedAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Timed) {
                return (Timed) annotation;
            }
        }
        return null;
    }

    /**
     * Creates wrapped TimedCall objects.
     * @param <T>
     */
    private static final class TimedCallAdapter<T> implements CallAdapter<T> {
        private final Timer timer;
        private final CallAdapter<?> nextCallAdapter;

        private TimedCallAdapter(Timer timer, CallAdapter<?> nextCallAdapter) {
            this.timer = timer;
            this.nextCallAdapter = nextCallAdapter;
        }

        @Override
        public Type responseType() {
            return nextCallAdapter.responseType();
        }

        @Override
        public <R> T adapt(Call<R> call) {
            return (T) nextCallAdapter.adapt(new TimedCall(call, timer));
        }
    }

    /**
     * Wraps Call request/response methods in a timer.
     * @param <T>
     */
    private static final class TimedCall<T> implements Call<T> {
        private final Timer timer;
        private final Call<T> wrappedCall;

        private TimedCall(Call<T> call, Timer timer) {
            this.wrappedCall = call;
            this.timer = timer;
        }

        @Override
        public Response<T> execute() throws IOException {
            try (Timer.Context ctx = timer.time()) {
                return wrappedCall.execute();
            }
        }

        @Override
        public void enqueue(Callback callback) {
            Timer.Context ctx = timer.time();

            wrappedCall.enqueue(new Callback<T>() {
                @Override
                public void onResponse(Call<T> call, Response<T> response) {
                    ctx.stop();
                    callback.onResponse(call, response);
                }

                @Override
                public void onFailure(Call<T> call, Throwable t) {
                    ctx.stop();
                    callback.onFailure(call, t);
                }
            });
        }

        @Override
        public boolean isExecuted() {
            return wrappedCall.isExecuted();
        }

        @Override
        public void cancel() {
            wrappedCall.cancel();
        }

        @Override
        public boolean isCanceled() {
            return wrappedCall.isCanceled();
        }

        @Override
        public Call<T> clone() {
            return new TimedCall<T>(wrappedCall.clone(), timer);
        }

        @Override
        public Request request() {
            return wrappedCall.request();
        }
    }
}
