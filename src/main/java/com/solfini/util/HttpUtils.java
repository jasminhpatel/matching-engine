package com.solfini.util;

/*
 * import java.net.InetAddress; import java.net.Socket;
 */

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bybit.BybitRestClient;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/*
 * import javax.net.ssl.SSLContext; import org.apache.http.client.methods.CloseableHttpResponse; import
 * org.apache.http.client.methods.HttpGet; import org.apache.http.client.methods.HttpPost; import
 * org.apache.http.conn.socket.ConnectionSocketFactory; import org.apache.http.conn.socket.PlainConnectionSocketFactory; import
 * org.apache.http.conn.ssl.SSLConnectionSocketFactory; import org.apache.http.entity.ByteArrayEntity; import
 * org.apache.http.impl.client.CloseableHttpClient; import org.apache.http.impl.client.HttpClients; import
 * org.apache.http.impl.conn.PoolingHttpClientConnectionManager; import org.apache.http.util.EntityUtils;
 */
public class HttpUtils {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(HttpUtils.class);

    public static Response get(final String httpsURL, final Map<String, Object> headers) {
        return get(httpsURL, headers, null, 0, false);
    }

    public static Response get(final String httpsURL, final Map<String, Object> headers, final String proxyHost, final int proxyPort, final boolean forceToUseProxy) {
        URL url = null;
        HttpsURLConnection connection = null;
        InputStream is = null;
        InputStreamReader isr = null;
        try {
            url = new URL(httpsURL);
            LOGGER.info(Constants.LOG_FMT_6, "Http Request: ", httpsURL, " proxyHost: ", proxyHost, " outboundIp: ", Context.getOutboundIp());
            if (!forceToUseProxy && Context.getOutboundIp() != null && !Context.getOutboundIp().isEmpty()) {
              connection = (HttpsURLConnection) url.openConnection();
              final InetAddress localAddr = InetAddress.getByName(Context.getOutboundIp());
              final SSLContext sslContext = SSLContext.getInstance("TLS");
              sslContext.init(null, null, null);

              connection.setSSLSocketFactory(new CustomSSLSocketFactory(sslContext.getSocketFactory(), localAddr));
            } else if (proxyHost != null) {
              Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
              connection = (HttpsURLConnection) url.openConnection(proxy);
            } else {
              connection = (HttpsURLConnection) url.openConnection();
            }
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
            //LOGGER.info(Constants.LOG_FMT_2, "Http Response: ", sb);

            return new Response(code, sb.toString());

        } catch (final MalformedURLException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
        } catch (KeyManagementException e) {
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

    public static Response post(final String httpsURL, final Map<String, Object> headers, final byte[] requestData, final String proxyHost,
                                final int proxyPort, final boolean forceToUseProxy) {
        URL url = null;
        HttpsURLConnection connection = null;
        InputStream is = null;
        InputStreamReader isr = null;

        try {
            url = new URL(httpsURL);
            LOGGER.info(Constants.LOG_FMT_6, "Http Request: ", httpsURL, " proxyHost: ", proxyHost, " outboundIp: ", Context.getOutboundIp());
            if (!forceToUseProxy && Context.getOutboundIp() != null && !Context.getOutboundIp().isEmpty()) {
              connection = (HttpsURLConnection) url.openConnection();
              final InetAddress localAddr = InetAddress.getByName(Context.getOutboundIp());
              final SSLContext sslContext = SSLContext.getInstance("TLS");
              sslContext.init(null, null, null);

              connection.setSSLSocketFactory(new CustomSSLSocketFactory(sslContext.getSocketFactory(), localAddr));
            } else if (proxyHost != null) {
              Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
              connection = (HttpsURLConnection) url.openConnection(proxy);
            } else {
              connection = (HttpsURLConnection) url.openConnection();
            }

            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");

            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }

            if (requestData != null) {
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestData);
                    os.flush();
                }
            }

            int code = connection.getResponseCode();
            is = (code >= 400) ? connection.getErrorStream() : connection.getInputStream();

            isr = new InputStreamReader(is);
            final char[] charArray = new char[1024];
            int numCharsRead;
            final StringBuilder sb = new StringBuilder();
            while ((numCharsRead = isr.read(charArray)) > 0) {
                sb.append(charArray, 0, numCharsRead);
            }
            LOGGER.info("Http Response: " + sb);
            return new Response(code, sb.toString());

        } catch (final IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
        } catch (KeyManagementException e) {
          e.printStackTrace();
        } finally {
            try {
                if (isr != null)
                    isr.close();
                if (is != null)
                    is.close();
            } catch (final IOException ignore) {
            }
        }
        return null;
    }

    public static Response put(final String httpsURL, final Map<String, Object> headers, final byte[] requestData, final String proxyHost,
                               final int proxyPort, final boolean forceToUseProxy) {
        URL url = null;
        HttpsURLConnection connection = null;
        InputStream is = null;
        InputStreamReader isr = null;

        try {
            url = new URL(httpsURL);
            if (!forceToUseProxy && Context.getOutboundIp() != null && !Context.getOutboundIp().isEmpty()) {
              connection = (HttpsURLConnection) url.openConnection();
              final InetAddress localAddr = InetAddress.getByName(Context.getOutboundIp());
              final SSLContext sslContext = SSLContext.getInstance("TLS");
              sslContext.init(null, null, null);

              connection.setSSLSocketFactory(new CustomSSLSocketFactory(sslContext.getSocketFactory(), localAddr));
            } else if (proxyHost != null) {
              Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
              connection = (HttpsURLConnection) url.openConnection(proxy);
            } else {
              connection = (HttpsURLConnection) url.openConnection();
            }

            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("PUT");

            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }

            if (requestData != null) {
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestData);
                    os.flush();
                }
            }

            int code = connection.getResponseCode();
            is = (code >= 400) ? connection.getErrorStream() : connection.getInputStream();

            if (is != null) {
                isr = new InputStreamReader(is);
                final char[] charArray = new char[1024];
                int numCharsRead;
                final StringBuilder sb = new StringBuilder();
                while ((numCharsRead = isr.read(charArray)) > 0) {
                    sb.append(charArray, 0, numCharsRead);
                }
                return new Response(code, sb.toString());
            } else {
                return new Response(code, "");
            }

        } catch (final IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
        } catch (KeyManagementException e) {
          e.printStackTrace();
        } finally {
            try {
                if (isr != null)
                    isr.close();
                if (is != null)
                    is.close();
            } catch (final IOException ignore) {
            }
        }
        return null;
    }

    public static Response delete(final String httpsURL, final Map<String, Object> headers, final String proxyHost,
                                  final int proxyPort, final boolean forceToUseProxy) {
        URL url = null;
        HttpsURLConnection connection = null;
        InputStream is = null;
        InputStreamReader isr = null;

        try {
            url = new URL(httpsURL);
            if (!forceToUseProxy && Context.getOutboundIp() != null && !Context.getOutboundIp().isEmpty()) {
              connection = (HttpsURLConnection) url.openConnection();
              final InetAddress localAddr = InetAddress.getByName(Context.getOutboundIp());
              final SSLContext sslContext = SSLContext.getInstance("TLS");
              sslContext.init(null, null, null);

              connection.setSSLSocketFactory(new CustomSSLSocketFactory(sslContext.getSocketFactory(), localAddr));
            } else if (proxyHost != null) {
              Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
              connection = (HttpsURLConnection) url.openConnection(proxy);
            } else {
              connection = (HttpsURLConnection) url.openConnection();
            }
            
            connection.setDoOutput(false);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("DELETE");

            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }

            int code = connection.getResponseCode();
            is = (code >= 400) ? connection.getErrorStream() : connection.getInputStream();

            if (is != null) {
                isr = new InputStreamReader(is);
                final char[] charArray = new char[1024];
                int numCharsRead;
                final StringBuilder sb = new StringBuilder();
                while ((numCharsRead = isr.read(charArray)) > 0) {
                    sb.append(charArray, 0, numCharsRead);
                }
                return new Response(code, sb.toString());
            } else {
                return new Response(code, "");
            }

        } catch (final IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
        } catch (KeyManagementException e) {
          e.printStackTrace();
        } finally {
            try {
                if (isr != null)
                    isr.close();
                if (is != null)
                    is.close();
            } catch (final IOException ignore) {
            }
        }
        return null;
    }

    /*
     * public static Response getWithBoundIp(final String httpsURL, final Map<String, Object> headers, final String sourceIp) { try
     * (CloseableHttpClient client = createBoundHttpClient(sourceIp)) { HttpGet get = new HttpGet(httpsURL); for (Map.Entry<String, Object>
     * entry : headers.entrySet()) { get.setHeader(entry.getKey(), String.valueOf(entry.getValue())); } try (CloseableHttpResponse response =
     * client.execute(get)) { int statusCode = response.getStatusLine().getStatusCode(); String body =
     * EntityUtils.toString(response.getEntity()); return new Response(statusCode, body); } } catch (Exception e) { e.printStackTrace();
     * return null; } }
     *
     * public static Response postWithBoundIp(final String httpsURL, final Map<String, Object> headers, final byte[] requestData, final String
     * sourceIp) { try (CloseableHttpClient client = createBoundHttpClient(sourceIp)) { HttpPost post = new HttpPost(httpsURL); for
     * (Map.Entry<String, Object> entry : headers.entrySet()) { post.setHeader(entry.getKey(), String.valueOf(entry.getValue())); } if
     * (requestData != null) { post.setEntity(new ByteArrayEntity(requestData)); } try (CloseableHttpResponse response = client.execute(post))
     * { int statusCode = response.getStatusLine().getStatusCode(); String body = EntityUtils.toString(response.getEntity()); return new
     * Response(statusCode, body); } } catch (Exception e) { e.printStackTrace(); return null; } }
     *
     * private static CloseableHttpClient createBoundHttpClient(String bindIp) throws Exception { final InetAddress localBindAddress =
     * InetAddress.getByName(bindIp);
     *
     * PlainConnectionSocketFactory plainFactory = new PlainConnectionSocketFactory() {
     *
     * @Override public Socket createSocket(org.apache.http.protocol.HttpContext context) throws IOException { Socket socket = new Socket();
     * socket.bind(new InetSocketAddress(localBindAddress, 0)); return socket; } };
     *
     * SSLConnectionSocketFactory sslFactory = new SSLConnectionSocketFactory( SSLContext.getDefault(), new String[] { "TLSv1.2", "TLSv1.3" },
     * null, SSLConnectionSocketFactory.getDefaultHostnameVerifier() ) {
     *
     * @Override public Socket createSocket(org.apache.http.protocol.HttpContext context) throws IOException { Socket socket = new Socket();
     * socket.bind(new InetSocketAddress(localBindAddress, 0)); return socket; } };
     *
     * var socketFactoryRegistry = org.apache.http.config.RegistryBuilder .<ConnectionSocketFactory>create() .register("http", plainFactory)
     * .register("https", sslFactory) .build();
     *
     * PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
     *
     * return HttpClients.custom() .setConnectionManager(connManager) .build(); }
     *
     */

  public static class Response {
    private final int code;
    private final String data;

    public Response(final int code, final String data) {
      this.code = code;
      this.data = data;
    }

    public final int getCode() {
      return code;
    }

    public final String getData() {
      return data;
    }
    }
}
