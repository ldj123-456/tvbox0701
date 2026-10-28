package dev.redguo.hgtvbridge;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.io.IOException;
import java.util.ArrayList;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

/** Optional software player backend for existing HGTV UI and source pipeline.
 * The upstream client MUST supply playable, authorized random-access media bytes.
 * Keep search, authentication, episode list, settings and media access in HGTV.
 */
public final class HgtvLibVlcAdapter implements AutoCloseable {
  public interface Events{
    void onFirstVideo();
    void onCompleted();
    void onError(String reason);
  }
  private final LibVLC vlc;
  private final FrameLayout host;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private MediaPlayer player;
  private SurfaceView surface;
  private LoopbackRangeServer server;
  private Events events;
  private int generation;
  private boolean closed;
  private int videoW,videoH,visibleW,visibleH,sarN=1,sarD=1;
  private int surfaceW,surfaceH;
  private long audioDelayUs;
  private View.OnLayoutChangeListener onResize;

  public HgtvLibVlcAdapter(Context context,FrameLayout host){
    if(context==null||host==null)throw new IllegalArgumentException("missing player host");
    this.host=host;
    ArrayList<String> opts=new ArrayList<String>();
    opts.add("--no-video-title-show");
    opts.add("--network-caching=750");
    opts.add("--file-caching=750");
    opts.add("--drop-late-frames");
    opts.add("--skip-frames");
    opts.add("--avcodec-fast");
    opts.add("--avcodec-skiploopfilter=2");
    vlc=new LibVLC(context.getApplicationContext(),opts);
  }

  /** Must run on UI thread; the source MUST be prepared and authorized by HGTV. */
  public void play(LoopbackRangeServer.SeekableSource source,long resumeMs,float speed,Events callbacks)
      throws IOException {
    ensureUi();
    if(closed)throw new IllegalStateException("backend closed");
    stop();
    server=new LoopbackRangeServer(source);
    events=callbacks;
    final int current=++generation;
    final MediaPlayer next=new MediaPlayer(vlc);
    player=next;
    host.setBackgroundColor(Color.BLACK);
    surface=new SurfaceView(host.getContext());
    surface.setBackgroundColor(Color.BLACK);
    host.addView(surface,0,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
    onResize=new View.OnLayoutChangeListener(){
      @Override public void onLayoutChange(View v,int l,int t,int r,int b,
                                            int ol,int ot,int orr,int ob){
        if(player==next && generation==current) resize(next);
      }
    };
    host.addOnLayoutChangeListener(onResize);
    next.setEventListener(event->ui.post(()->{
      if(generation!=current||player!=next)return;
      if(event.type==MediaPlayer.Event.Vout){resize(next);if(events!=null)events.onFirstVideo();}
      else if(event.type==MediaPlayer.Event.EndReached){if(events!=null)events.onCompleted();}
      else if(event.type==MediaPlayer.Event.EncounteredError){if(events!=null)events.onError("VLC decode or media transport error");}
    }));
    next.getVLCVout().setVideoView(surface);
    next.getVLCVout().attachViews(new IVLCVout.OnNewVideoLayoutListener(){
      @Override public void onNewVideoLayout(IVLCVout out,int w,int h,int vw,int vh,int sn,int sd){
        ui.post(()->{
          if(player!=next || generation!=current || closed)return;
          videoW=w;videoH=h;visibleW=vw;visibleH=vh;
          sarN=Math.max(1,sn);sarD=Math.max(1,sd);
          resize(next);
          Log.i("HGTV-LibVLC","Video "+w+"x"+h+" visible="+vw+"x"+vh+
                " SAR="+sn+"/"+sd+" fitted="+surfaceW+"x"+surfaceH);
        });
      }
    });
    Media media=new Media(vlc,Uri.parse(server.url()));
    try{
      media.setHWDecoderEnabled(false,false);
      media.addOption(":network-caching=750");
      next.setMedia(media);
    }finally{media.release();}
    next.setScale(0f);
    next.setAspectRatio(null);
    if(speed>0 && speed<=2f)next.setRate(speed);
    host.post(()->{if(player==next && generation==current)resize(next);});
    next.play();
    if(resumeMs>0)applyInitialSeek(next,current,resumeMs,0);
  }
  /** Native portrait dimensions are fitted inside the TV screen.
   * Update VLC's window size every time the host layout changes. */
  private void resize(MediaPlayer target){
    if(player!=target||surface==null)return;
    int w=host.getWidth(),h=host.getHeight();
    if(w<=0||h<=0)return;
    target.getVLCVout().setWindowSize(w,h);
    if(visibleW<=0||visibleH<=0)return;
    double ar=((double)visibleW*sarN)/((double)visibleH*sarD);
    if(ar<0.05||ar>20)return;
    int fw=w,fh=(int)Math.round(w/ar);
    if(fh>h){fh=h;fw=(int)Math.round(h*ar);}
    fw=Math.max(1,Math.min(w,fw));fh=Math.max(1,Math.min(h,fh));
    if(fw!=surfaceW||fh!=surfaceH){
      surface.setLayoutParams(new FrameLayout.LayoutParams(fw,fh,Gravity.CENTER));
      surfaceW=fw;surfaceH=fh;
    }
  }
  /** Fixed audio offset diagnostic (milliseconds); not a cure for video CPU overload. */
  public void audioDelayMs(long millis){
    ensureUi();audioDelayUs=Math.max(-1000,Math.min(1000,millis))*1000;
    if(player!=null)player.setAudioDelay(audioDelayUs);
  }
  public long audioDelayMs(){return audioDelayUs/1000;}
  public String diagnostic(){
    return "源 "+videoW+"x"+videoH+"，屏幕 "+host.getWidth()+"x"+host.getHeight()+
           "，输出 "+surfaceW+"x"+surfaceH+"，音频 "+audioDelayMs()+"ms";
  }
  /** Retry initial resume as soon as the demux is seekable, bounded to 8 seconds. */
  private void applyInitialSeek(final MediaPlayer target,final int ticket,final long position,
                                final int tries){
    ui.postDelayed(()->{
      if(closed||ticket!=generation||player!=target)return;
      if(target.isSeekable()){target.setTime(position);return;}
      if(tries<31)applyInitialSeek(target,ticket,position,tries+1);
    },250);
  }
  private static void ensureUi(){
    if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread only");
  }
  public void pause(){ensureUi();if(player!=null)player.pause();}
  public void resume(){ensureUi();if(player!=null)player.play();}
  public boolean isPlaying(){return player!=null&&player.isPlaying();}
  public long positionMs(){return player==null?0:Math.max(0,player.getTime());}
  public long durationMs(){return player==null?0:Math.max(0,player.getLength());}
  public void seek(long position){ensureUi();if(player!=null&&player.isSeekable())player.setTime(Math.max(0,position));}
  public void speed(float value){ensureUi();if(player!=null&&value>0&&value<=2f)player.setRate(value);}

  /** Stop player and local source every time the episode changes. */
  public void stop(){
    ensureUi();
    generation++;
    events=null;
    MediaPlayer old=player;player=null;
    if(old!=null){
      try{old.setEventListener(null);}catch(RuntimeException ignored){}
      try{old.stop();}catch(RuntimeException ignored){}
      try{old.getVLCVout().detachViews();}catch(RuntimeException ignored){}
      try{old.release();}catch(RuntimeException ignored){}
    }
    if(onResize!=null){host.removeOnLayoutChangeListener(onResize);onResize=null;}
    if(surface!=null){host.removeView(surface);surface=null;}
    surfaceW=0;surfaceH=0;visibleW=0;visibleH=0;videoW=0;videoH=0;audioDelayUs=0;
    if(server!=null){try{server.close();}catch(IOException ignored){}server=null;}
  }
  @Override public void close(){ensureUi();if(closed)return;stop();closed=true;vlc.release();}
}
