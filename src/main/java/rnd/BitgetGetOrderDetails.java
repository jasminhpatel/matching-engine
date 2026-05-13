package rnd;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class BitgetGetOrderDetails {

  private static final String BASE_URL = "https://api.bitget.com";

  private static String API_KEY = "BITGET_DEMO_API_KEY";
  private static String SECRET_KEY = "BITGET_DEMO_API_SECRET";
  private static String PASSPHRASE = "BITGET_PASSPHRASE";

  // Proxy config (update as needed)
  private static final String PROXY_HOST = "38.242.225.103";
  private static final int PROXY_PORT = 8888;

  // Optional proxy auth
  private static final String PROXY_USER = null; // "user"
  private static final String PROXY_PASS = null; // "password"

  public static void main(String[] args) throws Exception {
    API_KEY = System.getenv("BITGET_DEMO_API_KEY");
    SECRET_KEY = System.getenv("BITGET_DEMO_API_SECRET");
    PASSPHRASE = System.getenv("BITGET_PASSPHRASE");

    String orderId = "1778288438046174227181";

    String response = getOrderDetails(API_KEY, SECRET_KEY, PASSPHRASE, null, orderId);
    System.out.println(response);
  }

  public static String getOrderDetails(
      final String apiKey,
      final String secretKey,
      final String passphrase,
      final String orderId,
      final String clientOid
  ) throws Exception {

    if ((orderId == null || orderId.isBlank()) &&
        (clientOid == null || clientOid.isBlank())) {
      throw new IllegalArgumentException("Either orderId or clientOid is required");
    }

    String path = "/api/v3/trade/order-info";
    String query;

    if (orderId != null && !orderId.isBlank()) {
      query = "orderId=" + urlEncode(orderId);
    } else {
      query = "clientOid=" + urlEncode(clientOid);
    }

    String requestPath = path + "?" + query;
    String method = "GET";
    String timestamp = String.valueOf(Instant.now().toEpochMilli());
    String body = "";

    String preHash = timestamp + method + requestPath + body;
    String signature = hmacSha256Base64(preHash, secretKey);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + requestPath))
        .GET()
        .header("ACCESS-KEY", apiKey)
        .header("ACCESS-SIGN", signature)
        .header("ACCESS-TIMESTAMP", timestamp)
        .header("ACCESS-PASSPHRASE", passphrase)
        .header("Content-Type", "application/json")
        .header("locale", "en-US")
        .build();

    HttpClient client = buildHttpClientWithProxy();

    HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException(
          "HTTP error: " + response.statusCode() + ", body=" + response.body()
      );
    }

    return response.body();
  }

  private static HttpClient buildHttpClientWithProxy() {
    HttpClient.Builder builder = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)));

    // Optional: proxy authentication
    if (PROXY_USER != null && PROXY_PASS != null) {
      builder.authenticator(new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
          return new PasswordAuthentication(PROXY_USER, PROXY_PASS.toCharArray());
        }
      });
    }

    return builder.build();
  }

  private static String hmacSha256Base64(String data, String secretKey) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec =
        new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    mac.init(keySpec);

    byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(rawHmac);
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}