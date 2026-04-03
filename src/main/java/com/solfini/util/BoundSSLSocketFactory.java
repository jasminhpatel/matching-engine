package com.solfini.util;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.security.*;

public class BoundSSLSocketFactory extends SSLSocketFactory {

  private final SSLSocketFactory defaultFactory;
  private final InetAddress localAddress;

  public BoundSSLSocketFactory(final InetAddress localAddress) throws NoSuchAlgorithmException, KeyManagementException {
    this.defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    this.localAddress = localAddress;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return defaultFactory.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return defaultFactory.getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket(final Socket s, final String host, final int port, final boolean autoClose) throws IOException {
    return defaultFactory.createSocket(s, host, port, autoClose);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    final Socket socket = new Socket();
    socket.bind(new InetSocketAddress(localAddress, 0));
    socket.connect(new InetSocketAddress(host, port));
    return defaultFactory.createSocket(socket, host, port, true);
  }

  @Override
  public Socket createSocket(final String host, final int port, final InetAddress localAddr, final int localPort) throws IOException {
    final Socket socket = new Socket();
    socket.bind(new InetSocketAddress(localAddress, localPort));
    socket.connect(new InetSocketAddress(host, port));
    return defaultFactory.createSocket(socket, host, port, true);
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    final Socket socket = new Socket();
    socket.bind(new InetSocketAddress(localAddress, 0));
    socket.connect(new InetSocketAddress(host, port));
    return defaultFactory.createSocket(socket, host.getHostName(), port, true);
  }

  @Override
  public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddr, final int localPort) throws IOException {
    final Socket socket = new Socket();
    socket.bind(new InetSocketAddress(localAddress, localPort));
    socket.connect(new InetSocketAddress(address, port));
    return defaultFactory.createSocket(socket, address.getHostName(), port, true);
  }
}
