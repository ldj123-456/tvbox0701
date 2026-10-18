/*
 * XPTV bridge adapter for N3urda/hongguoTV's Node HTTP API.
 * TEMPLATE ONLY: not a standalone streaming source.
 * Requires an authorized backend reachable on the same LAN from Apple TV.
 * NEVER put access tokens in public repos or open the HTTP service to the Internet.
 * https://github.com/N3urda/hongguoTV/blob/main/docs/API.md
 */
const HG_BRIDGE_BASE = 'http://192.0.2.10:8787'; // TEST-NET-1 placeholder: replace with your server LAN address
const HG_HEADERS = {};
const hgConfig = {
  ver: 20261018,
  title: '红果 · Bridge 实验源',
  site: HG_BRIDGE_BASE,
  tabs: [{ name: '红果短剧', ext: { kind: 'home' } }]
};
function hgJson(value) {
  if (typeof value === 'string') return JSON.parse(value);
  if (value && typeof value === 'object') return value;
  throw new Error('红果 Bridge 返回空内容');
}
function hgApiUrl(path) {
  if (!/^https?:\/\/[^/]+/i.test(HG_BRIDGE_BASE)) throw new Error('请先配置红果 Bridge 服务地址');
  return HG_BRIDGE_BASE.replace(/\/+$/, '') + path;
}
async function hgGet(path) {
  const response = await $fetch.get(hgApiUrl(path), { headers: HG_HEADERS });
  const json = hgJson(response.data);
  if (json.error) throw new Error(json.error);
  return json;
}
function hgCard(item) {
  const id = String(item.id ?? '');
  if (!/^\d+$/.test(id)) return null;
  return {
    vod_id: id,
    vod_name: String(item.title || id),
    vod_pic: String(item.cover || ''),
    vod_content: String(item.description || ''),
    vod_remarks: String(item.badge || ''),
    ext: { id }
  };
}
async function getConfig() { return jsonify(hgConfig); }
async function getCards(ext) {
  ext = argsify(ext);
  const page = Number(ext.page) || 1;
  const data = await hgGet('/api/home?page=' + encodeURIComponent(String(page)));
  return jsonify({ list: (Array.isArray(data.items) ? data.items : []).map(hgCard).filter(Boolean) });
}
async function search(ext) {
  ext = argsify(ext);
  const phrase = String(ext.text || '').trim();
  if (!phrase) return jsonify({ list: [] });
  const page = Number(ext.page) || 1;
  const data = await hgGet('/api/search?q=' + encodeURIComponent(phrase) + '&page=' + encodeURIComponent(String(page)));
  return jsonify({ list: (Array.isArray(data.items) ? data.items : []).map(hgCard).filter(Boolean) });
}
async function getTracks(ext) {
  ext = argsify(ext);
  const seriesId = String(ext.id || '');
  if (!/^\d+$/.test(seriesId)) return jsonify({ list: [] });
  const data = await hgGet('/api/series/' + seriesId);
  const eps = Array.isArray(data.episodes) ? data.episodes : [];
  const tracks = eps.map((episode, i) => {
    const id = String(episode.id ?? '');
    if (!/^\d+$/.test(id)) return null;
    return { name: String(episode.title || '第' + (i + 1) + '集'), ext: { id }, pan: '' };
  }).filter(Boolean);
  return jsonify({ list: [{ title: '红果短剧', tracks }] });
}
async function getPlayinfo(ext) {
  ext = argsify(ext);
  const id = String(ext.id || '');
  if (!/^\d+$/.test(id)) return jsonify({ urls: [] });
  const data = await hgGet('/api/episodes/' + id + '/play?fresh=1');
  const path = String(data.path || '');
  if (!/^\/api\/episodes\/\d+\/stream$/.test(path)) return jsonify({ urls: [] });
  return jsonify({ urls: [hgApiUrl(path)] });
}
