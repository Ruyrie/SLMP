# 智牧通 Web 端实施方案（用户端 + 管理端）

> 编写：2026-10-24。范围：新建 `web/` 工程，完成原型中的 Web 用户端 46 页、Web 管理端 20 页，与现有 App、服务端组成完整三端。
> 业务规则、字段、校验、文案以 **App 当前实现 + 服务端实际接口** 为准，视觉与交互形态以 **Web 原型** 为准。本文中的冲突取舍按此顺序执行。
>
> **实施结果（2026-10-24）**：已按方案 A 完成。Web 用户端 46 页、管理端 20 页全部落地；管理端服务端从 39 个接口补齐到 **180 个操作 / 154 个路径**；两端 typecheck 与 build 通过；截图见 `web/screenshots/`；验收细节见 `web/docs/验收报告.md`。本文保留为方案与决策记录，第一节的“开工前现状”为历史快照。

---

## 一、现状核查（开工前的真实情况）

| 项 | 核查结果 | 依据 |
|---|---|---|
| Web 原型 | 66 个页面（用户端 46、管理端 20），69 个页面文件，257 张状态截图；Vue 3 + Vuetify 3 实现，1440 × 900 | `原型导出/Web端/智牧通Web原型/` |
| 原型公共库 | `zw.js`（744 行，约 30 个 `zw-*` 组件 + 菜单 + 外壳）、`zw.css`（55KB）、`tokens.css`、`tokens.json`、本地 Material Symbols 字体 | 同上 |
| 页面规格 | 用户端 11 个模块 + 通用，共 12 篇 Web UI 文档；管理端 4 篇；另有 08 号架构规范（811 行）、07 号 UI 规范 | `.tech-prep/text/Web端_*.md` |
| App | 11 个模块全部完成，938 个单测通过 | `docs/项目交接与三端联调说明.md`、`server/docs/服务端交付状态.md` |
| 服务端（用户端） | **325 个 `/api/v1` 接口，覆盖 App 319/319**；11 个业务模块 + 公共接口齐全 | 代码扫描、`/v3/api-docs`、交付状态 |
| 服务端（管理端） | **仅 39 个 `/admin/v1` 接口**：登录/验证码/改密/me、管理员与角色权限、操作日志、字典、品类/品种/标准、管理端消息 | 代码扫描、`docs/api/endpoints.csv` |
| 管理端服务端缺口 | 审核中心 R01–R04、内容运营 C01–C05、平台概览与用户/养殖场 S02–S04、基础数据 D02–D04（免疫模板、兽药目录管理、巡检模板）**均没有接口**；`admin.audit`、`admin.content` 包不存在 | `server/zhimu-biz/.../admin/` |
| 本机可运行性 | ✅ 已启动 Redis 6379、MinIO 9000/9001；MySQL 9.5 已运行；`zhimu` 库 31 个迁移执行成功；服务端 8080 已启动；`/api/v1/ping`、Web 登录（`X-Client: WEB` + HttpOnly 刷新 Cookie）实测通过 | 本次核查 |
| 工具链 | Node 24.11、npm 11.6、JDK 21、Maven 3.9；**未安装 pnpm**（corepack 可用） | 本次核查 |
| 依赖版本 | 架构文档锁定的 Vue 3.5.43 / Vuetify 3.13.6 / Vite 8.3.3 / TS 6.0.3 / vue-router 5.3.1 / Pinia 4.0.3 在 npm 均存在 | `npm view` |
| 示例数据缺口 | `t_immune_plan`、`t_immune_record`、`t_medication_record`、`t_disinfect_*` 为空（V9_11 未提供）；部分统计与 App mock 有已知差异 | 交付状态第五节 |
| 管理端账号 | `zhoumin / Zmt12345`（首登强制改密） | `V2_01__init_admin_rbac.sql` |
| 用户端账号 | 所有者 `13800006620 / Zmt12345`；员工 `13700005678`；验证码 `123456`（mock） | 服务端文档 |

**结论：** 用户端 46 页可以 100% 接真实接口；管理端 20 页中只有 5 页（S01 登录、S05 管理员权限、S06 操作日志、D01 品类品种、D05 字典）有真实接口，其余 15 页必须先决定“补服务端”还是“前端演示适配层”。

---

## 二、范围与页面清单

### 2.1 用户端 46 页

| 分组 | 页面 | 数量 | 主要真实接口 |
|---|---|---|---|
| 账号与通用 | WU-A01–A04、WU-U01、WU-U02 | 6 | A01–A11、U01–U05、U14 |
| 养殖场与今日 | WU-F01–F04、WU-T01–T04 | 8 | F01–F10、T01–T20 |
| 栏舍与繁育 | WU-L01–L05、WU-B01–B03 | 8 | L01–L11、B01–B07 |
| 饲喂与免疫用药 | WU-G01–G03、WU-D01–D03 | 6 | G01–G06、D01–D09 |
| 巡检与库存 | WU-I01–I04、WU-M01–M03 | 7 | I01–I09、M01–M11 |
| 经营 | WU-E01–E07 | 7 | E01–E14、行情、报表 |
| AI 与课堂 | WU-K01–K04 | 4 | K01–K11 |

### 2.2 管理端 20 页

| 分组 | 页面 | 真实接口现状 |
|---|---|---|
| 审核中心 | WA-R01–R04 | ❌ 无（表 `t_farm_audit`、`t_change_request`、`t_account_recovery` 已有数据） |
| 内容运营 | WA-C01–C05 | ❌ 无（课程/知识表已有，用户端有读接口；通知表需新增） |
| 基础数据 | WA-D01–D05 | ⚠️ D01、D05 有；D02免疫模板、D03兽药目录、D04巡检模板无 |
| 用户与系统 | WA-S01–S06 | ⚠️ S01、S05、S06 有；S02 概览、S03 用户、S04 养殖场无 |

### 2.3 优先级

1. **P0**：外壳、登录、会话恢复、养殖场切换、消息未读、公共组件、请求层 —— 所有页面的前提。
2. **P1**：用户端高频页 T01/T03/T04、L03/L04、I01/I03/I04、G01、D01/D02、M01/M02、E02/E03、K01。
3. **P2**：其余用户端页面与全部管理端页面。
4. **P3**：管理端“演示适配层 → 真实接口”切换。

---

## 三、冲突与差异处理规则

| 冲突 | 处理 |
|---|---|
| 业务规则、字段、校验、错误文案 | 以 App 当前代码为准；App 未覆盖处按服务端 VO/错误码；两者都无时查服务端模块文档 |
| 服务端返回与原型示例数字不同（如存栏 3,870 vs 870） | 一律显示服务端数据，不硬编码原型数字 |
| 交互形态 | 按 Web 原型：扫码→输入批次号、拍照→拖拽上传、底部面板→菜单/抽屉、FAB→顶栏“登记” |
| 原型展示但服务端无接口的功能 | 用户端不实现假数据；管理端按选定方案（见第八节）处理并在页面标注，不静默失败 |
| 原型使用 10月19日/周一等演示日期 | 使用服务端真实日期（`BeijingTime`）；格式化统一 `Asia/Shanghai` |
| 技术准备文档与实现细节不一致 | 以 App/服务端为准；架构文档中的 pnpm workspace 改 npm workspaces（本机无 pnpm），其余结构遵循 |
| 管理端 Cookie 要求 Secure | dev 环境 `cookie-secure: false`，本地 http 可登录；生产按 HTTPS |

---

## 四、技术方案

### 4.1 仓库结构（新增，不改 App 与 server 现有代码）

```
web/
├─ package.json               # npm workspaces: apps/*, packages/*
├─ package-lock.json
├─ tsconfig.base.json
├─ eslint.config.js / .prettierrc
├─ apps/
│  ├─ user/                   # Web 用户端，端口 5173，代理 /api/v1
│  │  ├─ index.html
│  │  ├─ vite.config.ts
│  │  ├─ .env.development / .env.production
│  │  └─ src/
│  │     ├─ main.ts  App.vue
│  │     ├─ api/            # 按模块，函数注释接口编号
│  │     ├─ stores/         # 应用级 + 页面级
│  │     ├─ router/         # 按模块拆分路由
│  │     ├─ menus/
│  │     └─ views/{account,farm,today,herd,breeding,feeding,inspect,health,stock,finance,ai,common}
│  └─ admin/                  # Web 管理端，端口 5174，代理 /admin/v1
│     └─ src/views/{audit,content,base,system}
├─ packages/shared/
│  ├─ types/                 # 由 OpenAPI 生成 + 手写 VO 别名
│  ├─ request/               # axios 实例、拦截器、刷新队列、SSE、上传
│  ├─ theme/                 # W1 主题、tokens.css、Vuetify 默认值、图标
│  ├─ components/            # zw-* 公共组件（SFC 化）
│  ├─ layouts/               # zw-shell、登录布局、状态页
│  ├─ composables/           # useServerTable / useDrawerForm / usePolling
│  ├─ directives/            # v-role / v-perm
│  └─ utils/                 # 日期、数字、校验、幂等键、文件
├─ e2e/                      # Playwright
└─ docs/                     # 架构、接口映射、缺口清单、运行手册
```

### 4.2 技术栈与版本（按架构文档，已核对可用）

- Vue 3.5.43 + TypeScript 6.0.3（严格模式）+ Vite 8.3.3
- Vuetify 3.13.6（**不用 4.x**，保证 `zw.css` 覆盖样式与原型一致）+ `vite-plugin-vuetify`
- vue-router 5.3.1（history 模式，路由名 = 页面编号）、Pinia 4.0.3（选项式 store）
- axios 1.20、ECharts 6.1（SVG 渲染）、hls.js 1.7.3、dayjs 1.11、dompurify 3.4、qrcode 1.5
- 高德 JS API 2.0（可选启用）、Material Symbols Outlined 本地字体
- 测试：Vitest 5 + Vue Test Utils + Playwright 1.63
- 包管理：npm workspaces（本机无 pnpm；结构与 pnpm workspace 等价）

### 4.3 视觉基座：直接移植原型公共库

- 复制 `tokens.css`、`zw.css`、`MaterialSymbolsOutlined.woff2`，保留变量名与类名。
- 把 `zw.js` 的 30 个组件逐个改为 `packages/shared/components/ZwXxx.vue`，**props / slots / 类名与原型一致**；页面写法保持原型风格。
- `zw-map` 默认使用原型示意地图；检测到 `VITE_AMAP_KEY` 时切换为高德 JS 地图（选点/标记/聚合）。地图不可用时不影响页面其他功能，并给出“地图暂不可用，可手动填写地址”的降级提示。
- `zw-line-chart / zw-bar-chart / zw-donut` 内部替换为 ECharts，属性不变。
- 登录页、外壳、抽屉、对话框、Snackbar、上传网格的尺寸与原型截图逐像素对齐（1440 × 900 验收）。

### 4.4 页面实现方式

- 每页一个 `.vue` SFC（`<script setup lang="ts">`），模板与页面级 CSS 从原型页面文件迁移，`data()` 中的 mock 数据替换为 store/接口。
- 页面只做展示与事件转交；请求、表单、校验、幂等、取消在 store 与 api 层。
- 原型中的 `states` 转成真实状态：`loading / ready / empty / filtered_empty / error / forbidden / not_found`。
- 每个页面至少实现：默认、空、加载失败、无权（涉及角色时）四种状态；有抽屉/对话框的页面实现关键弹层状态。
- 管理端表格行高 40px、用户端 48px，与原型一致。

### 4.5 请求层与认证

| 项 | 用户端 | 管理端 |
|---|---|---|
| 基址 | `/api/v1`（dev 代理 127.0.0.1:8080） | `/admin/v1`（dev 代理 127.0.0.1:8080） |
| 认证 | 内存 access token；刷新令牌为 HttpOnly Cookie（`ZMT_REFRESH`，Path `/api/v1/auth`） | HttpOnly Cookie（`ZMT_ADMIN_SESSION`，Path `/admin/v1`） |
| 登录 | `POST /auth/login/password`、`/auth/login/sms`，`X-Client: WEB` | `GET /auth/captcha` + `POST /auth/login` |
| 刷新 | `POST /auth/token/refresh`（Cookie）；40101 单飞刷新队列，失败回登录页 | 40106 回登录页；30 分钟无操作 |
| 请求头 | `Authorization`、`X-Farm-Id`、`X-Client: WEB`、`Idempotency-Key` | `X-Client: ADMIN_WEB` |
| 响应 | `{code,message,data,trace_id}`；成功只返回 data；错误统一 `ApiError` | 同左 |
| 错误码 | 按 08 号 7.4 节分派（40001→字段、40104→登录页、40302/3→无权、42206→确认条…） | 40106/40107、40308 |
| 取消 | 列表切换/离开页面 AbortController；刷新令牌用 Web Locks 防多标签页互相作废 | 同左 |
| 会话恢复 | 启动先 `refresh` 再 `session`，失败进登录页 | 启动调 `/auth/me`，失败进登录页 |

### 4.6 类型与接口

- 用 `openapi-typescript` 从运行中的服务端 `/v3/api-docs` 生成 `shared/types/generated.ts`（本次已保存 `docs/api/openapi.json`，312 路径 / 364 操作），再按模块导出 VO 别名。
- 字段一律 snake_case，不转驼峰；枚举只用编码 + 服务端 `xxx_text`；员工裁剪字段声明为可选。
- 每个 api 函数注释接口编号（如 `API-L05`）与页面编号；服务端变更后重新生成类型并跑类型检查。
- 已产出 `docs/api/endpoints.csv`（364 条：方法、路径、控制器）作为接口索引。

### 4.7 权限与角色

- 用户端角色 `OWNER / MANAGER / STAFF`：员工看不到金额、成本、盈亏、出栏单、行情、报表；相关菜单、按钮、列、字段**不渲染**。
- 字段裁剪以服务端 `@ManagerView` 实际返回为准：VO 里没有的字段整项不渲染，不显示 0/— 代替金额。
- 管理端按 `/auth/me` 返回的权限点渲染按钮；40308 提示并刷新权限。
- 前端隐藏只改善体验，服务端仍逐接口校验。

### 4.8 上传、媒体、AI、地图、图表

- 上传走 `POST /api/v1/files`（multipart：file、scene、视频 cover/duration_ms），进度条与失败重试；照片 JPG/PNG ≤5MB，视频 MP4 ≤30s/30MB，最多 6 个（视频 1 段）。
- 图片显示 `thumb_url`，临时地址按 file_id 缓存；全屏预览用 `MediaViewer`。
- 课程视频：hls.js + 720P/480P 切换 + 倍速 + 续播 + 10 秒进度上报。
- AI 问答：`fetch` + ReadableStream 解析 SSE（meta/delta/citation/done/error），支持停止生成、重新生成、超时 120 秒看门狗；固定声明“由 AI 生成，仅供参考”。
- 图表统一 ECharts 封装，按需注册，颜色取 `chart-1/2/3/ref`。
- 通知：登录后每 60 秒轮询未读与到期通知（Web Locks 单标签页轮询），浏览器通知由用户点击授权。

### 4.9 开发与构建

- `.env.development`：`VITE_API_BASE=/api/v1`、`VITE_AMAP_KEY=`（空则示意地图）。
- Vite 代理：`/api/v1`、`/admin/v1` → `http://127.0.0.1:8080`；`/hls` → MinIO（按接口返回地址，可直连）。
- 构建：`npm run build:user`、`npm run build:admin`；产物 `dist/`，带内容哈希，不发布 source map。
- 质量门：`npm run typecheck`、`npm run lint`、`npm run test`、关键流程 Playwright。
- 部署：Nginx 两个站点，`/api/v1`（AI 关闭缓冲、超时 180s）、`/admin/v1` 同源代理，未知路径回退 `index.html`。提供 `web/deploy/nginx.conf` 示例，不改现有 `zhimu-deploy`。

---

## 五、子代理分工（多代理并行）

### 5.1 依赖关系

```
F 基础基座（1 个，先行）
   ├─ U1…U7 用户端 7 组（并行）
   ├─ A1、A2 管理端 2 组（并行；依赖选定方案）
   ├─ B1、B2 服务端管理端接口（B1 方案 A/B、B2 仅方案 A）
   ├─ D 示例数据补齐（可与用户端并行）
   └─ Q 质检联调（最后，回派修复）
```

### 5.2 工作包

| 编号 | 范围 | 页面 | 交付物 |
|---|---|---|---|
| F | 工程脚手架、shared 基座、主题、zw-* 组件、请求层、认证、外壳、路由、菜单、状态页、登录页、工作台参考页、OpenAPI 类型生成、开发约定 | WU-A01、WU-T01、WU-U02、WA-S01 | 两应用可启动；真实登录；外壳；`web/docs/开发约定.md` |
| U1 | 账号与个人中心 + 通用 | WU-A02–A04、WU-U01 | 4 页 |
| U2 | 养殖场与成员 + 今日/日历/消息 | WU-F01–F04、WU-T02–T04 | 7 页 |
| U3 | 栏舍与存栏 + 繁育 | WU-L01–L05、WU-B01–B03 | 8 页 |
| U4 | 饲喂与称重 + 免疫用药消毒 | WU-G01–G03、WU-D01–D03 | 6 页 |
| U5 | 巡检与诊断 + 库存 | WU-I01–I04、WU-M01–M03 | 7 页 |
| U6 | 收支、出栏、行情、报表 | WU-E01–E07 | 7 页 |
| U7 | AI 助手与养殖课堂 | WU-K01–K04 | 4 页 |
| A1 | 审核中心 + 用户与系统（S01 除外） | WA-R01–R04、WA-S02–S06 | 9 页 |
| A2 | 内容运营 + 基础数据 | WA-C01–C05、WA-D01–D05 | 10 页 |
| B1 | 服务端：审核中心 R01–R04 + 平台概览/用户/养殖场 S02–S04 | — | 约 30 个 `/admin/v1` 接口 + Flyway + 单测 |
| B2 | 服务端：内容运营 C01–C05 + 基础数据 D02–D04 | — | 约 45 个接口 + 迁移 + 单测 |
| D | 示例数据：V9_11 免疫/用药/消毒，补齐审核/内容演示数据 | — | 1–2 个 `V9_xx` 脚本 |
| Q | 端到端联调、257 张原型截图抽样对比、缺陷清单、回派修复 | 全部 | `web/docs/验收报告.md` + 截图对比 |

### 5.3 协作规则（写进 `web/docs/开发约定.md`）

1. **文件所有权**：每个代理只写自己模块的 `views/<模块>/`、`stores/<模块>/`、`api/<模块>.ts`、`router/<模块>.ts`；`packages/shared` 与全局文件由 F 负责，其他代理通过消息提出修改请求，避免并行冲突。
2. **不碰 App 与 server**：除 B1/B2、D 明确授权外，任何代理不得修改 `app/`、`module_*`、`lib_*`、`server/`。
3. **接口以真实为准**：每个页面先读原型 + App 模块文档 + Web 模块文档 + 对应 Controller/VO，再写 store；字段拿不准时以服务端 OpenAPI/VO 为准。
4. **完成标准**：页面可操作、真实数据、四态齐全、角色视图正确、`npm run typecheck` 与 `npm run build` 通过；管理端页面必须注明数据来源（真实/演示）。
5. **汇报格式**：页面清单、调用接口清单、未解决问题、截图路径、已知缺口。
6. **冲突处理**：遇到 App 与原型/文档冲突，按 App 实现并记录到 `web/docs/冲突与取舍.md`；无法决定时停止并上报，不自行发明业务规则。

---

## 六、里程碑与验收

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 基座 | F 完成；两端可启动；真实登录；外壳 1:1；T01 接真实接口 | 登录 → 工作台截图与原型同布局；刷新页面会话恢复；切换养殖场 |
| M1 用户端 | U1–U7 完成 43 页 | 每页真实数据；员工/所有者双视角；四态；`build` 通过 |
| M2 管理端 | A1–A2 完成 19 页（+F 的 S01） | 登录/验证码/强制改密；已有接口页面真实数据；缺口页面标注 |
| M3 服务端补口（可选） | B1–B2 完成；D 补数据 | `/admin/v1` 接口冒烟；管理端切真实数据 |
| M4 验收 | Q 完成 | Playwright 关键流程通过；1440×900 截图对比；交付报告 |

**逐页验收清单**：默认状态、空状态、加载失败、无权/不存在；列表分页与筛选；主操作（新增/编辑/审核/登记）可提交；角色裁剪；数字/日期/单位格式；浏览器控制台无错误。

---

## 七、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 管理端服务端只有 39 个接口 | 15 页无法真实取数 | 方案选择（第八节）；演示适配层与真实接口同形状，后续只换 api 模块 |
| 示例数据缺口（免疫/用药/消毒 0 行） | D 模块页面为空 | D 代理补 V9_11；页面实现空状态 |
| 高德 JS Key/安全密钥缺失 | 地图选点不可用 | 默认示意地图 + 手动地址；Key 配置后启用 |
| 257 张原型截图逐一对比工作量大 | 视觉差异 | 关键页全量、其余页默认态抽样；统一截图脚本 |
| 多代理并行改公共文件 | 冲突 | 文件所有权 + F 统一维护 shared；合并前 typecheck |
| SSE/AI 需要外网 Key | 问答不可用 | Key 已配置；失败时按错误态展示，不阻塞页面 |
| npm 首次安装耗时 | 启动慢 | F 先安装并提交 lockfile；后续代理复用 |

---

## 八、待你确认的一个分叉：管理端数据来源

**方案 A（全量补口）**：新增 B1/B2 两个服务端工作包，补齐管理端 15 页所需接口（约 75 个）与迁移（通知表等），管理端 20 页全部真实数据。真正三端闭环，但服务端工作量与风险最大，需要恢复此前暂停的管理端开发。

**方案 B（核心补口，推荐）**：服务端只补“运营核心”——审核中心 R01–R04 + 平台概览/用户/养殖场 S02–S04（约 30 个接口，B1 一个工作包）；管理端其余 10 页（内容运营 C01–C05、基础数据 D02–D04）按原型实现，数据走演示适配层并标注。审核中心是平台运营的关键路径，先做真实；内容运营可后补。整体工作量比 A 小一半，又保住“审核能真用”。

**方案 C（不改服务端）**：本轮不改服务端。管理端 20 页全部按原型实现；5 页接真实接口；其余 15 页接“演示适配层”（数据形状按管理端服务端文档，页面显著标注“演示数据”，api 模块一行切换）。交付服务端缺口清单，后续补接口只替换 api 层。范围可控、最快看到完整 Web，但审核中心等页面暂时不是真实业务数据。

**方案 D（用户端优先）**：本轮只交付用户端 46 页 + 管理端登录/管理员/日志/字典 5 页；其余管理端待服务端恢复后另起一轮。最快、风险最低，但原型中的管理端未完整交付。

---

## 九、交付物

1. `web/` 完整工程（两端 + shared + e2e + 部署配置），两个应用可独立构建。
2. `web/docs/`：开发约定、接口与页面映射、冲突与取舍、缺口清单、运行与部署手册、验收报告。
3. `docs/Web端实施方案.md`（本文）、`docs/api/openapi.json`、`docs/api/endpoints.csv`。
4. 真实联调记录：登录/刷新/切换养殖场/各模块主流程；1440×900 截图。
5. 不修改 App；服务端改动仅限选定方案允许的范围，并在报告中列明。

---

## 十、启动方式（交付后）

```powershell
# 1. 中间件 + 服务端（已在运行则跳过）
cd D:\Android_Program\SLMP\server\tools; .\start-dev.ps1
cd D:\Android_Program\SLMP\server; mvn -pl zhimu-app -am spring-boot:run

# 2. Web
cd D:\Android_Program\SLMP\web
npm install
npm run dev:user     # http://127.0.0.1:5173
npm run dev:admin    # http://127.0.0.1:5174
```

账号：用户端 `13800006620 / Zmt12345`（所有者）、`13700005678`（员工，验证码 123456）；管理端 `zhoumin / Zmt12345`（首登改密）。
