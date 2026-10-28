package dev.redguo.hgtvbridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Read-only local HTTP Range bridge for a seekable, already authorized MP4 source.
 * Does not fetch third-party media or perform authorization/decryption itself.
 */
public final class LoopbackRangeServer implements Closeable {
  public interface SeekableSource extends Closeable {
    long length() throws IOException;
    int read(long position, byte[] into, int offset, int count) throws IOException;
  }
  private static final int MAX_LINE=2048, CHUNK=65536;
  private final SeekableSource source;
  private final ServerSocket listener;
  private final ThreadPoolExecutor workers;
  private final Thread accept;
  private final String path;
  private volatile boolean closed;

  public LoopbackRangeServer(SeekableSource input) throws IOException {
    if(input==null)throw new IllegalArgumentException("source required");
    source=input;
    byte[] token=new byte[24];
    new SecureRandom().nextBytes(token);
    StringBuilder key=new StringBuilder("/media/");
    for(byte x:token)key.append(String.format(Locale.ROOT,"%02x",x & 255));
    path=key.toString();
    listener=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
    workers=new ThreadPoolExecutor(2,2,5L,TimeUnit.SECONDS,
      new ArrayBlockingQueue<Runnable>(6),new ThreadPoolExecutor.AbortPolicy());
    accept=new Thread(new Runnable(){@Override public void run(){acceptLoop();}},"hgtv-local-accept");
    accept.setDaemon(true);
    accept.start();
  }
  public String url(){return "http://127.0.0.1:"+listener.getLocalPort()+path;}
  private void acceptLoop(){
    while(!closed){
      try{
        final Socket socket=listener.accept();
        socket.setSoTimeout(15000);
        try{workers.execute(new Runnable(){@Override public void run(){serve(socket);}});}
        catch(RuntimeException full){try{socket.close();}catch(IOException ignored){}}
      }catch(SocketException stopped){break;}
      catch(IOException broken){break;}
    }
  }
  private static String nextLine(InputStream in) throws IOException{
    byte[] buffer=new byte[MAX_LINE];int count=0;
    while(true){
      int ch=in.read();
      if(ch==-1)return count==0?null:new String(buffer,0,count,StandardCharsets.US_ASCII);
      if(ch=='\n'){
        if(count>0 && buffer[count-1]=='\r')count--;
        return new String(buffer,0,count,StandardCharsets.US_ASCII);
      }
      if(count>=MAX_LINE)throw new IOException("long HTTP line");
      buffer[count++]=(byte)ch;
    }
  }
  private static void header(OutputStream output,int status,String reason,long contentLength,String range)throws IOException{
    StringBuilder s=new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
      .append("Content-Type: video/mp4\r\nAccept-Ranges: bytes\r\nContent-Length: ")
      .append(contentLength).append("\r\nCache-Control: no-store\r\nConnection: close\r\n");
    if(range!=null)s.append("Content-Range: ").append(range).append("\r\n");
    output.write(s.append("\r\n").toString().getBytes(StandardCharsets.US_ASCII));
  }
  private static long[] bounds(String requested,long total)throws IOException{
    if(total<=0)throw new IOException("empty media");
    if(requested==null)return new long[]{0,total-1};
    if(!requested.startsWith("bytes=")||requested.indexOf(',')>=0)throw new IOException("bad Range");
    String value=requested.substring(6).trim();
    int dash=value.indexOf('-');
    if(dash<0||value.indexOf('-',dash+1)>=0)throw new IOException("bad Range");
    try{
      String a=value.substring(0,dash).trim(),b=value.substring(dash+1).trim();
      long from,to;
      if(a.length()==0){
        long n=Long.parseLong(b);
        if(n<=0)throw new IOException("bad suffix");
        from=total-Math.min(n,total);to=total-1;
      }else{
        from=Long.parseLong(a);to=b.length()==0?total-1:Long.parseLong(b);
        if(from<0||from>=total||to<from)throw new IOException("unsatisfiable");
        to=Math.min(total-1,to);
      }
      return new long[]{from,to};
    }catch(NumberFormatException e){throw new IOException("invalid Range number",e);}
  }
  private void serve(Socket peer){
    try(Socket socket=peer;
        InputStream input=new BufferedInputStream(socket.getInputStream());
        OutputStream output=new BufferedOutputStream(socket.getOutputStream())){
      String request=nextLine(input);
      if(request==null)return;
      String[] tokens=request.split(" ",3);
      String range=null;
      for(int i=0;i<40;i++){
        String line=nextLine(input);
        if(line==null||line.length()==0)break;
        if(line.regionMatches(true,0,"Range:",0,6))range=line.substring(6).trim();
      }
      if(tokens.length!=3||(!"GET".equals(tokens[0])&&!"HEAD".equals(tokens[0]))){
        header(output,405,"Method Not Allowed",0,null);output.flush();return;
      }
      if(!path.equals(tokens[1])){
        header(output,404,"Not Found",0,null);output.flush();return;
      }
      long total=source.length();
      if(total<=0){header(output,503,"Unavailable",0,null);output.flush();return;}
      long[] r;
      try{r=bounds(range,total);}catch(IOException invalid){
        header(output,416,"Range Not Satisfiable",0,"bytes */"+total);output.flush();return;
      }
      long from=r[0],left=r[1]-r[0]+1;
      header(output,range==null?200:206,range==null?"OK":"Partial Content",left,
        range==null?null:"bytes "+from+"-"+r[1]+"/"+total);
      if("HEAD".equals(tokens[0])){output.flush();return;}
      byte[] buffer=new byte[CHUNK];
      while(!closed&&left>0){
        int wanted=(int)Math.min(left,buffer.length);
        int count=source.read(from,buffer,0,wanted);
        if(count<=0||count>wanted)throw new IOException("invalid media read length");
        output.write(buffer,0,count);from+=count;left-=count;
      }
      output.flush();
    }catch(IOException ignored){ /* cancelled stream or receiver closed */ }
  }
  @Override public synchronized void close()throws IOException{
    if(closed)return;
    closed=true;listener.close();workers.shutdownNow();accept.interrupt();source.close();
  }
}
