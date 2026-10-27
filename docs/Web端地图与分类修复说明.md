# Web 端地图、分类与免重启修复说明（2026-10-25）

## 一、问题现象

1. 管理端「平台概览 → 养殖场分布」是示意地图：不能缩放/拖动，+/−/定位按钮没有行为；切品类后地图和右侧品类分布对不上，点地区后的明细也不带品类条件。
2. 用户端侧边栏「养殖场信息」「成员管理」点击无反应，控制台报 `Missing required param "farmId"`。
3. 品类筛选只按畜种（猪/禽/牛/羊）分组，和用户端建档时选的品类（育肥猪、种猪、肉鸡、肉牛）不是一套口径；品类分布、地图、列表三处显示不一致。
4. 选点地图瓦片失败时只显示空白底图，没有正确降级；需要刷新页面/重启客户端才能继续保存。
5. 后台审核、新建品类等操作完成后，用户端要刷新页面才能看到最新数据。

## 二、修复内容

### 1. 管理端分布地图改为真实可交互地图

- 新增 `apps/admin/src/views/system/components/AdminFarmMap.vue`，Leaflet 动态加载，只进概览页的异步 chunk。
- 底图优先高德栅格瓦片（中文地名、GCJ-02，与 App 高德 SDK 上报的经纬度一致），2.5 秒内没有瓦片或连续 tileerror 时自动切 OpenStreetMap；两者都不可达时保留聚合点并提示，仍可点击查看地区明细。
- 支持滚轮/双击/缩放按钮、拖动、比例尺、回到全部聚合点；聚合点为真实经纬度，显示省名、总数和审核状态比例条。
- 品类下没有已通过/待审核养殖场时，地图保留底图并在中央显示浮动空态提示，不再用整张白色遮罩把地图盖住。
- 修复列表选中项/悬停项的不透明 overlay 盖住文字的问题（之前选中「种猪（0）」等选项时文字会显示成空白）。
- 点击聚合点打开地区明细浮层，点地图空白处关闭。

### 2. 品类口径统一

- 服务端 `GET /admin/v1/overview` 的 `category_dist` 改为按 `t_category` 的品类返回：`category_id`、`category_name`、`species`、`species_text`、`count`、`ratio`；没有已通过养殖场的品类也会返回 0，供筛选下拉使用。
- `GET /admin/v1/overview/farm-map` 新增 `category_id` 过滤；`GET /admin/v1/overview/regions/{code}` 新增 `category_id`，地区明细的已通过/待审核/已冻结计数和待审核列表只统计该品类。
- 管理端下拉改为真实品类（育肥猪、种猪、肉鸡、肉牛），地图图例、品类分布、地区明细、列表跳转共用同一个 `category_id`；筛选时品类分布只显示该品类，占比按筛选后的总数计算。
- 地图、地区明细、列表、审核页的省份条件统一用 `adcode` 前 2 位；「查看养殖场列表」「去审核」会带上省份和品类条件，列表/审核页显示可清除的筛选标签。
- 地图的已通过/待审核/已冻结统计互斥：冻结场只计入冻结，不再同时计入待审核。

### 3. 侧边栏带参数路由

`packages/shared/src/layouts/shell.ts` 新增 `menuRouteParams` / `missingRouteParams`：菜单点击前根据路由 `path` 自动补当前养殖场 `farmId`，缺参数时回退「我的养殖场」，不再抛 `Missing required param`。新增 `tests/user-menu-navigation.test.ts` 覆盖。

### 4. 瓦片失败降级

- 管理端和用户端都避开了 `errorTileUrl` 透明占位图被误记为 `tileload` 的问题；用户端预检依次尝试高德与两张 OSM 瓦片，运行中瓦片失败或全部不可达时切示意地图并保留手动填写经纬度。
- 用户端选点地图优先高德瓦片，预检预算放宽到 3 秒，避免网络慢时误降级。

### 5. 免重启生效

- 用户端根组件监听 `focus` / `visibilitychange`，切回页面时静默 `farm.load(true)`，触发当前页的 `useFarmReload` 重新取数；后台审核通过、成员/养殖场状态变化无需重启或整页刷新。
- 管理端平台概览在页面重新可见时静默刷新；品类、地图、统计、趋势一起更新。
- 保存后仍由各 store 主动刷新列表；不依赖修改源码或重启服务端。

## 三、验证

### 服务端

```powershell
cd D:\Android_Program\SLMP\server
mvn -q -B -pl zhimu-biz -am test
```

结果：`zhimu-biz` 及依赖模块 **1233 个测试通过，0 失败**；新增/修改的 `OverviewServiceImplTest` 6 个测试覆盖品类分布、`category_id` 过滤、地区明细品类过滤和非法品类编号。

### Web

```powershell
cd D:\Android_Program\SLMP\web
npm run typecheck
npm run test
npm run build:user
npm run build:admin
```

结果：两端 `vue-tsc` 通过；Vitest **26 个测试通过**；两端生产构建成功。

### 浏览器联调（本地 5173/5174 + 8080）

- 管理端登录后概览：地图加载高德瓦片，聚合点 `内蒙古 3`，图例「已通过 2 · 待审核 1 · 已冻结 0」；缩放按钮改变瓦片层级；切「育肥猪」后聚合点 1、品类分布 1（100.0%）；点聚合点地区明细只显示育肥猪，已通过 1、待审核 0。
- 「查看养殖场列表」带 `province_code=15&category_id=101`，列表只显示东山猪场，页面显示省份/品类筛选标签。
- 切「肉牛」后聚合点 1（待审核），品类分布提示“该品类还没有已通过的养殖场，另有 1 个待审核”；「去审核」带 `category_id=301`，审核页只显示北湾牛场。
- 用户端侧边栏「养殖场信息」「成员管理」「我的养殖场」「我的修改申请」「个人中心」全部可打开，控制台无 `Missing required param`。
- 用户端新建养殖场：地图选点 + 资质图上传 + 提交后列表立即出现新场，审核状态为待审核；经纬度、地址、行政区划均写入数据库，不需要重启服务或刷新页面。
- 管理端新增品类后，用户端新建养殖场下拉立即出现该品类（不重启服务端）。
- 屏蔽高德瓦片时管理端自动切 OSM；再屏蔽 OSM 时保留聚合点和“底图暂时不可用”提示；用户端两张瓦片都屏蔽时降级示意地图并给出提示。

## 四、公网部署（2026-10-25）

已把本轮修复同步到演示服务器 `http://8.208.12.217/`：

| 项目 | 值 |
|---|---|
| 服务端 jar | `/opt/zhimu/zhimu-server.jar`，SHA-256 `cd77345d786d1a2e3b95a5dc8139a814179cc7b4c1a8aa7dddd118433f2c561b`，116,116,659 字节 |
| 用户端首页 | `/var/www/zhimu/index.html`，MD5 `c62cfe2015a4e2b2d1970b611600d899` |
| 管理端首页 | `/var/www/zhimu/admin/index.html`，MD5 `2ec913e30a30977aef109f8a0735e93b` |
| 更新前备份 | `/opt/zhimu/backups/web-map-fix-20261025-165002`（旧 jar/首页）、`...-165130`、`...-170826`、`...-171315`（各阶段旧网页） |
| 数据库 | Flyway 9.28，启动时 `Schema zhimu is up to date` |
| 服务状态 | `zhimu.service` active，`/api/v1/ping` 返回 0 |

云端验证：用户端侧边栏 5 个入口可打开；`/farms/new` 选点地图加载高德瓦片；管理端登录页与新资源正常加载；OpenAPI 已包含 `CategoryDist.categoryId/categoryName` 和 `regions/{code}?category_id`。管理端登录密码以当前线上密码为准。

## 五、涉及文件

- 服务端：`OverviewAdminController`、`OverviewService(Impl)`、`AdminOverviewQueryMapper(.xml)`、`FarmMapQuery`、`CategoryDistRow`、`OverviewVO`、`OverviewServiceImplTest`。
- 管理端：`OverviewView.vue`、`AdminFarmMap.vue`、`leaflet-runtime.ts`、`stores/system/overview.ts`、`stores/system/farms.ts`、`stores/audit/farmAudit.ts`、`AdminFarmsView.vue`、`FarmAuditView.vue`、`api/system.ts`、`package.json`。
- 用户端：`App.vue`、`FarmMapPicker.vue`。
- 共享：`layouts/ZwShell.vue`、`layouts/shell.ts`、Material Symbols 子集（补 `zoom_out_map`）。
- 文档：`web/README.md`、`web/docs/验收报告.md`、本文件。
