import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.cvent.retrofit.metrics.TimedCallAdapterFactory;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

/**
 *
 */
public final class Application {
    private Application() {

    }
    /**
     *
     */
    public interface TestClient {
        @Timed(name = "timer1")
        @GET("/timer1")
        Call<String> timer1();

        @Timed(name = "timer2")
        @GET("/timer2")
        Call<String> timer2();
    }

    public static void main(String[] args) throws Exception {
        MetricRegistry metrics = new MetricRegistry();

        TestClient client = new Retrofit.Builder()
                .addCallAdapterFactory(new TimedCallAdapterFactory(metrics))
                .baseUrl("http://localhost:8080/")
                .build()
                .create(TestClient.class);
        client.timer1();
    }
}