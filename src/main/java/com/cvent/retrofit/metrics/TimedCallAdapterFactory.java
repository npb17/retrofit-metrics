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
import java.lang.reflect.ParameterizedType;
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
        if (getRawType(returnType) != Call.class) {
            return null;
        }

        Timed timed = getTimedAnnotation(annotations);
        if (timed == null) {
            return null;
        }

        String name = timed.name();
        if (name.isEmpty()) {
            // Unfortunately retrofit does not provide the Method for the annotation therefore require a name
            throw new IllegalArgumentException("Timer annotation requires a non-empty name");
        }

        Type responseType = getParameterUpperBound(0, (ParameterizedType) returnType);
        return new TimedCallAdapter(responseType, metrics.timer(name));
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
     */
    private static final class TimedCallAdapter implements CallAdapter<Call<?>> {
        private final Type responseType;
        private final Timer timer;

        private TimedCallAdapter(Type responseType, Timer timer) {
            this.responseType = responseType;
            this.timer = timer;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public <R> Call<R> adapt(Call<R> call) {
            return new TimedCall<R>(call, timer);
        }
    }

    /**
     * Wraps Call request/response methods in a timer.
     * @param <R>
     */
    private static final class TimedCall<R> implements Call<R> {
        private final Timer timer;
        private final Call<R> wrappedCall;

        private TimedCall(Call<R> call, Timer timer) {
            this.wrappedCall = call;
            this.timer = timer;
        }

        @Override
        public Response<R> execute() throws IOException {
            try (Timer.Context ctx = timer.time()) {
                return wrappedCall.execute();
            }
        }

        @Override
        public void enqueue(Callback<R> callback) {
            Timer.Context ctx = timer.time();

            wrappedCall.enqueue(new Callback<R>() {
                @Override
                public void onResponse(Call<R> call, Response<R> response) {
                    ctx.stop();
                    callback.onResponse(call, response);
                }

                @Override
                public void onFailure(Call<R> call, Throwable t) {
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
        public Call<R> clone() {
            return new TimedCall<>(wrappedCall.clone(), timer);
        }

        @Override
        public Request request() {
            return wrappedCall.request();
        }
    }
}
