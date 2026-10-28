package dev.redguo.hgtvbridge;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Method;

/** Experimental fallback for HGTV 1.2.2.2 decoder error 4005 only.
 * Keeps the original HGTV app, original episode metadata, HTTP client,
 * session authorization and processed-media DataSource.
 * Temporary full-screen controls are only for first on-box proof of playback.
 */
public final class HgtvPlaybackFallback {
  private static HgtvPlaybackFallback active;
  private final Activity activity;
  private final FrameLayout overlay;
  private final TextView message;
  private final HgtvLibVlcAdapter vlc;

  private HgtvPlaybackFallback(Activity a,Object media,long position) throws Exception {
    activity=a;
    overlay=new FrameLayout(a);
    overlay.setFocusableInTouchMode(true);
    overlay.setBackgroundColor(Color.BLACK);
    a.addContentView(overlay,new FrameLayout.LayoutParams(-1,-1));
    message=new TextView(a);
    message.setTextColor(Color.WHITE);
    message.setTextSize(15);
    message.setPadding(14,12,14,12);
    message.setBackgroundColor(0xAA000000);
    message.setText("HGTV 原内容接口 · LibVLC 软件播放\n确定：暂停/继续 · 左右：快进退 · 上下：切集 · 返回：退出");
    overlay.addView(message,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    vlc=new HgtvLibVlcAdapter(a,overlay);
    try{
      vlc.play(new HgtvOriginalMediaSource(media),position,1.0f,new HgtvLibVlcAdapter.Events(){
        public void onFirstVideo(){
          if(active==HgtvPlaybackFallback.this)
            message.postDelayed(()->{if(active==HgtvPlaybackFallback.this)message.setVisibility(View.GONE);},5000);
        }
        public void onCompleted(){if(active==HgtvPlaybackFallback.this)next(1);}
        public void onError(String e){message.setVisibility(View.VISIBLE);message.setText("LibVLC 读取失败："+e+"\n返回键退出");}
      });
      overlay.requestFocus();
    }catch(Exception e){try{vlc.close();}catch(Exception ignored){}removeView();throw e;}
  }
  private void removeView(){
    if(overlay.getParent() instanceof ViewGroup)
      ((ViewGroup)overlay.getParent()).removeView(overlay);
  }
  private static Object field(Object target,String name)throws Exception{
    return target.getClass().getField(name).get(target);
  }
  private static long longField(Object target,String name){
    try{return target.getClass().getField(name).getLong(target);}
    catch(Exception ignored){return 0;}
  }
  private static boolean isDecoder4005(Throwable error){
    for(Throwable e=error;e!=null;e=e.getCause()){
      if("x.w0".equals(e.getClass().getName())){
        try{return e.getClass().getField("a").getInt(e)==4005;}
        catch(Exception ignored){return false;}
      }
    }
    return false;
  }
  /** This is invoked before original K0() error handling; false preserves old behavior. */
  public static synchronized boolean maybeStart(Object owner,Throwable error){
    if(!(owner instanceof Activity)||!isDecoder4005(error))return false;
    try{
      Object prepared=field(owner,"c3");
      if(prepared==null)return false;
      closeActive();
      HgtvPlaybackFallback fallback=new HgtvPlaybackFallback((Activity)owner,prepared,longField(owner,"p1"));
      active=fallback;
      Log.i("HGTV-LibVLC","Using original HGTV media source in LibVLC");
      return true;
    }catch(Throwable e){
      Log.e("HGTV-LibVLC","Cannot start fallback",e);
      return false;
    }
  }
  public static synchronized long positionOrNegative(Object owner){
    return active!=null&&active.activity==owner?active.vlc.positionMs():-1L;
  }
  /** Intercepts keys only while fallback overlay is visible. */
  public static synchronized boolean onKey(Object owner,KeyEvent event){
    if(active==null||active.activity!=owner||event.getAction()!=KeyEvent.ACTION_DOWN)return false;
    switch(event.getKeyCode()){
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        if(active.vlc.isPlaying())active.vlc.pause();else active.vlc.resume();return true;
      case KeyEvent.KEYCODE_DPAD_LEFT:
        active.vlc.seek(active.vlc.positionMs()-10000);return true;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        active.vlc.seek(active.vlc.positionMs()+10000);return true;
      case KeyEvent.KEYCODE_DPAD_UP:active.next(1);return true;
      case KeyEvent.KEYCODE_DPAD_DOWN:active.next(-1);return true;
      case KeyEvent.KEYCODE_BACK:closeActive();return false;
      default:return false;
    }
  }
  private void next(int direction){
    Activity original=activity;
    closeActive();
    try{
      Class<?> owner=Class.forName("com.hgtv.star.MainActivity");
      Method skip=Class.forName("C0.u0").getMethod("H1",owner,int.class);
      skip.invoke(null,original,direction);
    }catch(Exception e){Log.e("HGTV-LibVLC","Cannot switch HGTV original episode",e);}
  }
  public static synchronized void closeActive(){
    HgtvPlaybackFallback old=active;active=null;
    if(old==null)return;
    try{old.vlc.close();}catch(Throwable e){Log.w("HGTV-LibVLC","Close error",e);}
    old.removeView();
  }
}
