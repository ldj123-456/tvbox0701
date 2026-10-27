package dev.redguo.codecprobe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.HttpsURLConnection;

/**
 * Public website catalog inspection only. No authentication, playback signing,
 * encrypted-media handling or content decryption occurs here.
 * All requests are HTTPS to the official homepage domain.
 */
final class HongguoCatalog {
  static final String ROOT = "https://hongguoduanju.com";
  static final class Series {
    final String id, name;
    final int episodeCount;
    Series(String id, String name, int count) {
      this.id=id; this.name=name; this.episodeCount=count;
    }
    public String toString() { return name + (episodeCount>0?" · "+episodeCount+" 集":""); }
  }
  static final class Detail {
    final String name;
    final List<String> episodeIds;
    Detail(String name, List<String> episodeIds) {
      this.name=name; this.episodeIds=episodeIds;
    }
  }

  List<Series> home() throws Exception {
    // Use the website's published recommendations; avoid separate scraping hosts.
    return decodeCatalog(read(ROOT+"/"), 50);
  }
  List<Series> search(String keyword) throws Exception {
    if(keyword==null || keyword.trim().length()==0 || keyword.length()>60) {
      throw new IllegalArgumentException("请输入 1-60 字的剧名");
    }
    return decodeCatalog(read(ROOT+"/search/"+URLEncoder.encode(keyword,"UTF-8")+"?page=1"), 50);
  }
  Detail detail(String seriesId) throws Exception {
    if(seriesId==null || !seriesId.matches("[0-9]{8,30}"))throw new IllegalArgumentException("剧集编号无效");
    JSONObject root=router(read(ROOT+"/detail?series_id="+seriesId));
    JSONObject loader=root.optJSONObject("loaderData");
    JSONObject page=loader==null?null:loader.optJSONObject("detail_page");
    JSONObject info=page==null?null:page.optJSONObject("seriesDetail");
    if(info==null)throw new IllegalStateException("官网详情结构发生变化");
    JSONArray ids=info.optJSONArray("vid_list");
    if(ids==null || ids.length()==0)throw new IllegalStateException("详情页未返回分集编号");
    List<String> result=new ArrayList<String>();
    for(int i=0;i<ids.length()&&i<3000;i++){
      String id=ids.optString(i,"");
      if(id.matches("[0-9]{6,32}"))result.add(id);
    }
    if(result.isEmpty())throw new IllegalStateException("分集编号格式不符合预期");
    String title=name(info);
    if(title.length()==0)title="剧集 "+seriesId;
    return new Detail(title,result);
  }
  private static String name(JSONObject row) {
    String[] keys={"series_name","series_title","title","name","cell_name"};
    for(String key:keys) {
      String value=row.optString(key,"").trim();
      if(value.length()>0 && value.length()<200)return value;
    }
    return "";
  }
  private static String read(String uri) throws Exception {
    HttpsURLConnection con=(HttpsURLConnection)new URL(uri).openConnection();
    // Bundled modern TLS implementation on API 19, with system trust and host checks.
    con.setSSLSocketFactory(ModernTls.factory());
    con.setInstanceFollowRedirects(false);
    con.setConnectTimeout(12000); con.setReadTimeout(15000);
    con.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 4.4.2; zh-CN) AppleWebKit/537.36 Mobile Safari/537.36");
    con.setRequestProperty("Accept","text/html,application/json");
    con.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9");
    con.setRequestProperty("Accept-Encoding","identity");
    try {
      int status=con.getResponseCode();
      if(status!=200)throw new IllegalStateException("官网响应 HTTP "+status);
      InputStream in=con.getInputStream();
      try {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))>=0) {
          out.write(buf,0,n);
          if(out.size()>6*1024*1024)throw new IllegalStateException("内容页超过 6MB");
        }
        return new String(out.toByteArray(),"UTF-8");
      } finally { in.close(); }
    } catch(Exception error) {
      ModernTls.noteFailure(error);
      throw error;
    } finally {con.disconnect();}
  }
  private static JSONObject router(String html) throws Exception {
    int idx=html.indexOf("_ROUTER_DATA");
    if(idx<0)throw new IllegalStateException("官网未返回 _ROUTER_DATA");
    int first=html.indexOf('{',idx);
    if(first<0)throw new IllegalStateException("页面不含 JSON 数据");
    boolean quoted=false,escaped=false;int depth=0;
    for(int i=first;i<html.length();i++) {
      char c=html.charAt(i);
      if(quoted) {
        if(escaped)escaped=false;
        else if(c=='\\')escaped=true;
        else if(c=='"')quoted=false;
      } else {
        if(c=='"')quoted=true;
        else if(c=='{'||c=='[')depth++;
        else if(c=='}'||c==']') {
          depth--;
          if(depth==0)return new JSONObject(html.substring(first,i+1));
        }
      }
    }
    throw new IllegalStateException("官网嵌入数据不完整");
  }
  static List<Series> decodeCatalog(String html,int limit) throws Exception {
    JSONObject json=router(html);
    ArrayList<Series> result=new ArrayList<Series>();
    HashSet<String> seen=new HashSet<String>();
    JSONObject loader=json.optJSONObject("loaderData");
    collect(loader==null?json:loader,0,result,seen,limit);
    if(result.isEmpty())throw new IllegalStateException("官网目录格式已变化，未识别到剧集");
    return result;
  }
  private static void collect(Object node,int depth,List<Series> out,Set<String> seen,int limit) {
    if(depth>18||out.size()>=limit||node==null)return;
    if(node instanceof JSONObject) {
      JSONObject o=(JSONObject)node;
      JSONObject inner=o.optJSONObject("video_data");
      if(inner!=null)collect(inner,depth+1,out,seen,limit);
      String id=o.optString("series_id","");
      String title=name(o);
      if(id.matches("[0-9]{8,30}")&&title.length()>0&&seen.add(id)) {
        int count=o.optInt("episode_cnt",o.optJSONObject("series_episode_info")==null?0:o.optJSONObject("series_episode_info").optInt("episode_cnt",0));
        out.add(new Series(id,title,count));
        if(out.size()>=limit)return;
      }
      java.util.Iterator<String> keys=o.keys();
      while(keys.hasNext()&&out.size()<limit) {
        String key=keys.next();Object child=o.opt(key);
        if(child instanceof JSONObject||child instanceof JSONArray)collect(child,depth+1,out,seen,limit);
      }
    } else if(node instanceof JSONArray) {
      JSONArray arr=(JSONArray)node;
      for(int i=0;i<arr.length()&&out.size()<limit;i++)collect(arr.opt(i),depth+1,out,seen,limit);
    }
  }
}
