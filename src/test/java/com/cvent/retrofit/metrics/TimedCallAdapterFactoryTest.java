package com.cvent.retrofit.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import rx.Observable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A unit test for TimedCallAdapterFactory
 * 
 * @author bryan
 */
public class TimedCallAdapterFactoryTest {
    private static final String TIMER_NAME = "test.timer";
    private static final String RESPONSE_BODY = "{ \"name\": \"The body with no name\" }";
    private static final NamedObject RESPONSE_OBJECT = new NamedObject("The body with no name");

    private MockWebServer server;
    private MetricRegistry metrics;
    private Retrofit retrofit;

    @Before
    public void before() throws IOException {
        metrics = new MetricRegistry();
        server = new MockWebServer();
        server.start();

        MockResponse response = new MockResponse();
        response.setBody(RESPONSE_BODY);
        server.enqueue(response);

        retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(new TimedCallAdapterFactory(metrics))
                .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                .baseUrl(server.url("/").toString())
                .build();
    }

    @After
    public void after() throws IOException {
        server.shutdown();
    }

    @Test
    public void synchronous() throws IOException {
        TimedClient client = retrofit.create(TimedClient.class);
        Response<NamedObject> response = client.timed().execute();
        assertThat(response.code()).isEqualTo(HttpURLConnection.HTTP_OK);
        assertThat(response.body()).isEqualTo(RESPONSE_OBJECT);

        Timer timer = metrics.timer(TIMER_NAME);
        assertThat(timer).isNotNull();
        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getMeanRate()).isGreaterThan(0);
    }

    @Test
    public void asynchronous() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        TimedClient client = retrofit.create(TimedClient.class);

        client.timed().enqueue(new Callback<NamedObject>() {
            @Override
            public void onResponse(Call<NamedObject> call, Response<NamedObject> response) {
                try {
                    assertThat(response.code()).isEqualTo(HttpURLConnection.HTTP_OK);
                    assertThat(response.body()).isEqualTo(RESPONSE_OBJECT);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Call<NamedObject> call, Throwable t) {
                latch.countDown();
            }
        });
        latch.await(1L, TimeUnit.SECONDS);

        Timer timer = metrics.timer(TIMER_NAME);
        assertThat(timer).isNotNull();
        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getMeanRate()).isGreaterThan(0);
    }

    @Test
    public void rxJava() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        RxJavaClient client = retrofit.create(RxJavaClient.class);
        client.timed()
                .subscribe(response -> {
                    try {
                        assertThat(response.code()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(response.body()).isEqualTo(RESPONSE_OBJECT);
                    } finally {
                        latch.countDown();
                    }
                }, error -> {
                    throw new RuntimeException("Test failed");
                });
        latch.await(1L, TimeUnit.SECONDS);

        Timer timer = metrics.timer(TIMER_NAME);
        assertThat(timer).isNotNull();
        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getMeanRate()).isGreaterThan(0);
    }


    @Test(expected = IllegalArgumentException.class)
    public void timedMethodWithNoName() throws IOException {
        TimedClient client = retrofit.create(TimedClient.class);
        client.timedWithNoName();
    }

    @Test
    public void methodWithNoTimedAnnotation() throws IOException {
        TimedClient client = retrofit.create(TimedClient.class);
        Response<NamedObject> response = client.methodWithNoTimedAnnotation().execute();

        assertThat(response.code()).isEqualTo(HttpURLConnection.HTTP_OK);
        assertThat(response.body()).isEqualTo(RESPONSE_OBJECT);
    }

    /**
     * A test client interface
     */
    public interface TimedClient {
        @Timed(name = TIMER_NAME)
        @GET(".")
        Call<NamedObject> timed();

        @Timed
        @GET(".")
        Call<NamedObject> timedWithNoName();

        @GET(".")
        Call<NamedObject> methodWithNoTimedAnnotation();
    }

    /**
     * A test client interface
     */
    public interface RxJavaClient {
        @Timed(name = TIMER_NAME)
        @GET(".")
        Observable<Response<NamedObject>> timed();
    }

    /**
     * A test data class
     */
    public static class NamedObject {
        private final String name;

        public NamedObject(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 79 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return ((NamedObject) obj).name.equals(name);
        }
    }
}
