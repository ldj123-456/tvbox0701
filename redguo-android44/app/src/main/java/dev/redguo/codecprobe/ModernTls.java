package dev.redguo.codecprobe;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.conscrypt.Conscrypt;

/**
 * Local, independently bundled TLS provider for Android KitKat.
 * Keep normal certificate chain and HTTPS hostname verification enabled.
 * Do not trust all certificates and do not disable hostname checks.
 */
final class ModernTls {
    private static volatile SSLSocketFactory cachedFactory;
    private static volatile String status = "未尝试 TLS 连接";

    static synchronized SSLSocketFactory factory() throws Exception {
        if (cachedFactory != null) return cachedFactory;
        if (!Conscrypt.isAvailable()) {
            status = "Conscrypt JNI 初始化失败，未使用不安全降级";
            throw new IOException(status);
        }
        SSLContext context = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        context.init(null, null, new SecureRandom());
        cachedFactory = new SniFactory(context.getSocketFactory());
        status = "TLS 引擎：" + context.getProvider().getName() + "（等待握手）";
        return cachedFactory;
    }
    static String status() { return status; }
    static void noteFailure(Throwable error) {
        status = status + "；失败：" + error.getClass().getSimpleName() +
                (error.getMessage()==null?"":(" "+error.getMessage()));
        if(status.length()>1300)status = status.substring(0,1300);
    }

    private static final class SniFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        SniFactory(SSLSocketFactory delegate) { this.delegate = delegate; }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        private Socket prepare(Socket raw,String host) {
            if (!(raw instanceof SSLSocket)) return raw;
            SSLSocket socket=(SSLSocket)raw;
            if(host!=null && Conscrypt.isConscrypt(socket)) Conscrypt.setHostname(socket,host);
            List<String> allowed=new ArrayList<String>();
            for(String p:socket.getSupportedProtocols()) {
                if("TLSv1.3".equals(p) || "TLSv1.2".equals(p)) allowed.add(p);
            }
            if(!allowed.isEmpty()) socket.setEnabledProtocols(allowed.toArray(new String[0]));
            socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
                @Override public void handshakeCompleted(HandshakeCompletedEvent event) {
                    status = "TLS 成功：" + event.getSession().getProtocol()
                            + " / " + event.getCipherSuite();
                }
            });
            return socket;
        }
        @Override public Socket createSocket(Socket socket,String host,int port,boolean close) throws IOException {
            return prepare(delegate.createSocket(socket,host,port,close),host);
        }
        @Override public Socket createSocket(String host,int port) throws IOException {
            return prepare(delegate.createSocket(host,port),host);
        }
        @Override public Socket createSocket(String host,int port,InetAddress local,int localPort) throws IOException {
            return prepare(delegate.createSocket(host,port,local,localPort),host);
        }
        @Override public Socket createSocket(InetAddress host,int port) throws IOException {
            return prepare(delegate.createSocket(host,port),host.getHostName());
        }
        @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort) throws IOException {
            return prepare(delegate.createSocket(host,port,local,localPort),host.getHostName());
        }
    }
}
