// XPTV 红果短剧 v2 / tvOS 测试版
// 使用 2026 新版详情接口与可公开访问的网页播放地址。不处理加密或付费内容。
const CryptoJS=createCryptoJS();
const API_HOST='https://api5-normal-sinfonlineb.fqnovel.com';
const WEB_HOST='https://novelquickapp.com';
const UA_APP='com.phoenix.read/73532 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)';
const UA_WEB='Mozilla/5.0 (Linux; Android 16; 25053RT47C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';
const TABS=[['热剧','videoseries_hot'],['新剧','firstonlinetime_new'],['逆袭','cate_739'],['总裁','cate_29'],['现言','cate_3'],['打脸','cate_1051'],['马甲','cate_266'],['豪门','cate_1053'],['都市','cate_261'],['神豪','cate_20']];
const appConfig={ver:20261019,title:'红果短剧（全集目录测试）',site:'https://fanqienovel.com/#短剧',tabs:TABS.map(([name,item])=>({name,ext:{item}}))};
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
async function getPlayinfo(ext){
 ext=argsify(ext);
 const id=String(ext.id||''),vid=String(ext.vid||'');
 const index=Number(ext.index||0),limit=Number(ext.accessible);
 if(!/^\d{12,25}$/.test(id)||!/^\d{12,25}$/.test(vid))return jsonify({urls:[]});
 if(Number.isFinite(limit)&&limit>=0&&index>limit){$print('Hongguo web episode locked: '+index);return jsonify({urls:[]})}
 for(const host of ['https://hongguoduanju.com','https://novelquickapp.com']){
  try{
   const h=await fetchHtml(host+'/player/'+id+'/'+vid),url=webUrl(h,id,vid);
   if(url)return jsonify({urls:[url],headers:[{'User-Agent':UA_WEB,'Referer':host+'/'}]});
  }catch(e){$print('Hongguo open video failed on '+host+': '+e)}
 }
 // 红果 App 备用接口常返回 CENC 加密 MP4。不能作为普通 tvOS 播放链接。
 return jsonify({urls:[]})
}
async function search(ext){return jsonify({list:[]})}
