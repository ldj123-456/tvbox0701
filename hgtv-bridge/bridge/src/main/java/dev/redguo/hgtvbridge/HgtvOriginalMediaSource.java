package dev.redguo.hgtvbridge;

import android.net.Uri;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.Map;

/** Accesses prepared video bytes through the original HGTV 1.2.2.2 H0.s DataSource.
 * Does not replace HGTV's network, authorized media session, catalog, or codec processing.
 * Obfuscated names are specific to verified APK SHA-256 192d22df...c9579ccf.
 */
public final class HgtvOriginalMediaSource implements LoopbackRangeServer.SeekableSource {
  private final Object media, dataSource;
  private final Method open, read, close;
  private final Constructor<?> specCtor;
  private final Field size;
  private long cursor=-1;
  private boolean closed;

  public HgtvOriginalMediaSource(Object preparedVideo) throws IOException {
    try {
      Class<?> remote=Class.forName("E0.p1");
      if(preparedVideo==null||!remote.isInstance(preparedVideo))
        throw new IOException("Original HGTV E0.p1 source required");
      media=preparedVideo;
      Class<?> data=Class.forName("H0.s"), spec=Class.forName("o0.q");
      dataSource=data.getConstructor(remote).newInstance(media);
      open=data.getMethod("h",spec);
      read=data.getMethod("read",byte[].class,int.class,int.class);
      close=data.getMethod("close");
      specCtor=spec.getConstructor(Uri.class,int.class,byte[].class,Map.class,long.class,long.class,String.class,int.class);
      size=remote.getField("e");
      if(size.getLong(media)<=0)throw new IOException("HGTV source not prepared");
    } catch(IOException e){throw e;}
    catch(Exception e){throw new IOException("Original HGTV data source unavailable",e);}
  }
  public synchronized long length() throws IOException {
    if(closed)throw new IOException("Source closed");
    try{return size.getLong(media);}
    catch(Exception e){throw new IOException("Cannot read HGTV stream length",e);}
  }
  public synchronized int read(long position,byte[] bytes,int offset,int count) throws IOException {
    if(position<0||count<0||offset<0||offset>bytes.length-count)
      throw new IOException("Invalid read range");
    if(count==0)return 0;
    long remaining=length()-position;
    if(remaining<=0)return -1;
    try {
      if(cursor!=position){
        close.invoke(dataSource);
        Object spec=specCtor.newInstance(Uri.parse("hgtv://episode/vlc"),
             1,null,Collections.emptyMap(),position,-1L,null,0);
        open.invoke(dataSource,spec);
        cursor=position;
      }
      int n=((Integer)read.invoke(dataSource,bytes,offset,(int)Math.min(remaining,count))).intValue();
      if(n>0)cursor+=n;
      return n;
    }catch(Exception e){
      Throwable c=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;
      throw c instanceof IOException?(IOException)c:new IOException("HGTV media read failed",c);
    }
  }
  public synchronized void close() throws IOException {
    if(closed)return;
    closed=true;
    try{close.invoke(dataSource);}
    catch(Exception e){throw new IOException("HGTV data source close failed",e);}
    // The original HGTV Activity, not this adapter, owns the live E0.p1 session.
  }
}
