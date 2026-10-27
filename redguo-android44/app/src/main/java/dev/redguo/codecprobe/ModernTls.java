package dev.redguo.codecprobe;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.conscrypt.Conscrypt;

/**
 * App-bundled modern TLS for Android KitKat, using normal PKIX certificate
 * validation backed by OS CAs plus Mozilla's publicly trusted CAs.
 * HTTPS hostname verification is still handled by HttpsURLConnection.
 * This class never disables trust, hostname validation, or SNI.
 */
final class ModernTls {
    private static volatile SSLSocketFactory cachedFactory;
    private static volatile String status = "尚未发起 TLS 连接";
    static synchronized SSLSocketFactory factory(Context context) throws Exception {
        if (cachedFactory != null) return cachedFactory;
        if (!Conscrypt.isAvailable()) {
            status = "Conscrypt JNI 初始化失败（不进行不安全降级）";
            throw new IOException(status);
        }
        KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
        trusted.load(null, null);
        int systemCount = 0;
        try {
            KeyStore system = KeyStore.getInstance("AndroidCAStore");
            system.load(null, null);
            Enumeration<String> aliases = system.aliases();
            while(aliases.hasMoreElements()) {
                String alias=aliases.nextElement();
                Certificate cert=system.getCertificate(alias);
                if(cert!=null) {
                    trusted.setCertificateEntry("system-"+(systemCount++),cert);
                }
            }
        } catch(Exception ignored) {
            // Some operator firmwares do not expose AndroidCAStore.
            // The independent publicly audited root store remains available.
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        int mozillaCount = 0;
        InputStream stream=context.getAssets().open("mozilla-ca-2026-10-20.pem");
        try {
            Collection<? extends Certificate> ca=cf.generateCertificates(stream);
            for(Certificate cert:ca){
                trusted.setCertificateEntry("mozilla-"+(mozillaCount++),cert);
            }
        }finally{stream.close();}
        if(mozillaCount<100)throw new IOException("现代受信任根证书加载不完整："+mozillaCount);
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trusted); // PKIX validation; no permissive X509TrustManager
        SSLContext tls=SSLContext.getInstance("TLS",Conscrypt.newProvider());
        tls.init(null,trust.getTrustManagers(),new SecureRandom());
        cachedFactory=new SniFactory(tls.getSocketFactory());
        status="可信根证书：系统 "+systemCount+" + Mozilla "+mozillaCount+"；TLS 引擎："+tls.getProvider().getName();
        return cachedFactory;
    }
    static String status(){return status;}
    static void noteFailure(Throwable error) {
        String message=error.getClass().getSimpleName()+" "+(error.getMessage()==null?"":error.getMessage());
        status=status+"；失败："+message;
        if(status.length()>1800)status=status.substring(status.length()-1800);
    }
    private static final class SniFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        SniFactory(SSLSocketFactory delegate){this.delegate=delegate;}
        @Override public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
        @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        private Socket prepare(Socket socket,String hostname){
            if(!(socket instanceof SSLSocket))return socket;
            SSLSocket ssl=(SSLSocket)socket;
            if(hostname!=null && Conscrypt.isConscrypt(ssl))Conscrypt.setHostname(ssl,hostname);
            List<String> protocols=new ArrayList<String>();
            for(String p:ssl.getSupportedProtocols())
                if("TLSv1.2".equals(p)||"TLSv1.3".equals(p))protocols.add(p);
            if(!protocols.isEmpty())ssl.setEnabledProtocols(protocols.toArray(new String[0]));
            ssl.addHandshakeCompletedListener(new HandshakeCompletedListener(){
                @Override public void handshakeCompleted(HandshakeCompletedEvent e){
                    status="证书校验成功；"+e.getSession().getProtocol()+" / "+e.getCipherSuite();
                }
            });
            return ssl;
        }
        @Override public Socket createSocket(Socket socket,String host,int port,boolean close) throws IOException{return prepare(delegate.createSocket(socket,host,port,close),host);}
        @Override public Socket createSocket(String host,int port) throws IOException{return prepare(delegate.createSocket(host,port),host);}
        @Override public Socket createSocket(String host,int port,InetAddress local,int localPort) throws IOException{return prepare(delegate.createSocket(host,port,local,localPort),host);}
        @Override public Socket createSocket(InetAddress host,int port) throws IOException{return prepare(delegate.createSocket(host,port),host.getHostName());}
        @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort) throws IOException{return prepare(delegate.createSocket(host,port,local,localPort),host.getHostName());}
    }
}
