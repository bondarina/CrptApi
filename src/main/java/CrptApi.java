import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final int REQUEST_LIMIT;
    private final long TIME_UNIT;
    private static ScheduledExecutorService executor;
    private AtomicInteger requestCount = new AtomicInteger(0);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/commissioning/contract/create?pg=milk";

    public CrptApi(int requestLimit, long timeUnit) {
        this.TIME_UNIT = timeUnit;
        this.REQUEST_LIMIT = requestLimit;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.executor.scheduleAtFixedRate(this::resetRequestsCount, 0, TIME_UNIT, TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) throws IOException, URISyntaxException {
        String json = readFile("jsonFile");
        Root rootJson = mapper.readValue(json, Root.class);


        CrptApi crpt = new CrptApi(5, 5000);
        crpt.allowRequests();
        crpt.createRfCommission(rootJson, "{\"Id\":1,\"Password\":\"abc.(URL ismp.crpt.ru и markirovka.demo " +
                "не отвечают, авторизоваться не получается, потому программа отвечает кодом 401)\"}");
        crpt.releaseRequest();
        crpt.shutdown();

    }

    private static String readFile(String filePath) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                CrptApi.class.getResourceAsStream(filePath)))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sb.toString();
    }

    public String createRfCommission(Root document, String signature) throws IOException, URISyntaxException, UnsupportedCharsetException {

        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(new URI(API_URL));

        StringEntity requestEntity = new StringEntity(document.toString(), StandardCharsets.US_ASCII);
        httpPost.setEntity(requestEntity);

        httpPost.setHeader("Content-Type","multipart/form-data;application/json;charset=utf-8");
        httpPost.setHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpX....dbj6KCaQUtSwtsZDdghGYJt9tbtPUApZB0ctnjXWHoA");
        httpPost.setHeader("Accept", "*/*");

        HttpResponse httpResponse = httpClient.execute(httpPost);

        HttpEntity httpEntity = httpResponse.getEntity();
        String responseBody = EntityUtils.toString(httpEntity);

        int statusCode = httpResponse.getStatusLine().getStatusCode();
        if (statusCode != (200 | 201)) {
            throw new IOException("Unexpected status code: " + statusCode);
        }

        System.out.println(responseBody);
        return responseBody;
    }

    public synchronized boolean allowRequests() {
        int requests = requestCount.incrementAndGet();
        if (requests > REQUEST_LIMIT) {
            requestCount.decrementAndGet();
            return false;
        }
        return true;
    }

    public synchronized void releaseRequest() {
        requestCount.decrementAndGet();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)){
                    System.out.println("Executor has not been terminated");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void resetRequestsCount() {
        requestCount.set(0);
        notifyAll();
    }

    @Data
    static class Description {
        private String participantInn;
    }

    @Data
    static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @Data
    static class Root {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private Boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private ArrayList<Product> products;
        private String reg_date;
        private String reg_number;
    }
}


