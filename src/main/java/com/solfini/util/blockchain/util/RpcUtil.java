package com.solfini.util.blockchain.util;

import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

public class RpcUtil {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(RpcUtil.class);
  private static final int PROXY_PORT = 8888;

  public static Web3j createWeb3jConnection(final String chainType, final String proxyHost, final boolean useSecondary, final boolean hasProxyError) {
    HttpService httpService;
    final String rpcUrl = Context.getWeb3Provider(chainType, useSecondary);
    if (proxyHost != null) {
      final Proxy proxy = new Proxy(
          Proxy.Type.HTTP,
          new InetSocketAddress(proxyHost, PROXY_PORT)
      );
      final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
          .proxy(proxy)
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS);

      final OkHttpClient client = clientBuilder.build();
      httpService = new HttpService(rpcUrl, client);
    } else {
      httpService = new HttpService(rpcUrl);
    }
    final String connectionInfo = "Web3 Provider: " + rpcUrl + " proxy: " + proxyHost + " useSecondary: "
        + useSecondary + " hasProxyError: " + hasProxyError;
    LOGGER.info(connectionInfo);
    System.out.println(connectionInfo);
    return Web3j.build(httpService);
  }
}
//    lastProxy = ExternalExchangeUtil.getProxy(lastProxy, hasProxyError);