# 智牧通 Web（用户端 + 管理端）

Vue 3 + Vuetify 3 + TypeScript + Vite 的 Web 用户端与管理端，接真实服务端 `/api/v1` 与 `/admin/v1`。
视觉与交互来自 Web 原型（`原型导出/Web端/智牧通Web原型`），业务规则、字段与文案以 App / 服务端为准。

## 一、目录

```
web/
├─ apps/user        Web 用户端（46 页）
├─ apps/admin       Web 管理端（20 页，生产部署在 /admin/）
├─ packages/shared  类型、请求层、zw-* 公共组件、主题、菜单、工具
├─ e2e / tools      Playwright 脚本与截图工具
└─ deploy           Nginx 配置与公网 IP 部署说明
```

## 二、本地运行

前置：服务端已在 `http://127.0.0.1:8080` 运行（MySQL、Redis、MinIO 已启动）。

```powershell
cd D:\Android_Program\SLMP\web
npm install
npm run dev:user     # http://127.0.0.1:5173
npm run dev:admin    # http://127.0.0.1:5174
```

Vite 已代理 `/api/v1`、`/admin/v1` 到 8080，浏览器同源，无需 CORS。

## 三、账号

| 端 | 账号 | 说明 |
|---|---|---|
| 用户端 | `13800006620 / Zmt12345` | 所有者王建国 |
| 用户端 | `13700005678 / Zmt12345` | 员工李明（验证码登录用 123456） |
| 管理端 | `zhoumin / Zmt12345` | 超级管理员（dev 已关闭强制改密；正式种子首登会要求改密） |

## 四、构建与检查

```powershell
npm run typecheck       # 两端 vue-tsc
npm run build:user      # apps/user/dist
npm run build:admin     # apps/admin/dist（资源前缀 /admin/）
npm run test            # Vitest
```

## 五、接口类型

`packages/shared/src/types/generated.ts` 由运行中的服务端 OpenAPI 生成：

```powershell
# 先取最新 OpenAPI（服务端启动后）
Invoke-RestMethod http://127.0.0.1:8080/v3/api-docs | ConvertTo-Json -Depth 50 -Compress | Set-Content ..\docs\api\openapi.json
npm run gen:api
```

服务端 JSON 是 snake_case；生成脚本会把 SpringDoc 的 camelCase 属性名统一转成 snake_case，页面直接按 snake_case 使用。

## 六、部署到公网 IP

见 [`deploy/README.md`](deploy/README.md)：

- `http://<公网IP>/` → 用户端
- `http://<公网IP>/admin/` → 管理端（后台）
- Nginx 同源代理 `/api/v1`、`/admin/v1`、`/hls`
- HTTP 部署需服务端 `COOKIE_SECURE=false`、`ALLOWED_ORIGINS=http://<公网IP>`；HTTPS 保持 `COOKIE_SECURE=true`

## 七、验收与截图

- 验收报告：[`docs/验收报告.md`](docs/验收报告.md)
- 用户端截图：[`screenshots/user`](screenshots/user)（46 张，1440×900）
- 管理端截图：[`screenshots/admin`](screenshots/admin)（20 张，1440×900）
- 接口类型与端点清单：`../docs/api/`

## 八、目录约定

- 页面：`apps/<app>/src/views/<模块>/XxxView.vue`，路由名 = 页面编号（如 WU-L04）。
- store：`apps/<app>/src/stores/<模块>/xxx.ts`（Pinia 选项式）。
- 接口：`apps/<app>/src/api/<模块>.ts`，函数注释接口编号。
- 公共：`packages/shared`（zw 组件、请求层、类型、主题、菜单、格式化）。
- 页面四态：loading / ready / empty / filtered_empty / error / forbidden。
- 员工角色不渲染金额、成本、盈亏、出栏单、行情、报表相关菜单、按钮、列与字段。
