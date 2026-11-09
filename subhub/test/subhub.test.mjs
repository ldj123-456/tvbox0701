import test from 'node:test';
import assert from 'node:assert/strict';
import handler from '../_worker.js';

const ADMIN='a'.repeat(64), SUB_KEY='b'.repeat(64);
const UUID='11111111-1111-4111-8111-111111111111';
function environment() {
  const db=new Map();
  return {
    SUB_ADMIN:ADMIN,SUB_LINK_KEY:SUB_KEY,
    SUB_DB:{
      async get(key,type){const value=db.get(key);return type==='json'&&value?JSON.parse(value):value??null;},
      async put(key,value){db.set(key,value);}
    },
    ASSETS:{async fetch(){return new Response('HOME',{status:200});}}
  };
}
const base='https://sub.example.net';
const node={name:'专享-US-LA',address:'la.example.net',port:443,uuid:UUID,
  sni:'edge.example.net',host:'edge.example.net',path:'/ws',region:'US-LA',enabled:true};
const sub='/sub/'+SUB_KEY+'?protocol=default&region=US-LA';
function call(env,path,method='GET',body,token) {
  const headers=token?{authorization:'Bearer '+token}:{};
  const request=new Request(base+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
  return handler.fetch(request,env);
}
test('health is not a proxy and starts without nodes',async()=>{
  const result=await call(environment(),'/api/health');
  assert.equal(result.status,200);
  const data=await result.json();
  assert.equal(data.ok,true);assert.equal(data.proxyBackend,false);
  assert.equal(data.configuredNodes,0);
  assert.equal(data.publishableNodes,0);
});
test('admin actions require independent bearer token',async()=>{
  const env=environment();
  assert.equal((await call(env,'/api/admin/nodes')).status,401);
  assert.equal((await call(env,'/api/admin/nodes','GET',undefined,SUB_KEY)).status,401);
  const r=await call(env,'/api/admin/nodes','GET',undefined,ADMIN);
  assert.deepEqual(await r.json(),{nodes:[]});
});
test('a configured but unverified node never enters a subscription',async()=>{
  const env=environment();
  const r=await call(env,'/api/admin/nodes','PUT',{nodes:[node]},ADMIN);
  assert.equal(r.status,200);
  assert.equal((await call(env,sub)).status,503);
});
test('only enabled and verified nodes of requested region are published',async()=>{
  const env=environment();
  const sg={...node,name:'专享-SG',region:'SG',address:'sg.example.net'};
  const off={...node,name:'OFF',enabled:false};
  const put=await call(env,'/api/admin/nodes','PUT',
    {nodes:[{...node,verified:true},{...sg,verified:true},off]},ADMIN);
  assert.equal(put.status,200);
  assert.equal((await put.json()).publishableNodes,2);
  const r=await call(env,sub);
  assert.equal(r.status,200);
  const text=Buffer.from(await r.text(),'base64').toString('utf8');
  const urls=text.trim().split('\n');
  assert.equal(urls.length,1);
  assert.ok(urls[0].startsWith('vless://'));
  assert.ok(urls[0].includes('la.example.net:443'));
  assert.ok(urls[0].includes('security=tls'));
  assert.ok(urls[0].includes('type=ws'));
  assert.ok(decodeURIComponent(urls[0]).includes('专享-US-LA'));
});
test('invalid link token returns 404',async()=>{
  const env=environment();
  assert.equal((await call(env,'/sub/invalid')).status,404);
  assert.equal((await call(env,'/sub/'+'z'.repeat(64))).status,404);
});
test('invalid nodes do not overwrite stored configuration',async()=>{
  const env=environment();
  const result=await call(env,'/api/admin/nodes','PUT',
    {nodes:[{...node,path:'badpath'}]},ADMIN);
  assert.equal(result.status,400);
  assert.deepEqual(await (await call(env,'/api/admin/nodes','GET',undefined,ADMIN)).json(),{nodes:[]});
});
test('static home and missing asset binding are safe',async()=>{
  const env=environment();
  assert.equal((await call(env,'/')).status,200);
  env.ASSETS=null;
  assert.equal((await call(env,'/anything')).status,404);
});