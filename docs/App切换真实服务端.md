# App 从 Mock 切换到真实服务端

> 2026-10-23。适用于 debug 包联调；release 包默认不走 Mock。
> 服务端接口已覆盖 App 的 319 个路径，但部分页面依赖的演示数据仍在补齐，见第四节。

## 一、服务端准备

1. 启动 MySQL、Redis、MinIO（本机用 `server/tools/start-dev.ps1`）。
2. 配置 `server/.env.local`：`DB_*`、`REDIS_*`、`MINIO_*`、`JWT_USER_SECRET`、`JWT_ADMIN_SECRET`，
   需要 AI/短信/推送时再配 `DASHSCOPE_API_KEY`、`SMS_*`、`JPUSH_*`、`AMAP_WEB_KEY`。
3. 启动服务端：

   ```powershell
   cd server
   mvn -pl zhimu-app -am spring-boot:run
   # 或
   mvn -pl zhimu-app -am package -DskipTests
   java -jar zhimu-app/target/zhimu-server.jar
   ```

4. 验证：`GET http://127.0.0.1:8080/api/v1/ping` 返回 `code=0`。
5. 演示时钟（可选）：`ZMT_FIXED_DATE=2026-10-19`，让“今天”和 Mock 一致；生产不要配。

> 已执行过 `V2_07` / `V9_10` 旧版本的本地库，脚本内容改过会导致 Flyway 校验失败：
> 删除 `zhimu` 库重建，或在 `server` 下执行 `mvn -pl zhimu-app flyway:repair` 后重启。

## 二、App 配置

编辑 `local.properties`（不提交）：

```properties
sdk.dir=D\:\androidsdk
zmt.baseUrl=https://你的服务端域名/
zmt.useMock=false
zmt.mockToday=
amap.key=你的高德 Android Key
jpush.appKey=debug 包极光 AppKey
```

同时清空测试直连，让 AI/短信走服务端：

```properties
ai.dashscopeKey=
ai.deepseekKey=
aliyun.accessKeyId=
aliyun.accessKeySecret=
```

然后重新构建安装 debug 包：

```powershell
.\gradlew :app:assembleDebug
```

示例账号：`13800006620 / Zmt12345`（所有者王建国），`13700005678`（员工李明，Mock 短信验证码 123456）。
员工账号用于验证 `@ManagerView` 金额/单价裁剪和栏舍权限。

## 三、必须使用 HTTPS

App 的网络安全配置 `lib_common/src/main/res/xml/common_core_network_security_config.xml`
是 `cleartextTrafficPermitted="false"`，**不能直接连 `http://192.168.x.x:8080`**。

本机联调三选一：

1. 用内网穿透/测试域名提供 HTTPS（推荐，和正式环境一致）。
2. Nginx 反代本机 8080 并配证书。
3. 临时给 debug 包加 cleartext 例外（属于 App 改动，需要确认；不要进 release）。

Web 端同源访问 `/api/v1`，不受此限制。

## 四、当前演示数据口径

服务端数据来自 `db/sample/V9_01–V9_12`，不是生产数据。联调时以下页面可能与 Mock 不完全一致：

| 页面 | 现状 |
|---|---|
| 首页/待办/消息 | 基本对齐；撤回通知现在可见 |
| 栏舍/批次/存栏 | 对齐；时间线补了 FEED/WEIGH/INSPECT/DRUG |
| 繁育 | 1001 是猪场，孵化记录只在 1002 西岭鸡场；验证孵化请切 1002 |
| 饲喂/称重 | 对齐；饲料扣减已接模块09 |
| 巡检/异常诊断 | 对齐；AI 诊断需要服务端 AI Key，未配置时状态 FAILED |
| 免疫/用药/消毒 | 接口完整；部分疫苗/兽药/消毒剂批号按 V9_10 新补，健康记录可现场登记 |
| 库存 | 39 接口完整；V9_10 批号从 7 增到 12，库存总览数量会高于模块09自己的 Mock |
| 财务/报表 | 39 接口完整；AI 摘要未配 Key 时规则降级/FAILED |
| AI/课堂 | 19 接口完整；课程视频为可直接播放的测试流，未做 HLS 转码 |

## 五、常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 所有请求 401/40104 | 令牌过期或服务端 `JWT_USER_SECRET` 变了；重新登录 |
| 403/40303 | 当前角色无权；员工账号只能看负责栏舍 |
| 403/40304 | 栏舍不在员工负责范围 |
| 403/40305/40306 | 养殖场审核中/已停用/被冻结 |
| 404/40401 | 记录 ID 不属于当前 `X-Farm-Id`，或传了耳号而不是 `animal_id` |
| 500/50001 | 用响应的 `trace_id` 查 `server/logs/zhimu-server-error.log` |
| 图片/视频 404 | MinIO 未启动，或 `files.{域名}` 反代未配 |
| 推送收不到 | debug 包 AppKey 与服务端 `JPUSH_APP_KEY` 必须是同一个极光应用 |
| AI 一直“正在思考” | 检查 `DASHSCOPE_API_KEY`、Nginx 对 `/api/v1/ai/` 是否关闭缓冲 |

## 六、联调顺序建议

1. 登录/刷新/退出 → 养殖场列表/切换 → 首页
2. 栏舍/批次/存栏 → 饲喂/称重 → 巡检/异常
3. 繁育 → 免疫/用药/消毒 → 库存
4. 财务/出栏/报表 → AI/课堂
5. 员工账号回归：金额隐藏、栏舍权限、40303/40304
6. 修改申请：U01 表单 → 提交 → 管理端审核 → 回写（handler 合并后）
