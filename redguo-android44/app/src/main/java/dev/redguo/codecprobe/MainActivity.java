package dev.redguo.codecprobe;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
  private LibVLC vlc;
  private MediaPlayer player;
  private SurfaceView surface;
  private TextView logText;
  private boolean soft = true, surfaceReady = false;
  private String current = "";
  private long started;
  private final StringBuilder logs = new StringBuilder();

  private void log(String message) {
    logs.append("[").append(SystemClock.elapsedRealtime()/1000).append("s] ").append(message).append("\n");
    if (logs.length()>13000) logs.delete(0,6000);
    if (logText!=null) logText.setText("最近结果： " + message + "\n按“完整日志”可拍照记录");
  }
  private Button makeButton(String label, LinearLayout group, Runnable action) {
    Button b=new Button(this); b.setText(label); b.setTextSize(13);
    group.addView(b,new LinearLayout.LayoutParams(0,-1,1f));
    b.setOnClickListener(view -> action.run());
    return b;
  }
  private LinearLayout row(int h) {
    LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
    r.setMinimumHeight(h); return r;
  }
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(10,8,10,8); root.setBackgroundColor(Color.rgb(15,18,26));
    TextView title=new TextView(this); title.setText("红果轻量TV · Android 4.4 解码自检"); title.setTextColor(Color.WHITE);
    title.setTextSize(20); root.addView(title,new LinearLayout.LayoutParams(-1,48));
    LinearLayout tests=row(50);
    makeButton("H.264 360P",tests,()->playSample("h264_360.mp4"));
    makeButton("HEVC 360P",tests,()->playSample("hevc_360.mp4"));
    makeButton("HEVC 720P",tests,()->playSample("hevc_720.mp4"));
    root.addView(tests,new LinearLayout.LayoutParams(-1,60));
    LinearLayout actions=row(50);
    final Button mode=makeButton("当前：软件解码",actions,()->{});
    mode.setOnClickListener(view -> {soft=!soft;mode.setText(soft?"当前：软件解码":"当前：硬解尝试");log("解码模式切换，重新选择视频测试");});
    makeButton("完整日志",actions,()->showDetails());
    makeButton("暂停/继续",actions,()->toggle());
    root.addView(actions,new LinearLayout.LayoutParams(-1,60));
    surface=new SurfaceView(this); surface.getHolder().addCallback(this);
    root.addView(surface,new LinearLayout.LayoutParams(-1,0,1f));
    logText=new TextView(this); logText.setTextColor(Color.WHITE);logText.setTextSize(16);
    root.addView(logText,new LinearLayout.LayoutParams(-1,100));
    setContentView(root);
    log("已启动；API "+Build.VERSION.SDK_INT+"，CPU "+Build.CPU_ABI);
  }
  private void showDetails() {
    StringBuilder report=new StringBuilder();
    report.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
    report.append("CPU ABI: ").append(Build.CPU_ABI).append("\nModel: ").append(Build.MODEL).append("\n");
    boolean any=false;
    try {
      for(int i=0;i<MediaCodecList.getCodecCount();i++) {
        MediaCodecInfo info=MediaCodecList.getCodecInfoAt(i);
        if(info.isEncoder())continue;
        for(String mime:info.getSupportedTypes())if("video/hevc".equalsIgnoreCase(mime)){
          report.append("HEVC codec: ").append(info.getName()).append("\n"); any=true;
        }
      }
    }catch(Throwable t){report.append("Codec probe failed: ").append(t.getClass().getSimpleName()).append("\n");}
    if(!any)report.append("系统未公布 HEVC 解码器（不代表 VLC 软解不可用）\n");
    report.append("\n测试日志：\n").append(logs);
    ScrollView scroller=new ScrollView(this);
    TextView text=new TextView(this); text.setText(report);text.setTextSize(16);text.setPadding(14,14,14,14);
    scroller.addView(text);
    new AlertDialog.Builder(this).setTitle("设备与播放器诊断").setView(scroller).setPositiveButton("关闭",null).show();
  }
  private void playSample(String sample) {
    if(!surfaceReady){log("视频表面尚未就绪");return;}
    try {
      File file=new File(getCacheDir(),sample);
      if(!file.exists()||file.length()<500) {
        InputStream input=getAssets().open(sample);
        FileOutputStream output=new FileOutputStream(file);
        try {
          byte[] buffer=new byte[32768]; int read;
          while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
        }finally{input.close();output.close();}
      }
      releasePlayer();
      ArrayList<String> options=new ArrayList<String>();
      options.add("--file-caching=300");
      vlc=new LibVLC(this,options);
      MediaPlayer candidate=new MediaPlayer(vlc); player=candidate;
      current=sample; started=SystemClock.elapsedRealtime();
      candidate.setEventListener(event -> runOnUiThread(() -> {
        if(candidate!=player)return;
        if(event.type==MediaPlayer.Event.Playing)log(current+" 已启动，观察画面");
        else if(event.type==MediaPlayer.Event.Vout)log(current+" 视频输出建立，耗时 "+(SystemClock.elapsedRealtime()-started)+"ms");
        else if(event.type==MediaPlayer.Event.EncounteredError)log(current+" 播放错误，模式="+(soft?"软解":"硬解"));
        else if(event.type==MediaPlayer.Event.EndReached)log(current+" 完整播放结束");
      }));
      Media media=new Media(vlc,Uri.fromFile(file));
      try { media.setHWDecoderEnabled(!soft,false); candidate.setMedia(media); }
      finally {media.release();}
      candidate.getVLCVout().setVideoView(surface);
      candidate.getVLCVout().attachViews();
      log("测试 "+sample+"，"+(soft?"软件解码":"硬件解码尝试"));
      candidate.play();
    } catch(Throwable t) {
      log("初始化失败："+t.getClass().getSimpleName()+" "+t.getMessage());
      releasePlayer();
    }
  }
  private void toggle() {
    if(player==null)return;
    try {if(player.isPlaying()) player.pause(); else player.play();}
    catch(Throwable t){log("暂停/恢复失败 "+t.getClass().getSimpleName());}
  }
  private void releasePlayer() {
    MediaPlayer old=player;player=null;
    if(old!=null){
      try{old.setEventListener(null);}catch(Throwable ignored){}
      try{old.stop();}catch(Throwable ignored){}
      try{old.getVLCVout().detachViews();}catch(Throwable ignored){}
      try{old.release();}catch(Throwable ignored){}
    }
    if(vlc!=null){try{vlc.release();}catch(Throwable ignored){}vlc=null;}
  }
  @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;}
  @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){surfaceReady=true;}
  @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;releasePlayer();}
  @Override protected void onPause(){super.onPause();if(player!=null)player.pause();}
  @Override protected void onDestroy(){releasePlayer();super.onDestroy();}
}
