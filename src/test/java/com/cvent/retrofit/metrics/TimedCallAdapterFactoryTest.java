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
import retrofit2.http.GET;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class TimedCallAdapterFactoryTest {
    private static final String TIMER_NAME = "test.timer";

    private MockWebServer server;
    private MetricRegistry metrics;
    private TimedClient client;

    @Before
    public void before() throws IOException {
        metrics = new MetricRegistry();
        server = new MockWebServer();
        server.start();

        MockResponse response = new MockResponse();
        response.setBody("{}");
        server.enqueue(response);

        client = new Retrofit.Builder()
                .addCallAdapterFactory(new TimedCallAdapterFactory(metrics))
                .baseUrl(server.url("/").toString())
                .build()
                .create(TimedClient.class);
    }

    @After
    public void after() throws IOException {
        server.shutdown();
    }

    @Test
    public void synchronous() throws IOException {
        Response<Void> response = client.timed().execute();
        assertThat(response.code()).isEqualTo(HttpURLConnection.HTTP_OK);

        Timer timer = metrics.timer(TIMER_NAME);
        assertThat(timer).isNotNull();
        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getMeanRate()).isGreaterThan(0);
    }

    @Test
    public void asynchronous() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        client.timed().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                try {
                    assertEquals(HttpURLConnection.HTTP_OK, response.code());
                }
                finally {
                    latch.countDown();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                latch.countDown();
            }
        });
        latch.await();

        Timer timer = metrics.timer(TIMER_NAME);
        assertThat(timer).isNotNull();
        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getMeanRate()).isGreaterThan(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTimedMethodWithNoName() throws IOException {
        client.timedWithNoName();
    }

    public interface TimedClient {
        @Timed(name = TIMER_NAME)
        @GET(".")
        Call<Void> timed();

        @Timed
        @GET(".")
        Call<Void> timedWithNoName();
    }
}
