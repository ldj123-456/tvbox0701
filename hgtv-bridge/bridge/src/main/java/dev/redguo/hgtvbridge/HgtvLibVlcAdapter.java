package dev.redguo.hgtvbridge;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;
import android.util.Log;
import android.widget.FrameLayout;
import java.io.IOException;
import java.util.ArrayList;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

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
  private View.OnLayoutChangeListener viewportListener;
  private int lastWindowW,lastWindowH;

  public HgtvLibVlcAdapter(Context context,FrameLayout host){
    if(context==null||host==null)throw new IllegalArgumentException("missing player host");
    this.host=host;
    ArrayList<String> opts=new ArrayList<String>();
    opts.add("--no-video-title-show");
    opts.add("--network-caching=350");
    opts.add("--file-caching=350");
    // Prefer A/V sync over retaining late video frames on slow ARMv7 CPUs.
    opts.add("--drop-late-frames");
    opts.add("--skip-frames");
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
    surface=new SurfaceView(host.getContext());
    // Keep SurfaceView MATCH_PARENT for the lifetime of a single episode.
    // Resizing/replacing its native buffer on video format changes caused
    // black-screen regressions on the target Android 4.4 device.
    host.addView(surface,0,new FrameLayout.LayoutParams(-1,-1));
    viewportListener=new View.OnLayoutChangeListener(){
      @Override public void onLayoutChange(View view,int l,int t,int r,int b,
          int oldL,int oldT,int oldR,int oldB){
        if(player==next && generation==current)syncVideoWindow(next);
      }
    };
    host.addOnLayoutChangeListener(viewportListener);
    next.setEventListener(event->ui.post(()->{
      if(generation!=current||player!=next)return;
      if(event.type==MediaPlayer.Event.Vout){
        syncVideoWindow(next);
        if(events!=null)events.onFirstVideo();
      }
      else if(event.type==MediaPlayer.Event.EndReached){if(events!=null)events.onCompleted();}
      else if(event.type==MediaPlayer.Event.EncounteredError){if(events!=null)events.onError("VLC decode or media transport error");}
    }));
    next.getVLCVout().setVideoView(surface);
    // Explicit window geometry fixes portrait video drawn on the left.
    syncVideoWindow(next);
    next.getVLCVout().attachViews();
    host.post(()->{if(player==next && generation==current)syncVideoWindow(next);});
    Media media=new Media(vlc,Uri.parse(server.url()));
    try{
      media.setHWDecoderEnabled(false,false);
      next.setMedia(media);
    }finally{media.release();}
    // Let VLC letterbox portrait material; never force 16:9 stretching.
    next.setAspectRatio(null);
    next.setScale(0f);
    if(speed>0 && speed<=2f)next.setRate(speed);
    next.play();
    if(resumeMs>0)applyInitialSeek(next,current,resumeMs,0);
  }
  /** Notify VLC of the real viewport without changing the SurfaceView layout.
   * Must run on the UI thread, after and during attach, and after every episode.
   */
  private void syncVideoWindow(MediaPlayer target) {
    if(target!=player || surface==null)return;
    int w=host.getWidth(),h=host.getHeight();
    if(w<=0||h<=0)return;
    // Always resend: the first pre-attach window call may not reach the
    // native vout; the post-attach callback must not be skipped.
    lastWindowW=w;lastWindowH=h;
    target.getVLCVout().setWindowSize(w,h);
    Log.i("HGTV-LibVLC","VLC window="+w+"x"+h+" surface=match-parent");
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


  /** Temporary fixed offset control. Milliseconds are converted to VLC microseconds. */
  private long audioOffsetUs;
  public void audioDelayMs(long millis) {
    ensureUi();
    audioOffsetUs=Math.max(-1000,Math.min(1000,millis))*1000;
    if(player!=null)player.setAudioDelay(audioOffsetUs);
  }
  public long audioDelayMs(){return audioOffsetUs/1000;}
  public String diagnostic() {
    return "v4 居中窗口 "+lastWindowW+"x"+lastWindowH+" / 容器 "+
       host.getWidth()+"x"+host.getHeight()+" / 音频偏移 "+audioDelayMs()+"ms";
  }
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
    if(viewportListener!=null){
      host.removeOnLayoutChangeListener(viewportListener);viewportListener=null;
    }
    if(surface!=null){host.removeView(surface);surface=null;}
    lastWindowW=0;lastWindowH=0;
    if(server!=null){try{server.close();}catch(IOException ignored){}server=null;}
  }
  @Override public void close(){ensureUi();if(closed)return;stop();closed=true;vlc.release();}
}
