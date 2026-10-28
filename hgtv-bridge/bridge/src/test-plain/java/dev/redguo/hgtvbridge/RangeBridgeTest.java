package dev.redguo.hgtvbridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public final class RangeBridgeTest {
  private static final byte[] data=new byte[100000];
  private static void check(boolean good,String name){if(!good)throw new AssertionError(name);}
  private static byte[] read(InputStream in)throws IOException{
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
    while((n=in.read(buf))>=0)out.write(buf,0,n);
    in.close();return out.toByteArray();
  }
  private static final class Source implements LoopbackRangeServer.SeekableSource{
    boolean closed;
    public long length(){return data.length;}
    public synchronized int read(long p,byte[] buf,int off,int count){
      if(closed)return -1;
      int len=(int)Math.min(count,data.length-p);
      if(len<=0)return -1;
      System.arraycopy(data,(int)p,buf,off,len);
      return len;
    }
    public void close(){closed=true;}
  }
  public static void main(String[] args)throws Exception{
    for(int i=0;i<data.length;i++)data[i]=(byte)(i%199);
    Source source=new Source();
    String url;
    try(LoopbackRangeServer s=new LoopbackRangeServer(source)){
      url=s.url();
      check(url.startsWith("http://127.0.0.1:"),"loopback binding");
      HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
      check(c.getResponseCode()==200,"full request");
      check(Arrays.equals(data,read(c.getInputStream())),"full content");c.disconnect();
      c=(HttpURLConnection)new URL(url).openConnection();c.setRequestProperty("Range","bytes=51-135");
      check(c.getResponseCode()==206,"partial status");
      check("bytes 51-135/100000".equals(c.getHeaderField("Content-Range")),"content range");
      check(Arrays.equals(Arrays.copyOfRange(data,51,136),read(c.getInputStream())),"partial bytes");c.disconnect();
      c=(HttpURLConnection)new URL(url).openConnection();c.setRequestProperty("Range","bytes=-5");
      check(c.getResponseCode()==206,"suffix status");
      check(Arrays.equals(Arrays.copyOfRange(data,data.length-5,data.length),read(c.getInputStream())),"suffix bytes");c.disconnect();
      c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("HEAD");
      check(c.getResponseCode()==200 && c.getContentLength()==data.length,"head");c.disconnect();
      c=(HttpURLConnection)new URL(url).openConnection();c.setRequestProperty("Range","bytes=999999-");
      check(c.getResponseCode()==416,"invalid range");c.disconnect();
      c=(HttpURLConnection)new URL(url.replaceAll("[0-9a-f]{48}$","invalid")).openConnection();
      check(c.getResponseCode()==404,"token protected");c.disconnect();
    }
    check(source.closed,"source closed");
    try{new URL(url).openConnection().getInputStream();throw new AssertionError("closed server must reject");}
    catch(IOException expected){}
    System.out.println("PASS: loopback Range, HEAD, token and lifecycle tests");
  }
}
