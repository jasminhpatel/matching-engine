package com.solfini.util.blockchain.util;

import org.apache.commons.codec.binary.Base64;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;

/**
 * Class HTTPSClient.
 * 
 * @author Chris.
 */
public class HTTPSClient {

  public final static String get(final String httpsURL) {
    StringBuffer responseData = new StringBuffer();
    try {
      URL myurl = new URL(httpsURL);
      HttpsURLConnection con = (HttpsURLConnection) myurl.openConnection();
      con.setRequestMethod("GET");
      InputStream ins;
      if (con.getResponseCode() >= 400) {
        ins = con.getErrorStream();
      } else {
        ins = con.getInputStream();
      }
      BufferedReader in = new BufferedReader(new InputStreamReader(ins));
      String str;
      while ((str = in.readLine()) != null) {
        // str is one line of text; readLine() strips the newline character(s)
        responseData.append(str);
      }
      in.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
    return responseData.toString();
  }

  public final static String post(final String httpsURL, final byte[] requestData, final String username, final String password) {
    String result = "";
    try {
      String authString = username + ":" + password;
      // System.out.println("auth string: " + authString);
      byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
      String authStringEnc = new String(authEncBytes);
      // System.out.println("Base64 encoded auth string: " + authStringEnc);

      URL url = new URL(httpsURL);
      // URLConnection urlConnection = url.openConnection();
      URLConnection urlConnection = url.openConnection();

      urlConnection.setDoOutput(true);
      urlConnection.setDoInput(true);
      urlConnection.setUseCaches(false);
      urlConnection.setRequestProperty("Authorization", "Basic " + authStringEnc);
      // urlConnection.setRequestMethod("POST");

      if (requestData != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(requestData, 0, requestData.length);
        baos.writeTo(urlConnection.getOutputStream());
        baos.flush();
      }

      InputStream is = urlConnection.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);
      int numCharsRead;
      char[] charArray = new char[1024];
      StringBuffer sb = new StringBuffer();
      while ((numCharsRead = isr.read(charArray)) > 0) {
        sb.append(charArray, 0, numCharsRead);
      }
      result = sb.toString();

      // System.out.println("*** BEGIN ***");
      // System.out.println(result);
      // System.out.println("*** END ***");
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }

  public final static String postWithAuthorization(final String httpsURL, final byte[] requestData, final String authorization) {
    String result = "";
    try {
      URL url = new URL(httpsURL);
      HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
      urlConnection.setDoOutput(true);
      urlConnection.setDoInput(true);
      urlConnection.setUseCaches(false);
      urlConnection.setRequestProperty("Authorization", authorization);
      urlConnection.setRequestMethod("POST");


      if (requestData != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(requestData, 0, requestData.length);
        baos.writeTo(urlConnection.getOutputStream());
        baos.flush();
      }

      InputStream is = urlConnection.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);
      int numCharsRead;
      char[] charArray = new char[1024];
      StringBuffer sb = new StringBuffer();
      while ((numCharsRead = isr.read(charArray)) > 0) {
        sb.append(charArray, 0, numCharsRead);
      }
      result = sb.toString();

      // System.out.println("*** BEGIN ***");
      // System.out.println(result);
      // System.out.println("*** END ***");
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return result;
  }


  public final static String post(final String httpsURL, final byte[] requestData) {
    StringBuffer responseData = new StringBuffer();
    try {
      URL myurl = new URL(httpsURL);
      HttpsURLConnection con = (HttpsURLConnection) myurl.openConnection();
      con.setDoOutput(true);
      con.setDoInput(true);
      con.setUseCaches(false);


      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      baos.write(requestData, 0, requestData.length);
      baos.writeTo(con.getOutputStream());
      baos.flush();

      InputStream ins;
      if (con.getResponseCode() >= 400) {
        ins = con.getErrorStream();
      } else {
        ins = con.getInputStream();
      }
      InputStreamReader isr = new InputStreamReader(ins);
      BufferedReader in = new BufferedReader(isr);

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

  public final static String postWithSignature(final String httpsURL, final byte[] requestData, final byte[] requestSecret)
      throws Exception {
    String result = null;
    try {
      final String signature = HmacSHA384.hexdigest(requestData, requestSecret);
      final URL url = new URL(httpsURL);
      final HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setDoOutput(true);
      con.setDoInput(true);
      con.setUseCaches(false);
      con.setRequestProperty("signature", signature);
      con.setRequestMethod("POST");
      con.setConnectTimeout(5 * 60* 1000); //set timeout to 2 minutes
      con.setReadTimeout(10 * 60* 1000); //set timeout to 5 minutes

      if (requestData != null) {
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
          baos.write(requestData, 0, requestData.length);
          try (final OutputStream stream = con.getOutputStream();) {
            baos.writeTo(stream);
          }
          baos.flush();
        }
      }
      InputStream is;
      if (con.getResponseCode() >= 400) {
        is = con.getErrorStream();
      } else {
        is = con.getInputStream();
      }

      try (final InputStreamReader isr = new InputStreamReader(is);) {
        int numCharsRead;
        final char[] charArray = new char[1024];
        final StringBuilder sb = new StringBuilder();
        while ((numCharsRead = isr.read(charArray)) > 0) {
          sb.append(charArray, 0, numCharsRead);
        }
        result = sb.toString();
      }
    } catch (final MalformedURLException e) {
      e.printStackTrace();
      return "{\"error\":\"" + e.getMessage() + "\"}";
    } catch (final IOException e) {
      e.printStackTrace();
      throw e;//retry
    } catch (final Exception e) {
      e.printStackTrace();
      throw e;//retry
    }
    return result;
  }

}
