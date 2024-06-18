import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static final String CREATE_DOCUMENT_URL_STR = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();
    private final HttpClient httpClient;
    private final int requestLimit;
    private final AtomicInteger requestSent = new AtomicInteger(0);
    private final ScheduledExecutorService executor;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        executor = Executors.newScheduledThreadPool(1);
        startTimer(timeUnit);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        mapper.registerModule(new JavaTimeModule());
    }

    public synchronized void createDocument(Document document, String sign) {
        if (requestSent.get() >= requestLimit) {
            System.out.println("Request limit exceeded");
            return;
        }

        String jsonData = serializeData(Map.of("document", document, "sign", sign));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CREATE_DOCUMENT_URL_STR))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException ignored) {
            Thread.currentThread().interrupt();
        }
        int requestCount = requestSent.incrementAndGet();
        System.out.println("Current request count: " + requestCount);
    }

    protected synchronized void shutDown() {
        executor.shutdown();
    }

    private void startTimer(TimeUnit timeUnit) {
        Runnable timerTask = () -> {
            requestSent.set(0);
        };
        executor.scheduleAtFixedRate(timerTask, 0, 1, timeUnit);
    }

    private static String serializeData(Map<String, Object> data) {
        String jsonString = "";
        try {
            jsonString = mapper.writeValueAsString(data);
        } catch (JsonProcessingException ignored) {
        }
        return jsonString;
    }

    private static String generateRandomString() {
        int length = random.nextInt(10);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, Math.min(length, uuid.length()));
    }

    public static class Description {
        public String participantInn;

        public Description() {
            this.participantInn = generateRandomString();
        }
    }

    public static class Product {
        public String certificate_document;
        public LocalDate certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public LocalDate production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;

        public Product() {
            this.certificate_document = generateRandomString();
            this.certificate_document_date = LocalDate.now();
            this.certificate_document_number = generateRandomString();
            this.owner_inn = generateRandomString();
            this.producer_inn = generateRandomString();
            this.production_date = LocalDate.now();
            this.tnved_code = generateRandomString();
            this.uit_code = generateRandomString();
            this.uitu_code = generateRandomString();
        }
    }

    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public LocalDate production_date;
        public String production_type;
        public List<Product> products;
        public LocalDate reg_date;
        public String reg_number;

        public Document() {
            this.description = new Description();
            this.doc_id = generateRandomString();
            this.doc_status = generateRandomString();
            this.doc_type = generateRandomString();
            this.importRequest = false;
            this.owner_inn = generateRandomString();
            this.participant_inn = generateRandomString();
            this.producer_inn = generateRandomString();
            this.production_date = LocalDate.now();
            this.production_type = generateRandomString();
            this.products = List.of(new Product(), new Product());
            this.reg_date = LocalDate.now();
            this.reg_number = generateRandomString();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        final int requestLimit = 5;
        final String sign = "sign";
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, requestLimit);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(requestLimit);
        Runnable task = () -> {
            for (int i = 0; i < requestLimit + 2; i++) {
                Thread thread = new Thread(() -> crptApi.createDocument(new Document(), sign));
                thread.start();
            }
        };
        executor.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);

        Thread.sleep(10000);
        executor.shutdownNow();
        crptApi.shutDown();
    }
}
