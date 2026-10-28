package dev.redguo.codecprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

/**
 * Android 4.4 / ARM32 codec DIAGNOSTIC ONLY.
 * No third-party catalog, authentication, stream extraction or network traffic.
 * Uses self-created local 12 s H.264 / HEVC samples for controlled tests.
 */
public final class MainActivity extends Activity implements SurfaceHolder.Callback {
  private SurfaceView surface;
  private TextView summary;
  private StringBuilder logs=new StringBuilder();
  private LibVLC vlc;
  private MediaPlayer vlcPlayer;
  private volatile Thread codecWorker;
  private volatile boolean codecBusy;
  private volatile boolean surfaceReady;
  private volatile boolean stopped;
  private long vlcStart;
  private String vlcSample;
  private int testSerial;
  private final ArrayList<Button> buttons=new ArrayList<Button>();

  private void log(String line){
    final String record=String.format(Locale.US,"[%ds] %s",SystemClock.elapsedRealtime()/1000,line);
    logs.append(record).append('\n');
    if(logs.length()>20000)logs.delete(0,6000);
    if(summary!=null)summary.setText(record+"\n按“设备报告”查看解码器与结果");
  }
  private LinearLayout row(){
    LinearLayout r=new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    r.setMinimumHeight(64);
    return r;
  }
  private void button(LinearLayout parent,String name,final Runnable job){
    Button b=new Button(this);
    b.setText(name);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setMinHeight(58);
    parent.addView(b,new LinearLayout.LayoutParams(0,62,1));
    b.setOnClickListener(v->job.run());
    buttons.add(b);
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    LinearLayout root=new LinearLayout(this);
    root.setBackgroundColor(0xff111722);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(8,8,8,8);
    TextView heading=new TextView(this);
    heading.setText("EC6108 HEVC 硬解能力诊断 · Android 4.4");
    heading.setTextColor(Color.WHITE);
    heading.setTextSize(21);
    root.addView(heading,new LinearLayout.LayoutParams(-1,50));
    LinearLayout first=row();
    button(first,"查看芯片与解码器",()->showReport());
    button(first,"系统硬解 HEVC 720P",()->startCodec("video/hevc","hevc_720.mp4"));
    button(first,"系统硬解 H264 720P",()->startCodec("video/avc","h264_720.mp4"));
    root.addView(first,new LinearLayout.LayoutParams(-1,65));
    LinearLayout second=row();
    button(second,"VLC软解 HEVC 480P",()->startVlc("hevc_480.mp4",false));
    button(second,"VLC软解 HEVC 720P",()->startVlc("hevc_720.mp4",false));
    button(second,"VLC尝试硬解 HEVC 720P",()->startVlc("hevc_720.mp4",true));
    root.addView(second,new LinearLayout.LayoutParams(-1,65));
    LinearLayout third=row();
    button(third,"VLC软解 H264 720P",()->startVlc("h264_720.mp4",false));
    button(third,"停止/释放",()->stopAll());
    button(third,"完整测试记录",()->showLog());
    root.addView(third,new LinearLayout.LayoutParams(-1,65));
    TextView hint=new TextView(this);
    hint.setText("硬解按钮只尝试系统公布的厂商解码器，找不到时不会偷换软件解码。\nVLC“尝试硬解”也可能自动回退，不能单凭有画面认定硬解成功。");
    hint.setTextColor(0xffd5dee9);
    hint.setTextSize(12);
    root.addView(hint,new LinearLayout.LayoutParams(-1,57));
    surface=new SurfaceView(this);
    surface.getHolder().addCallback(this);
    surface.setBackgroundColor(Color.BLACK);
    root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
    summary=new TextView(this);
    summary.setTextColor(Color.WHITE);
    summary.setTextSize(15);
    summary.setPadding(6,8,6,5);
    root.addView(summary,new LinearLayout.LayoutParams(-1,75));
    setContentView(root);
    stopped=false;
    log("准备就绪。型号="+Build.MODEL+" Android "+Build.VERSION.RELEASE+
        " API"+Build.VERSION.SDK_INT+" ABI "+Build.CPU_ABI);
  }

  private static boolean looksSystemSoftware(String name){
    String n=name.toLowerCase(Locale.US);
    return n.startsWith("omx.google.")||n.startsWith("c2.android.")||
      n.startsWith("omx.ffmpeg.")||n.startsWith("c2.google.");
  }
  private String codecNames(String mime){
    StringBuilder out=new StringBuilder();
    boolean found=false;
    try{
      for(int i=0;i<MediaCodecList.getCodecCount();i++){
        MediaCodecInfo info=MediaCodecList.getCodecInfoAt(i);
        if(info.isEncoder())continue;
        for(String type:info.getSupportedTypes()){
          if(type.equalsIgnoreCase(mime)){
            found=true;
            out.append("\n  ").append(info.getName())
              .append(looksSystemSoftware(info.getName())?" [系统软件]":" [厂商候选]");
          }
        }
      }
    }catch(Throwable e){out.append("\n  枚举失败 ").append(e.getClass().getSimpleName())
      .append(" ").append(e.getMessage());}
    if(!found)out.append("\n  未公布该格式的解码器");
    return out.toString();
  }
  private String pickVendorCodec(String mime){
    try{
      for(int i=0;i<MediaCodecList.getCodecCount();i++){
        MediaCodecInfo info=MediaCodecList.getCodecInfoAt(i);
        if(info.isEncoder()||looksSystemSoftware(info.getName()))continue;
        for(String type:info.getSupportedTypes())
          if(mime.equalsIgnoreCase(type))return info.getName();
      }
    }catch(Throwable e){log("枚举解码器异常："+e.toString());}
    return null;
  }
  private String deviceReport(){
    StringBuilder b=new StringBuilder();
    b.append("设备：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
    .append("\nAndroid ").append(Build.VERSION.RELEASE)
    .append(" API ").append(Build.VERSION.SDK_INT)
    .append("\n硬件：").append(Build.HARDWARE).append(" / ").append(Build.BOARD)
    .append("\nABI：").append(Build.CPU_ABI)
    .append("\n\nMediaCodec HEVC:").append(codecNames("video/hevc"))
    .append("\n\nMediaCodec H.264:").append(codecNames("video/avc"))
    .append("\n\n注意：厂商解码器“存在”不代表一定能启动；必须运行实际硬解样片。");
    return b.toString();
  }
  private void dialog(String title,String content){
    ScrollView scroller=new ScrollView(this);
    TextView text=new TextView(this);
    text.setText(content);
    text.setTextSize(16);
    text.setPadding(18,18,18,18);
    scroller.addView(text);
    new AlertDialog.Builder(this).setTitle(title).setView(scroller)
      .setPositiveButton("关闭",null).show();
  }
  private void showReport(){dialog("解码器列表",deviceReport());}
  private void showLog(){dialog("设备能力与实测结果",deviceReport()+"\n\n"+logs.toString());}

  private File extractAsset(String name)throws Exception{
    File dst=new File(getCacheDir(),name);
    if(dst.exists()&&dst.length()>4096)return dst;
    InputStream in=getAssets().open(name);
    FileOutputStream out=new FileOutputStream(dst);
    try{byte[] bytes=new byte[32768];int n;
      while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
    finally{try{in.close();}finally{out.close();}}
    return dst;
  }
  private void enableButtons(boolean enabled){
    for(Button b:buttons)b.setEnabled(enabled);
  }
  private void stopVlc(){
    MediaPlayer p=vlcPlayer;vlcPlayer=null;
    if(p!=null){
      try{p.setEventListener(null);}catch(Throwable ignored){}
      try{p.stop();}catch(Throwable ignored){}
      try{p.getVLCVout().detachViews();}catch(Throwable ignored){}
      try{p.release();}catch(Throwable ignored){}
    }
    if(vlc!=null){try{vlc.release();}catch(Throwable ignored){} vlc=null;}
  }
  private void stopAll(){
    ++testSerial;
    Thread task=codecWorker;
    if(task!=null)task.interrupt();
    stopVlc();
    log(codecBusy?"已要求停止系统硬解测试":"已停止播放器");
  }

  private void startVlc(String sample,boolean allowHardware){
    if(codecBusy){log("系统硬解测试进行中，请先完成测试");return;}
    if(!surfaceReady){log("屏幕输出尚未准备好");return;}
    stopVlc();
    try{
      File local=extractAsset(sample);
      ArrayList<String> opts=new ArrayList<String>();
      opts.add("--no-video-title-show");
      opts.add("--file-caching=300");
      vlc=new LibVLC(getApplicationContext(),opts);
      final MediaPlayer candidate=new MediaPlayer(vlc);
      vlcPlayer=candidate;vlcSample=sample;vlcStart=SystemClock.elapsedRealtime();
      candidate.setEventListener(event->runOnUiThread(()->{
        if(candidate!=vlcPlayer)return;
        if(event.type==MediaPlayer.Event.Playing)log(sample+" 已开始 VLC "+(allowHardware?"硬解尝试":"软件解码"));
        else if(event.type==MediaPlayer.Event.Vout)
          log(sample+" 有画面，耗时 "+(SystemClock.elapsedRealtime()-vlcStart)+"ms");
        else if(event.type==MediaPlayer.Event.EndReached)
          log(sample+" 已播完，VLC 运行 "+(SystemClock.elapsedRealtime()-vlcStart)+"ms");
        else if(event.type==MediaPlayer.Event.EncounteredError)
          log(sample+" VLC播放错误（硬解="+allowHardware+"）");
      }));
      candidate.getVLCVout().setVideoView(surface);
      candidate.getVLCVout().attachViews();
      Media video=new Media(vlc,Uri.fromFile(local));
      try{
        // A hint only: VLC may fall back when firmware MediaCodec cannot decode.
        video.setHWDecoderEnabled(allowHardware,false);
        candidate.setMedia(video);
      }finally{video.release();}
      log("VLC "+(allowHardware?"硬件解码尝试（并非确定硬解）":"明确关闭硬解")+
          "，片段 "+sample+"（12秒24fps）");
      candidate.play();
    }catch(Throwable t){
      log("VLC 初始化失败："+t.getClass().getSimpleName()+" "+t.getMessage());
      stopVlc();
    }
  }

  private void startCodec(final String mime,final String sample){
    if(codecBusy){log("MediaCodec 测试正在运行");return;}
    if(!surfaceReady){log("视频输出窗口尚未就绪");return;}
    final String vendor=pickVendorCodec(mime);
    if(vendor==null){
      log("系统未公布厂商 "+mime+" 解码器。不能把软件解码当作硬解。");
      dialog("硬件解码不可直接调用",
          "系统 MediaCodecList 未公布厂商 "+mime+" 解码器。\n\n"+
          "这不排除芯片本身具备硬解模块；可能是 Android 4.4 固件没有开放标准接口。\n\n"+
          deviceReport());return;
    }
    stopVlc();
    codecBusy=true;
    final int serial=++testSerial;
    log("开始硬解实测："+vendor+" / "+sample);
    Thread worker=new Thread(()->runCodecWorker(serial,mime,sample,vendor),
                             "hardware-codec-test");
    codecWorker=worker;
    worker.start();
  }

  private void runCodecWorker(int serial,String mime,String filename,String codecName){
    MediaExtractor extractor=null;
    MediaCodec codec=null;
    int frames=0,late=0;
    long start=SystemClock.elapsedRealtime(),maxLag=0,firstPts=-1,lastPts=0;
    String failure=null;
    try{
      File video=extractAsset(filename);
      extractor=new MediaExtractor();
      extractor.setDataSource(video.getAbsolutePath());
      int track=-1;MediaFormat format=null;
      for(int i=0;i<extractor.getTrackCount();i++){
        MediaFormat f=extractor.getTrackFormat(i);
        if(mime.equalsIgnoreCase(f.getString(MediaFormat.KEY_MIME))){track=i;format=f;break;}
      }
      if(track<0)throw new Exception("样片没有 "+mime+" 轨道");
      extractor.selectTrack(track);
      Surface display=surface.getHolder().getSurface();
      if(display==null||!display.isValid())throw new Exception("Surface 无效");
      codec=MediaCodec.createByCodecName(codecName);
      codec.configure(format,display,null,0);
      codec.start();
      final ByteBuffer[] inputs=codec.getInputBuffers();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      boolean inputEnded=false,outputEnded=false;
      long began=SystemClock.elapsedRealtime();
      while(!outputEnded && serial==testSerial && !Thread.currentThread().isInterrupted()){
        if(SystemClock.elapsedRealtime()-began>35000)throw new Exception("超过35秒未完成测试");
        if(!inputEnded){
          int index=codec.dequeueInputBuffer(30000);
          if(index>=0){
            ByteBuffer buf=inputs[index];buf.clear();
            int n=extractor.readSampleData(buf,0);
            if(n<0){
              codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputEnded=true;
            }else{
              long pts=extractor.getSampleTime();
              codec.queueInputBuffer(index,0,n,Math.max(0,pts),0);
              extractor.advance();
            }
          }
        }
        int output=codec.dequeueOutputBuffer(info,30000);
        if(output>=0){
          if(info.size>0){
            if(firstPts<0)firstPts=info.presentationTimeUs;
            long idealMs=Math.max(0,(info.presentationTimeUs-firstPts)/1000);
            long clock=SystemClock.elapsedRealtime()-began;
            if(clock<idealMs){
              long pause=Math.min(100,idealMs-clock);
              if(pause>0)Thread.sleep(pause);
            }else{
              long lag=clock-idealMs;
              maxLag=Math.max(maxLag,lag);
              if(lag>150)late++;
            }
            lastPts=info.presentationTimeUs;
            frames++;
          }
          codec.releaseOutputBuffer(output,true);
          if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)outputEnded=true;
        }
      }
      if(serial!=testSerial)throw new InterruptedException("用户停止");
      final int done=frames,delayed=late;
      final long elapsed=SystemClock.elapsedRealtime()-began;
      final long duration=Math.max(0,(lastPts-firstPts)/1000);
      final long lag=maxLag;
      runOnUiThread(()->log(codecName+" 硬解成功；帧数 "+done+
          "，视频 "+duration+"ms，耗时 "+elapsed+"ms，延迟帧 "+delayed+
          "，最大滞后 "+lag+"ms"));
    }catch(Throwable e){
      failure=e.getClass().getSimpleName()+": "+e.getMessage();
      final String message=failure;
      runOnUiThread(()->log(codecName+" 硬解失败："+message));
    }finally{
      if(codec!=null){
        try{codec.stop();}catch(Throwable ignored){}
        try{codec.release();}catch(Throwable ignored){}
      }
      if(extractor!=null)try{extractor.release();}catch(Throwable ignored){}
      runOnUiThread(()->{
        codecBusy=false;
        codecWorker=null;
        log("系统硬解测试结束；可选择其他测试项");
      });
    }
  }
  @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;}
  @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){surfaceReady=true;}
  @Override public void surfaceDestroyed(SurfaceHolder h){
    surfaceReady=false;stopAll();
  }
  @Override protected void onPause(){super.onPause();stopAll();}
  @Override protected void onDestroy(){stopped=true;stopAll();super.onDestroy();}
}
