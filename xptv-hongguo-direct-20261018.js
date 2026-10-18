// Hongguo XPTV direct access test 2026-10-18. Does NOT proxy or decrypt protected content.
// XPTV 红果短剧 v2 / tvOS 测试版
// 使用 2026 新版详情接口与可公开访问的网页播放地址。不处理加密或付费内容。
const CryptoJS=createCryptoJS();
const API_HOST='https://api5-normal-sinfonlineb.fqnovel.com';
const WEB_HOST='https://novelquickapp.com';
const UA_APP='com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)';
const UA_WEB='Mozilla/5.0 (Linux; Android 16; 25053RT47C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';
const TABS=[['热剧','videoseries_hot'],['新剧','firstonlinetime_new'],['逆袭','cate_739'],['总裁','cate_29'],['现言','cate_3'],['打脸','cate_1051'],['马甲','cate_266'],['豪门','cate_1053'],['都市','cate_261'],['神豪','cate_20']];
const appConfig={ver:20261018,title:'红果直连（独立测试）',site:'https://hongguoduanju.com',tabs:[{name:'东北后妈·测试',ext:{featured:true}},...TABS.map(([name,item])=>({name,ext:{item}}))]};
function obj(x){if(typeof x!=='string')return x||{};try{return JSON.parse(x)}catch(_){return {}}}
function nested(x,ks){return ks.reduce((o,k)=>o&&o[k],x)}
function randID(){return '1'+String(Math.floor(Math.random()*900000000)+100000000)+String(Math.floor(Math.random()*900000000)+100000000)}
const DEV=randID(),IID=randID();
function qp(t){return [['aid','8662'],['app_name','novelread'],['version_code','73532'],['version_name','7.3.5.32'],['manifest_version_code','73532'],['update_version_code','73532'],['channel','update_64'],['device_platform','android'],['os','android'],['ssmix','a'],['device_type','25053RT47C'],['device_brand','Redmi'],['language','zh'],['os_api','36'],['os_version','16'],['resolution','1280*2772'],['dpi','520'],['ac','wifi'],['device_id',DEV],['iid',IID],['_rticket',String(t)]]}
function esc(x){return encodeURIComponent(String(x)).replace(/[!'()*]/g,c=>'%'+c.charCodeAt(0).toString(16).toUpperCase())}
function md5(x){return CryptoJS.MD5(x).toString(CryptoJS.enc.Hex)}
function gorgon(q,b,time){
  const get4=h=>[0,2,4,6].map(i=>parseInt(h.slice(i,i+2),16));
  const p=new Array(20).fill(0), a=get4(md5(q));for(let i=0;i<4;i++)p[i]=a[i];
  const stub=b?md5(b).toUpperCase():'';if(stub){const bytes=get4(stub);for(let i=0;i<4;i++)p[4+i]=bytes[i]}
  p[12]=0;p[13]=6;p[14]=11;p[15]=28;
  const ts=Math.floor(time/1000);p[16]=(ts>>>24)&255;p[17]=(ts>>>16)&255;p[18]=(ts>>>8)&255;p[19]=ts&255;
  const key=[0x44,0xb9,0xb9,0xd9,0xa4,0xae,0xf9,0xfc,0xa4,0x93,0xaa,0x75,0x7c,0xa3,0xc2,0xc4,0xa4,0x96,0x93,0x8f];
  for(let i=0;i<20;i++)p[i]^=key[i];
  function rev(v){let r=0;for(let j=0;j<8;j++){r=(r<<1)|(v&1);v>>>=1}return r}
  for(let i=0;i<20;i++)p[i]=(rev(((((p[i]<<4)|(p[i]>>>4))&255)^p[(i+1)%20]))^255^20)&255;
  const sign=[0x84,0x04,0x40,0x1c,0,0,...p].map(x=>x.toString(16).padStart(2,'0')).join('').toUpperCase();
  return {'X-Khronos':String(ts),'X-Gorgon':sign,'X-SS-Req-Ticket':String(time),'X-SS-STUB':stub}
}
async function post(path,body){
  const now=Date.now(), query=qp(now).map(([k,v])=>esc(k)+'='+esc(v)).join('&'), b=JSON.stringify(body);
  const headers={'User-Agent':UA_APP,'Accept':'application/json','Content-Type':'application/json; charset=utf-8','X-XS-From-Web':'0','Sdk-Version':'2',...gorgon(query,b,now)};
  const r=await $fetch.post(API_HOST+path+'?'+query,b,{headers});return obj(r.data)
}
async function getConfig(){return jsonify(appConfig)}
async function getCards(ext){
  ext=argsify(ext);
  if(ext.featured) return jsonify({list:[{vod_id:'7693516944581872664',vod_name:'东北后妈，整顿全家',vod_pic:'',vod_remarks:'第4集可用性测试',ext:{id:'7693516944581872664'}}]});
  const page=Math.max(1,Number(ext.page)||1), d=new Date(), pad=n=>String(n).padStart(2,'0');
  const sid=[d.getFullYear(),pad(d.getMonth()+1),pad(d.getDate()),pad(d.getHours()),pad(d.getMinutes())].join('');
  const pairs=[['change_type',0],['selected_items',ext.item||'videoseries_hot'],['tab_type',8],['cell_id','6952850996422770718'],['version_tag','video_feed_refactor'],['device_id','1423244030195267'],['aid',1967],['app_name','novelapp'],['ssmix','a'],['session_id',sid],['offset',(page-1)*20]];
  const q=pairs.map(([k,v])=>esc(k)+'='+esc(v)).join('&');
  try{
    const r=await $fetch.get('https://reading.snssdk.com/reading/bookapi/bookmall/cell/change/v?'+q,{headers:{'User-Agent':UA_WEB}});
    const rows=nested(obj(r.data),['data','cell_view','cell_data'])||[];
    return jsonify({list:rows.map(e=>{
      const v=(e.video_data||[])[0]||{};return {vod_id:String(v.series_id||''),vod_name:v.title||'',vod_pic:v.cover||'',vod_remarks:v.sub_title||'',ext:{id:String(v.series_id||'')}};
    }).filter(x=>x.vod_id&&x.vod_name)});
  }catch(e){$print('Hongguo list failed: '+e);return jsonify({list:[]})}
}
function webEps(html){
 const s=String(html||'');
 const all=[...s.matchAll(/"vid_list"\s*:\s*\[([^\]]*)\]/g)];
 for(const m of all){
  let v=[];
  try{v=JSON.parse('['+m[1]+']')}catch(_){v=(m[1].match(/\d{15,25}/g)||[])}
  const eps=v.map((x,i)=>({vid:String(typeof x==='object'?(x.vid||x.video_id||''):x),vid_index:i+1,title:''})).filter(x=>/^\d{12,25}$/.test(x.vid));
  if(eps.length)return eps;
 }
 return [];
}
function webFreeCount(html){
 const m=String(html||'').match(/"accessible_episode_cnt"\s*:\s*(\d+)/);
 return m?Number(m[1]):-1;
}
function cleanVideoUrl(raw){
 return String(raw||'').replace(/\\u002[fF]/g,'/').replace(/\\u0026/g,'&').replace(/&amp;/g,'&').replace(/\\\//g,'/').replace(/([?&])Range=[^&]*/gi,'$1').replace(/[?&]$/,'');
}
async function fetchHtml(url){
 const r=await $fetch.get(url,{headers:{'User-Agent':UA_WEB,'Referer':'https://hongguoduanju.com/','Accept':'text/html,application/xhtml+xml,*/*'}});
 return String(r.data||'');
}
async function getTracks(ext){
 ext=argsify(ext);const id=String(ext.id||ext.vod_id||'');
 if(!/^\d{12,25}$/.test(id))return jsonify({list:[]});
 let eps=[],open=-1,reason='';
 const pages=['https://hongguoduanju.com/detail?series_id='+id,'https://hongguoduanju.com/player/'+id,'https://novelquickapp.com/player/'+id];
 for(const page of pages){
  try{
   const html=await fetchHtml(page),found=webEps(html);
   if(found.length>eps.length){eps=found;open=webFreeCount(html)}
   if(eps.length>1)break;
  }catch(e){reason+=String(e)+'; ';$print('Hongguo detail fetch '+page+' failed: '+e)}
 }
 // 页面失效时，仅作为备选使用 App 数据，不把单集误判为全剧
 if(!eps.length){
  try{
   const v=nested(await post('/novel/player/video_detail/v1/',{series_id:id}),['data','video_data'])||{};
   eps=(Array.isArray(v.video_list)?v.video_list:[]).map((e,i)=>({vid:String(e.vid||''),vid_index:Number(e.vid_index||i+1),title:''})).filter(e=>/^\d{12,25}$/.test(e.vid));
  }catch(e){$print('Hongguo app detail failed: '+e)}
 }
 const tracks=eps.map((e,i)=>({
  name:'第'+(i+1)+'集'+(open>=0&&i>=open?' 🔒':''),
  pan:'',
  ext:{id,vid:e.vid,index:i+1,accessible:open}
 }));
 return jsonify({list:[{title:tracks.length?'红果 '+tracks.length+' 集'+(open>=0?'（网页可看前'+open+'集）':''):'未获取到有效选集',tracks}]})
}
function webUrl(html,id,vid){
 // 不再从整页抓第一个 main_url：页面还可能包含推荐片或加密预览流
 const s=String(html||''),scripts=/<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi;
 let m;
 while((m=scripts.exec(s))){
  if(!/\btype\s*=\s*["']application\/ld\+json["']/i.test(m[1]))continue;
  let obj;
  try{obj=JSON.parse(m[2])}catch(_){continue}
  const videos=Array.isArray(obj)?obj:[obj];
  for(const v of videos){
   if(!v||v['@type']!=='VideoObject')continue;
   const link=String(v.url||'');
   // 保证视频与当前剧集、当前分集对应
   if(!link.includes('/player/'+id+'/'+vid))continue;
   const src=cleanVideoUrl(v.contentUrl);
   if(/^https:\/\//i.test(src))return src;
  }
 }
 return '';
}
function routerObject(html){
 const txt=String(html||''),a=txt.indexOf('_ROUTER_DATA');if(a<0)return {};
 const start=txt.indexOf('{',a);if(start<0)return {};
 let depth=0,quoted=false,escaped=false;
 for(let i=start;i<txt.length;i++){
  const c=txt[i];
  if(quoted){if(escaped)escaped=false;else if(c==='\\')escaped=true;else if(c==='"')quoted=false;continue}
  if(c==='"'){quoted=true;continue}
  if(c==='{')depth++;
  if(c==='}'){depth--;if(depth===0){try{return JSON.parse(txt.slice(start,i+1))}catch(_){return {}}}}
 }
 return {};
}
function pickClearModel(x){
 let v=(x&&x.video_model)||x;
 if(typeof v==='string'){try{v=JSON.parse(v)}catch(_){return ''}}
 if(!v||typeof v!=='object')return '';
 let rows=v.video_list||[];
 if(!Array.isArray(rows))rows=Object.values(rows);
 for(const row of rows){
  if(!row||typeof row!=='object')continue;
  const enc=row.encrypt_info||{};
  if(enc.spade_a||enc.encrypt===true||/cenc|drm|aes/i.test(String(enc.encryption_method||'')))continue;
  const urls=[row.main_url,row.url,row.play_url,...(Array.isArray(row.url_list)?row.url_list:[])];
  for(let url of urls){
   if(url&&typeof url==='object')url=url.url||url.main_url||'';
   if(typeof url!=='string')continue;
   if(!/^https?:\/\//i.test(url)){try{url=CryptoJS.enc.Utf8.stringify(CryptoJS.enc.Base64.parse(url))}catch(_){}}
   url=cleanVideoUrl(url);
   if(/^https:\/\/[^\s"'<>]+$/i.test(url))return url;
  }
 }
 return '';
}
async function appClearVideo(vid){
 const calls=[
  ['/novel/player/video_model/v1/',{video_id:vid,content_type:1,biz_param:{need_all_video_definition:true,video_platform:3}}],
  ['/novel/player/multi_video_model/preload/v1/',{biz_param:{detail_page_version:0,device_level:3,disable_digg_stat:false,need_all_video_definition:true,need_mp4_align:false,use_os_player:false,use_server_dns:false,video_platform:1024},dr_scene:'preload',mixed_video_id_map:{'1004':[vid]}}]
 ];
 for(const [path,body] of calls){
  try{
   const res=await post(path,body),data=res.data||res;
   const u=pickClearModel(data)||(data[vid]?pickClearModel(data[vid]):'');
   if(u)return u;
  }catch(e){$print('Hongguo direct App API unavailable: '+e)}
 }
 return '';
}
async function getPlayinfo(ext){
 ext=argsify(ext);
 const id=String(ext.id||''),vid=String(ext.vid||'');
 if(!/^\d{12,25}$/.test(id)||!/^\d{12,25}$/.test(vid))return jsonify({urls:[]});
 // Do not assume that the public website's preview count is the user's App permission.
 // Check requested episode for freely presented media, without decrypting protected streams.
 for(const host of ['https://hongguoduanju.com','https://novelquickapp.com']){
  try{
   const html=await fetchHtml(host+'/player/'+id+'/'+vid),u=webUrl(html,id,vid);
   if(u)return jsonify({urls:[u],headers:[{'User-Agent':UA_WEB,'Referer':host+'/'}]});
  }catch(e){$print('Hongguo official site media unavailable: '+e)}
 }
 const u=await appClearVideo(vid);
 if(u)return jsonify({urls:[u],headers:[{'User-Agent':UA_APP,'Referer':'https://novel.snssdk.com/'}]});
 $print('Hongguo direct: no plain video address available for '+vid);
 return jsonify({urls:[]});
}
async function search(ext){
 ext=argsify(ext);
 const q=String(ext.text||'').trim(),page=Math.max(1,Number(ext.page)||1);
 if(!q)return jsonify({list:[]});
 try{
  const html=await fetchHtml('https://hongguoduanju.com/search/'+encodeURIComponent(q)+'?page='+page);
  const data=routerObject(html),l=data.loaderData||{},part=l['search_(keyword)/page']||l.search_page||{};
  const rows=part.searchList||part.search_list||[];
  return jsonify({list:rows.map(r=>{
    let v=r.video_data||r.videoData||r;
    if(Array.isArray(v))v=v[0]||{};
    const id=String(v.series_id||v.series_id_str||r.series_id||'');
    return {vod_id:id,vod_name:v.series_name||v.series_title||v.title||r.name||'',vod_pic:v.series_cover||v.cover||'',vod_remarks:v.episode_right_text||'',ext:{id}};
  }).filter(v=>/^\d{12,25}$/.test(v.vod_id)&&v.vod_name)});
 }catch(e){$print('Hongguo website search failed: '+e);return jsonify({list:[]})}
}

