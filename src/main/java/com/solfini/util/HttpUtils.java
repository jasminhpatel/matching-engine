package com.solfini.util;

import java.net.InetSocketAddress;
import java.net.Proxy;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class HttpUtils {

  public static Response get(final String httpsURL, final Map<String, Object> headers) {
    URL url = null;
    HttpsURLConnection connection = null;
    InputStream is = null;
    InputStreamReader isr = null;
    try {
      url = new URL(httpsURL);
      connection = (HttpsURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestMethod("GET");
      for (Map.Entry<String, Object> entry : headers.entrySet()) {
        connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
      }

      int code = connection.getResponseCode();
      if (code >= 400) {
        is = connection.getErrorStream();
      } else {
        is = connection.getInputStream();
      }

      isr = new InputStreamReader(is);
      int numCharsRead;
      char[] charArray = new char[1024];
      StringBuffer sb = new StringBuffer();
      while ((numCharsRead = isr.read(charArray)) > 0) {
        sb.append(charArray, 0, numCharsRead);
      }

      return new Response(code, sb.toString());

    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (isr != null) {
        try {
          isr.close();
        } catch (IOException e) {
        }
      }
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
    return null;
  }

  public static Response get(final String httpsURL, final Map<String, Object> headers, final String proxyHost, final int proxyPort) {
    URL url = null;
    HttpsURLConnection connection = null;
    InputStream is = null;
    InputStreamReader isr = null;
    try {
      url = new URL(httpsURL);
      Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
      connection = (HttpsURLConnection) url.openConnection(proxy);
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestMethod("GET");
      for (Map.Entry<String, Object> entry : headers.entrySet()) {
        connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
      }

      int code = connection.getResponseCode();
      if (code >= 400) {
        is = connection.getErrorStream();
      } else {
        is = connection.getInputStream();
      }

      isr = new InputStreamReader(is);
      int numCharsRead;
      final char[] charArray = new char[1024];
      final StringBuffer sb = new StringBuffer();
      while ((numCharsRead = isr.read(charArray)) > 0) {
        sb.append(charArray, 0, numCharsRead);
      }

      return new Response(code, sb.toString());

    } catch (final MalformedURLException e) {
      e.printStackTrace();
    } catch (final IOException e) {
      e.printStackTrace();
    } finally {
      if (isr != null) {
        try {
          isr.close();
        } catch (IOException e) {
        }
      }
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
    return null;
  }

  public static Response post(final String httpsURL, final Map<String, Object> headers, final byte[] requestData) {
    URL url = null;
    HttpsURLConnection connection = null;
    InputStream is = null;
    InputStreamReader isr = null;
    try {
      url = new URL(httpsURL);
      connection = (HttpsURLConnection) url.openConnection();
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.setUseCaches(false);
      connection.setRequestMethod("POST");
      for (Map.Entry<String, Object> entry : headers.entrySet()) {
        connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
      }

      if (requestData != null) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(requestData, 0, requestData.length);
        baos.writeTo(connection.getOutputStream());
        baos.flush();
      }

      int code = connection.getResponseCode();
      if (code >= 400) {
        is = connection.getErrorStream();
      } else {
        is = connection.getInputStream();
      }

      isr = new InputStreamReader(is);
      int numCharsRead;
      char[] charArray = new char[1024];
      StringBuffer sb = new StringBuffer();
      while ((numCharsRead = isr.read(charArray)) > 0) {
        sb.append(charArray, 0, numCharsRead);
      }

      return new Response(code, sb.toString());

    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (isr != null) {
        try {
          isr.close();
        } catch (IOException e) {
        }
      }
      if (is != null) {
        try {
          is.close();
        } catch (IOException e) {
        }
      }
    }
    return null;
  }

  public static class Response {
    private final int code;
    private final String data;

    public Response(int code, String data) {
      this.code = code;
      this.data = data;
    }

    public int getCode() {
      return code;
    }

    public String getData() {
      return data;
    }
  }
}
