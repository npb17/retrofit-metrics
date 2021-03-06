Retrofit Metrics
================

Adds support for http://metrics.dropwizard.io `Timed` annotations to retrofit2 clients.

### Download from Maven

```xml
<dependency>
  <groupId>com.cvent</groupId>
  <artifactId>retrofit-metrics</artifactId>
  <version>(insert latest version)</version>
</dependency>
```

### Example

```java
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.Timed;
import com.cvent.retrofit.metrics.TimedCallAdapterFactory;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

public class Application {
    public interface TestClient {
        @Timed(name = "endpoint1")
        @GET("/endpoint1")
        Call<String> endpoint1();

        @Timed(name = "endpoint2")
        @GET("/endpoint2")
        Call<String> endpoint2();
    }

    public static void main(String[] args) throws Exception {
        MetricRegistry metrics = new MetricRegistry();

        TestClient client = new Retrofit.Builder()
                .addCallAdapterFactory(new TimedCallAdapterFactory(metrics))
                .baseUrl("http://localhost:8080/")
                .build()
                .create(TestClient.class);
        client.endpoint1();
    }
}
```

### Usage with RxJava

You must add `TimedCallAdapterFactory` before adding the `RxJavaCallAdapterFactory` to the `Retrofit.Builder`.
 
```java
TestClient client = new Retrofit.Builder()
    .addCallAdapterFactory(new TimedCallAdapterFactory(metrics))
    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
    .baseUrl("http://localhost:8080/")
    .build()
    .create(TestClient.class);
```

### See Also

* http://square.github.io/retrofit/
* http://metrics.dropwizard.io/

