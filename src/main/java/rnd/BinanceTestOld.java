package rnd;

import com.solfini.util.HttpUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class BinanceTestOld {
  static String HMAC_SHA256_ALGORITHM = "HmacSHA256";
  static String REQUEST_TOKEN = "L65Hq6Fcc6LdROvk32nwjzzvC4fbWQpUx1WG8cFJSMXiV55jpTe7a117RJ57hKOc";
  static String REQUEST_SECRET = "P4AgUEQtLEzKfGe31C8y9nzjYUunwgbtpMXuqjmajHhkVa5vm3gmJn7I35WVJVFA";
  //static String REQUEST_TOKEN = "1d773b4c6f1361614091fc5e9e48de1fd4e0b8dbd8a6c917844b69dec4def0f0";
  //static String REQUEST_SECRET = "4d9b0470fec2f70ecb58a13c417af5e5cd3011e68088cf37934e67f45b343b19";
  static String HOST = "https://testnet.binance.vision";
  //static String HOST = "https://testnet.binancefuture.com";
  static String X_MBX_APIKEY = "X-MBX-APIKEY";

  public static void main(String[] args) throws Exception {
    //getAccount();

    getOrder();
  }
  //symbol=BTCUSDT&orderId=3728018067&recvWindow=60000&timestamp=1710400969212
  //symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559
  public static void getOrder() throws Exception {
    StringBuilder url = new StringBuilder();
    url.append("symbol=").append("BTCUSDT");
    url.append("&orderId=").append("3728018067");
    url.append("&recvWindow=").append(60000);
    url.append("&timestamp=").append(System.currentTimeMillis());
    System.out.println(url);
    String signature = getSignature(url.toString().getBytes(), REQUEST_SECRET.getBytes());
    url.append("&signature=").append(signature);
    System.out.println(url);
    url.insert(0, "/api/v3/order?");
    //url.insert(0, "/fapi/v1/order?");
    url.insert(0, HOST);

    final String fullUrl = url.toString();
    System.out.println(fullUrl);

    Map<String, Object> headers = new HashMap<>();
    headers.put(X_MBX_APIKEY, REQUEST_TOKEN);

    HttpUtils.Response response = HttpUtils.get(fullUrl, headers);
    System.out.println(response.getCode());
    if (response != null && (response.getCode() == 200 || response.getCode() == 201)) {
      final String returnValue = response.getData();
      System.out.println(returnValue);
    } else {
      System.out.println(response.getData());
    }

  }

  private static String getSignature(final byte[] message, final byte[] keyData) throws Exception {
    final SecretKey key = new SecretKeySpec(keyData, HMAC_SHA256_ALGORITHM);
    final Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
    mac.init(key);
    mac.update(message);
    byte[] hmac = mac.doFinal();
    String hd;
    final BigInteger hash = new BigInteger(1, hmac);
    hd = hash.toString(16);
    while (hd.length() < 32) {
      hd = "0" + hd;
    }
    return hd;
  }

  public static void getAccount() throws Exception {
/*    OkHttpClient client = new OkHttpClient().newBuilder()
        .build();
    MediaType mediaType = MediaType.parse("application/json");
    RequestBody body = RequestBody.create(mediaType, "");
    Request request = new Request.Builder()
        .url("https://testnet.binance.vision/api/v3/account?recvWindow=5000&timestamp=new Date().getTime()&signature={{signature}}")
        .method("GET", body)
        .addHeader("Content-Type", "application/json")
        .addHeader("X-MBX-APIKEY", "L65Hq6Fcc6LdROvk32nwjzzvC4fbWQpUx1WG8cFJSMXiV55jpTe7a117RJ57hKOc")
        .build();
    Response response = client.newCall(request).execute();
    invoke("/api/v3/account?timestamp=" + System.currentTimeMillis());*/
  }

  private static String invoke(final String service) throws Exception {
    return invoke(service, REQUEST_TOKEN, REQUEST_SECRET);
  }

  private static String invoke(final String service, final String payload) throws Exception {
    return invoke(service, payload, REQUEST_TOKEN, REQUEST_SECRET);
  }

  private static String invoke(final String service, final String payload, final String requestToken, final String requestSecret)
      throws Exception {
    String signature = hexdigestV2(payload.getBytes(), requestSecret.getBytes());
    System.out.println(service + " " + payload + " requestSecret=" + requestSecret + ", signature=" + signature);
    String response = postV2(HOST + service, payload.getBytes(), requestToken, signature);
    System.out.println("<< " + response);
    return response;
  }

  private static String invoke(final String service, final String requestToken, final String requestSecret) throws Exception {
    System.out.println(service + " " + " requestSecret=" + requestSecret);
    String response = getV2(HOST + service, requestToken, null);
    System.out.println("<< " + response);
    return response;
  }

  private static String hexdigestV2(final byte[] message, final byte[] keyData) throws Exception {
    final SecretKey key = new SecretKeySpec(keyData, HMAC_SHA256_ALGORITHM);
    final Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
    mac.init(key);
    mac.update(message);
    byte[] hmac = mac.doFinal();
    String hd;
    final BigInteger hash = new BigInteger(1, hmac);
    hd = hash.toString(16);
    while (hd.length() < 32) {
      hd = "0" + hd;
    }
    return hd;
  }

  private static String postV2(final String httpsURL, final byte[] requestData, final String requestToken, final String signature) {
    final StringBuilder responseData = new StringBuilder();
    try {
      final URL url = new URL(httpsURL);
      final HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setDoOutput(true);
      con.setDoInput(true);
      con.setUseCaches(false);
      con.setRequestProperty("X-MBX-APIKEY", requestToken);
      con.setRequestProperty("signature", signature);
      con.setRequestMethod("POST");

      final ByteArrayOutputStream stream = new ByteArrayOutputStream();
      if (requestData != null)
        stream.write(requestData, 0, requestData.length);
      stream.writeTo(con.getOutputStream());
      stream.flush();

      final InputStream ins = con.getInputStream();
      final InputStreamReader isr = new InputStreamReader(ins);
      final BufferedReader in = new BufferedReader(isr);

      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        responseData.append(inputLine);
        responseData.append("\n");
      }
      in.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return responseData.toString();
  }

  private static String getV2(final String httpsURL, final String requestToken, final String signature) {
    final StringBuilder responseData = new StringBuilder();
    try {
      final URL url = new URL(httpsURL);
      final HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setDoOutput(true);
      con.setDoInput(true);
      con.setUseCaches(false);
      con.setRequestProperty("X-MBX-APIKEY", requestToken);
      //con.setRequestProperty("signature", signature);
      con.setRequestMethod("GET");

      final InputStream ins = con.getInputStream();
      final InputStreamReader isr = new InputStreamReader(ins);
      final BufferedReader in = new BufferedReader(isr);

      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        responseData.append(inputLine);
        responseData.append("\n");
      }
      in.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return responseData.toString();
  }

}
