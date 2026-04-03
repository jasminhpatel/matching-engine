package com.solfini.util;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiUtils {
  private static final Logger LOGGER = LogManager.getLogger(ApiUtils.class);
  private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
  private static final String API_URL = Context.getApiUrl();
  private static final String REQUEST_TOKEN = Context.getRequestToken();
  private static final String REQUEST_SECRET = Context.getRequestSecret();
  private static String COOKIE = null;

  public static void sendPendingWithdrawWithVTokenRequest(final int withdrawId, final int account, final String channel) throws Exception {
    final StringBuilder sb = new StringBuilder("{");
    sb.append(",\"withdrawId\":").append(withdrawId);
    sb.append(",\"account\":").append(account);
    sb.append(",\"channel\":\"").append(channel).append("\"");
    sb.append("}");
    invoke("/api/sendPendingWithdrawWithVTokenRequest", sb.toString());
  }

  private static void invoke(final String service, final String payload) throws Exception {
    invoke(service, payload, REQUEST_TOKEN, REQUEST_SECRET);
  }

  private static void invoke(final String service, final String payload, final String requestToken, final String requestSecret) throws Exception {
    final long timestamp = System.nanoTime();
    String signature = hexdigestV2(payload.getBytes(), requestSecret.getBytes());
    LOGGER.info(timestamp + " request: " + service + " " + payload);
    //System.out.println(timestamp + " request: " + service + " " + payload);
    String response = postV2(API_URL + service, payload.getBytes(), requestToken, signature);
    LOGGER.info(timestamp + " response: " + service + " " + response);
    //System.out.println(timestamp + " response: " + service + " " + response);
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
      con.setRequestProperty("User-Agent", "Mozilla");
      con.setRequestProperty("X-MBX-APIKEY", requestToken);
      con.setRequestProperty("signature", signature);
      if (COOKIE != null) {
        con.setRequestProperty("cookie", COOKIE);
      }
      con.setRequestMethod("POST");

      final ByteArrayOutputStream stream = new ByteArrayOutputStream();
      if (requestData != null)
        stream.write(requestData, 0, requestData.length);
      stream.writeTo(con.getOutputStream());
      stream.flush();

      if (COOKIE == null) {
        COOKIE = con.getHeaderField("Set-Cookie");
      }

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
     // LOGGER.error(Constants.ERROR, e);
    }
    return responseData.toString();
  }
}
