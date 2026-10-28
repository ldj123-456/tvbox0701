package dev.redguo.hgtvbridge;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
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

  public HgtvLibVlcAdapter(Context context,FrameLayout host){
    if(context==null||host==null)throw new IllegalArgumentException("missing player host");
    this.host=host;
    ArrayList<String> opts=new ArrayList<String>();
    opts.add("--no-video-title-show");
    opts.add("--network-caching=350");
    opts.add("--file-caching=350");
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
    host.addView(surface,0,new FrameLayout.LayoutParams(-1,-1));
    next.setEventListener(event->ui.post(()->{
      if(generation!=current||player!=next)return;
      if(event.type==MediaPlayer.Event.Vout){if(events!=null)events.onFirstVideo();}
      else if(event.type==MediaPlayer.Event.EndReached){if(events!=null)events.onCompleted();}
      else if(event.type==MediaPlayer.Event.EncounteredError){if(events!=null)events.onError("VLC decode or media transport error");}
    }));
    next.getVLCVout().setVideoView(surface);
    next.getVLCVout().attachViews();
    Media media=new Media(vlc,Uri.parse(server.url()));
    try{
      media.setHWDecoderEnabled(false,false);
      next.setMedia(media);
    }finally{media.release();}
    if(speed>0 && speed<=2f)next.setRate(speed);
    next.play();
    if(resumeMs>0)applyInitialSeek(next,current,resumeMs,0);
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
    return "v3 视频兼容模式（v1 渲染） / 容器 "+
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
    if(surface!=null){host.removeView(surface);surface=null;}
    if(server!=null){try{server.close();}catch(IOException ignored){}server=null;}
  }
  @Override public void close(){ensureUi();if(closed)return;stop();closed=true;vlc.release();}
}
