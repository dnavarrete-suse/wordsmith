import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.NoSuchElementException;

//Prometheus imports -- monitoring-demo
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.HTTPServer;

public class Main {


    //Prometheus metric builder -- monitoring-demo
    static final Counter db_request_counter = Counter.build().name("db_requests_total").help("Calls to DB counter").register();
    static final Counter db_failure_counter = Counter.build().name("db_requests_failed_total").help("Failed calls to DB counter").register();


    public static void main(String[] args) throws Exception {
	
        //Prometheus server metric exporter -- monitoring-demo
	HTTPServer promserver = new HTTPServer(8734);        

	    
        Class.forName("org.postgresql.Driver");
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/noun", handler(() -> randomWord("nouns")));
        server.createContext("/verb", handler(() -> randomWord("verbs")));
        server.createContext("/adjective", handler(() -> randomWord("adjectives")));
        server.start();
    }

    private static String randomWord(String table) {

        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://db:5432/postgres", "postgres", "")) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet set = statement.executeQuery("SELECT word FROM " + table + " ORDER BY random() LIMIT 1")) {

                    //Prometheus counter increment when the connection to the db is made-- monitoring-demo
                    db_request_counter.inc();

                    while (set.next()) {
                        return set.getString(1);
                    }
                }
            }
        } catch (SQLException e) {
            //Prometheus counter increment when the request to the db fails -- monitoring-demo
            db_failure_counter.inc();

            e.printStackTrace();
        }

        throw new NoSuchElementException(table);
    }

    private static HttpHandler handler(Supplier<String> word) {
        return t -> {
            String response = "{\"word\":\"" + word.get() + "\"}";
            byte[] bytes = response.getBytes(Charsets.UTF_8);

            System.out.println(response);
            
            t.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
            t.getResponseHeaders().add("cache-control", "private, no-cache, no-store, must-revalidate, max-age=0");
            t.getResponseHeaders().add("pragma", "no-cache");

            t.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = t.getResponseBody()) {
                os.write(bytes);
            }
        };
    }
}
