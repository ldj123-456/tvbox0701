# 轩轩私人订阅管理器 (V0.2)

> 仅管理订阅链接和节点配置。**不提供流量转发、代理出口或绕过平台限制的功能。** 不包含任何可使用的第三方节点。

## 架构与进度

- 前端：Cloudflare Pages Advanced Mode (`_worker.js` + `index.html`)。
- 后端：Cloudflare KV Binding `SUB_DB`，仅存储自有节点配置。
- 鉴权：管理员 Bearer Token (`SUB_ADMIN`) 和独立的订阅访问 Token (`SUB_LINK_KEY`)。令牌必须分别随机生成，至少 32 字符；不得提交进 GitHub。
- 订阅：VLESS + WebSocket + TLS，Base64 文本，支持 `region=US-LA|US|SG|OTHER|all`。
- 发布规则：节点配置需同时满足 `enabled: true` 与 `verified: true` 才会被导出。**verified 是管理员确认标记，不是自动链路检查或 IP 信誉保证。**
- 当前：源码与自动测试已完成；**此分支尚未部署到生产环境，也没有实际洛杉矶代理出口**。

## 开发和验证

Node.js 20+，不需要下载任何 npm 依赖：

```bash
cd subhub
npm test
npm run check
```

测试仅使用示例域名 `example.net` 和虚拟 UUID，不连接第三方节点或代理服务。

## HTTP 接口

| 方法/路径 | 功能 | 鉴权 |
|---|---|---|
| `GET /api/health` | 获取管理接口健康和节点数量 | 无（不返回凭据） |
| `GET /api/admin/nodes` | 查看保存的配置 | `Authorization: Bearer <SUB_ADMIN>` |
| `PUT /api/admin/nodes` | 原子更新节点配置（上限 100） | `Authorization: Bearer <SUB_ADMIN>` |
| `GET /sub/<SUB_LINK_KEY>?protocol=default&region=US-LA` | 获取订阅 | 私有 URL 令牌 |

KV 存储键为 `nodes`，对应 JSON 结构如下（**不能当作实际可用节点**）：

```json
{
  "nodes": [{
    "name": "US-LA (example, not connected)",
    "address": "la.example.net",
    "port": 443,
    "uuid": "11111111-1111-4111-8111-111111111111",
    "sni": "edge.example.net",
    "host": "edge.example.net",
    "path": "/ws",
    "region": "US-LA",
    "enabled": false,
    "verified": false
  }]
}
```

注意：即使参数合法，也不意味着代理后端存在或可用，**请在合规服务器上完成端到端连接、出口位置与 IP 质量检测后，才将节点标为 verified**。

## 部署前检查

1. 原 `edgetunnel` Pages 项目已得到 Cloudflare 明确的 `8000119 blocked` 响应，请保留证据并使用 Cloudflare 官方申诉渠道，不要更换名称重复部署被禁止的同一代理实现。
2. 本项目为独立的**订阅管理服务**，与代理出口解耦。必须先确认平台允许的使用方式、自己的节点归属权和可用的部署环境。
3. 配置存储绑定 `SUB_DB`，管理员密钥 `SUB_ADMIN` 与订阅访问密钥 `SUB_LINK_KEY`；不要在公开仓库、聊天或日志中出现真实密钥。
4. 无出口时保持节点列表为空：订阅接口应该返回 **503**，不能生成假线路。

## 安全注意

- `/sub/` 令牌属于私密凭证；泄露后应立即轮换。
- 管理接口仅接受 Bearer 令牌；建议增加访问日志审计、Rate Limit 和定期密钥轮换。
- Cloudflare KV 是最终一致性存储，管理接口写入后短时间内其他边缘位置可能读取到旧内容。
- GitHub 仓库若为公开，只能存放不含凭据的源码和测试，不能提交真实 UUID 或私有网络配置。

## 不含内容

此版本不含 Xray、sing-box、Cloudflare 隧道代理实现、第三方订阅源镜像、网络出口、可用代理链接或自动运行的端到端网络检测。
