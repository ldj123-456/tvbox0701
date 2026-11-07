import http from 'node:http';
import { createHash } from 'node:crypto';
const PORT = Number(process.env.PORT || 10000);
const send = (socket, opcode, data = Buffer.alloc(0)) => {
  const body = Buffer.isBuffer(data) ? data : Buffer.from(String(data));
  if (body.length > 125) throw new Error('Frame too large for this diagnostic');
  socket.write(Buffer.concat([Buffer.from([0x80 | opcode, body.length]), body]));
};
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store');
  if (req.url === '/health') {
    res.end(JSON.stringify({ ok: true, service: 'free-websocket-smoke-test', node: process.version }));
    return;
  }
  res.statusCode = 200;
  res.end(JSON.stringify({ message: 'HTTP/WebSocket diagnostic only; NOT a proxy or VPN.', health: '/health', websocket: '/echo' }));
});
server.on('upgrade', (req, socket) => {
  const key = req.headers['sec-websocket-key'];
  if (req.url !== '/echo' || typeof key !== 'string' || req.headers.upgrade?.toLowerCase() !== 'websocket') {
    socket.end('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n');
    return;
  }
  const accept = createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  let buffer = Buffer.alloc(0);
  socket.on('data', chunk => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 2) {
      const b0 = buffer[0], b1 = buffer[1];
      const length = b1 & 0x7f;
      if (length > 125 || !(b1 & 0x80)) { socket.destroy(); return; }
      const total = 2 + 4 + length;
      if (buffer.length < total) return;
      const mask = buffer.subarray(2, 6);
      const payload = Buffer.from(buffer.subarray(6, total));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
      buffer = buffer.subarray(total);
      const opcode = b0 & 0x0f;
      if (opcode === 0x8) { send(socket, 0x8); socket.end(); return; }
      if (opcode === 0x9) { send(socket, 0xA, payload); continue; }
      if (opcode === 0x1) { send(socket, 0x1, payload); continue; }
      socket.destroy(); return;
    }
  });
});
server.listen(PORT, '0.0.0.0', () => console.log('Ready on port ' + PORT));
