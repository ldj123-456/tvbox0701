// Xuanxuan private subscription hub. Distributes configurations only; never relays traffic.
const headers = {"cache-control":"private, no-store, max-age=0","x-content-type-options":"nosniff","referrer-policy":"no-referrer"};
function json(value,status=200){return new Response(JSON.stringify(value),{status,headers:{...headers,"content-type":"application/json; charset=utf-8"}});}
function missing(){return json({error:"Not found"},404);}
function vless(n){const q=new URLSearchParams({encryption:"none",security:"tls",sni:n.sni,type:"ws",host:n.host,path:n.path});return "vless://"+n.uuid+"@"+n.address+":"+n.port+"?"+q.toString()+"#"+encodeURIComponent(n.name);}
function enc(s){const bytes=new TextEncoder().encode(s);let data="";for(let i=0;i<bytes.length;i++)data+=String.fromCharCode(bytes[i]);return btoa(data);}
function validHost(v){return typeof v==="string"&&v.length>0&&v.length<=253&&/^[a-zA-Z0-9.-]+$/.test(v)&&v.split(".").every(label=>label.length>0&&label.length<=63&&!label.startsWith("-")&&!label.endsWith("-"));}
function normalize(n){if(!n||typeof n!=="object")throw Error("Invalid node object");const o={name:String(n.name||"").trim(),address:String(n.address||"").trim(),port:Number(n.port),uuid:String(n.uuid||"").trim().toLowerCase(),sni:String(n.sni||"").trim(),host:String(n.host||"").trim(),path:String(n.path||"").trim(),region:String(n.region||"OTHER").trim(),enabled:n.enabled===true,verified:n.verified===true};
 if(!o.name||o.name.length>80||/[\r\n]/.test(o.name))throw Error("Invalid node name");
 if(!validHost(o.address)||!validHost(o.sni)||!validHost(o.host))throw Error("Invalid address, SNI or Host");
 if(!Number.isInteger(o.port)||o.port<1||o.port>65535)throw Error("Invalid port");
 if(!/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(o.uuid))throw Error("Invalid UUID");
 if(!o.path.startsWith("/")||o.path.length>240||/[\r\n?#]/.test(o.path))throw Error("Invalid WebSocket path");
 if(!["US-LA","US","SG","OTHER"].includes(o.region))throw Error("Invalid region");
 return o;
}
async function nodes(env){if(!env.SUB_DB)return null;const x=await env.SUB_DB.get("nodes","json");return Array.isArray(x)?x:[];}
function authorize(req,key){const auth=req.headers.get("authorization")||"";return typeof key==="string"&&key.length>=32&&auth==="Bearer "+key;}
export default {async fetch(req,env){
 try{
 const u=new URL(req.url);const path=u.pathname.replace(/\/+$/,"")||"/";
 if(path==="/api/health"){const list=await nodes(env);return json({ok:true,component:"subscription-management-only",storageReady:list!==null,configuredNodes:list?.length||0,publishableNodes:list?.filter(n=>n.enabled&&n.verified).length||0,proxyBackend:false});}
 if(path==="/api/admin/nodes"){
   if(!authorize(req,env.SUB_ADMIN))return json({error:"Unauthorized"},401);
   if(!env.SUB_DB)return json({error:"Storage binding unavailable"},503);
   if(req.method==="GET")return json({nodes:await nodes(env)});
   if(req.method==="PUT"){
      const raw=await req.text();if(new TextEncoder().encode(raw).length>65536)return json({error:"Configuration too large"},413);
      let body;try{body=JSON.parse(raw)}catch{return json({error:"Invalid JSON"},400);}
      if(!body||!Array.isArray(body.nodes)||body.nodes.length>100)return json({error:"nodes must be an array of up to 100 entries"},400);
      let list;try{list=body.nodes.map(normalize)}catch(e){return json({error:e.message},400);}
      await env.SUB_DB.put("nodes",JSON.stringify(list));
      return json({ok:true,count:list.length,publishableNodes:list.filter(n=>n.enabled&&n.verified).length,warning:"verified=true is a manual attestation; run an end-to-end test first."});
   }
   return json({error:"Method not allowed"},405);
 }
 if(path.startsWith("/sub/")){
    if(req.method!=="GET")return json({error:"Method not allowed"},405);
    const token=decodeURIComponent(path.slice(5));
    if(!/^[a-zA-Z0-9_-]{32,256}$/.test(token)||typeof env.SUB_LINK_KEY!=="string"||env.SUB_LINK_KEY.length<32||token!==env.SUB_LINK_KEY)return missing();
    const protocol=u.searchParams.get("protocol")||"default";
    if(protocol!=="default")return json({error:"Only protocol=default is available"},400);
    const region=u.searchParams.get("region")||"all";
    if(!["all","US-LA","US","SG","OTHER"].includes(region))return json({error:"Unknown region"},400);
    const entries=await nodes(env);
    if(!entries)return json({error:"Storage binding unavailable"},503);
    const out=entries.filter(n=>n.enabled&&n.verified&&(region==="all"||n.region===region));
    if(!out.length)return json({error:"No verified and enabled nodes. An authorized exit server is required."},503);
    return new Response(enc(out.map(vless).join("\n")+"\n"),{headers:{...headers,"content-type":"text/plain; charset=utf-8","profile-update-interval":"6"}});
 }
 if(req.method==="GET"&&env.ASSETS?.fetch&&(path==="/"||path==="/index.html"))return env.ASSETS.fetch(new Request(new URL("/index.html",req.url),req));
 if(req.method==="GET"&&env.ASSETS?.fetch)return env.ASSETS.fetch(req);
 return missing();
 }catch(e){console.error("Subscription service error",{name:String(e?.name||"Error")});return json({error:"Internal error"},500);}
}};