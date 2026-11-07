# Free Render HTTP / WebSocket smoke test

A standalone HTTP + WebSocket echo diagnostic, not an EdgeTunnel proxy or Shadowrocket node.

- HTTP GET /health returns JSON with ok:true
- WebSocket /echo returns the received text
- Render plan: Free only
- Start: cd free-render-ws-smoke-test && npm start
- Build: cd free-render-ws-smoke-test && npm install --omit=dev
- Region: Oregon
- No external API keys, no datastore, no outbound proxy support.

This test lives on an isolated branch and does not change the repository main branch.
