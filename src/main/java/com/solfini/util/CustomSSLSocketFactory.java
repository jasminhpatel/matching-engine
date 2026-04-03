package com.solfini.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;

public class CustomSSLSocketFactory extends SSLSocketFactory {
  private final SSLSocketFactory delegate;
  private final InetAddress localAddress;

  public CustomSSLSocketFactory(final SSLSocketFactory delegate,final InetAddress localAddress) {
    this.delegate = delegate;
    this.localAddress = localAddress;
  }

  @Override
  public Socket createSocket() throws IOException {
  final  Socket socket = new Socket();
    socket.bind(new InetSocketAddress(localAddress, 0));
    return socket;
  }

  @Override
  public Socket createSocket(final Socket s, final String host,final int port,final boolean autoClose) throws IOException {
    return delegate.createSocket(s, host, port, autoClose);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
  final  Socket socket = createSocket();
    return delegate.createSocket(socket, host, port, true);
  }

  @Override
  public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
    return createSocket(host, port);
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
 final   Socket socket = createSocket();
    return delegate.createSocket(socket, host.getHostAddress(), port, true);
  }

  @Override
  public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException {
    return createSocket(address, port);
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }
}
