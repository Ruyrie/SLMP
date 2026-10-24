# Web 管理端服务端补口 · 公共开发说明（给子代理）

> 本轮目标：把 Web 管理端 20 个页面依赖的 `/admin/v1` 接口补齐，使服务端**全部功能可用**。用户端 `/api/v1` 已交付，不得改变其行为。

## 一、工作方式

- 每个子代理在**独立 worktree** 中工作（分支已建好），互不干扰；完成后在各自 worktree 提交（commit），**不要 push、不要 merge、不要切分支**。
- 只改 `server/` 下自己负责的包与本模块文档；不碰 `app/`、`module_*`、`lib_*`、`web/`。
- 不修改已执行过的 Flyway 脚本（`V1_00`–`V9_12`）；需要结构变更时新增版本，版本号由主控分配。
- 不修改 `server/docs/服务端交付状态.md`（由主控统一更新）。
- 不改用户端接口的路径、字段、行为；管理端 Service 优先复用 `biz/*/api/*AdminService` 中已有的方法。

## 二、开工前必读

1. `server/docs/服务端开发约定.md`（分包、统一响应、snake_case、分页、错误码、权限、留痕、幂等、Flyway）。
2. 本模块的服务端技术准备文档：`第六组_智牧通_UI设计与技术准备文档/服务器端/管理端/管理端-0X-*.md`（接口编号 ADM-xxx、字段、规则、错误码、表结构）。
3. 对应 Web 管理端 UI 文档：`.tech-prep/text/Web端_管理端_Web管理端-0X-*.md`（页面要什么数据、什么操作、状态与文案）。
4. 现有代码写法：
   - 管理端框架：`server/zhimu-biz/src/main/java/com/zhimu/server/admin/system/`（Controller/Service/VO/Query/Convert、`AdminContext`、`@AdminPermission`）。
   - 审核写入 Service：`biz/farm/api/FarmAuditAdminService`、`FarmChangeAdminService`、`FarmPlatformService`；`biz/account/api/AccountAdminService`、`AccountRecoveryAdminService`；`biz/common/api/ChangeRequestAdminService`。
   - 基础数据写法：`admin/base/`（CategoryAdminController、DictAdminController、BreedAdminService、StdAdminService）。
   - 文件与媒体：`biz/common/FileService`、`zhimu-integration` 的 `ObjectStorage`、`MediaProcessor`（MinIO、FFmpeg）。
   - 操作留痕：`OperationLogRecorder`、`@OpLog`、`AdminLogTexts`。
5. 表结构：`server/zhimu-app/src/main/resources/db/migration/*.sql`；权限点：`V2_01__init_admin_rbac.sql`（37 个 `WA-xx:VIEW/HANDLE` 已存在）。
6. 真实接口：服务端已在 8080 运行，可 `GET http://127.0.0.1:8080/v3/api-docs` 核对；OpenAPI 快照在 `docs/api/openapi.json`、`docs/api/endpoints.csv`。

## 三、接口硬性要求

- 路径统一 `/admin/v1/...`；Controller 用 `@RestController` + `@RequestMapping`，写操作加 `@AdminPermission("WA-R01:HANDLE")` 等权限点。
- 统一响应 `ApiResponse<T>`；列表 `PageVO<T>`（list、total、page、size、has_more、tab_counts 按需）；page 从 1 开始，size 默认 20，最大 100。
- JSON 一律 snake_case（Jackson 全局已配置）；VO 字段名、枚举编码、`xxx_text` 与模块服务端文档一致；布尔包装类型 `Boolean isXxx`。
- 时间用 `BeijingTime.now()/today()`；datetime `yyyy-MM-dd HH:mm:ss`、date `yyyy-MM-dd`。
- 错误用 `BizException(ErrorCode.XXX, "文案")`；文案按模块服务端文档逐字；40401 记录不存在，40902 版本冲突，40903 状态已变化。
- 写操作加 `@Idempotent`（如适用）、`@RateLimit`（登录类已存在），并记录操作日志（`OperationLogRecorder` 或 `@OpLog`），日志含操作人、对象、原因、前后值。
- 不返回 204；空列表 `[]`，空对象 `{}`，data 不为 null（除非文档明确允许）。
- 管理端鉴权由框架 Cookie 拦截器完成，Controller 里取 `AdminContext.requireAdminId()`；不要自行解析令牌。
- 员工/用户端字段裁剪与管理端无关，但仍不要泄露密码哈希、恢复码明文、完整手机号（按文档打码）。
- 列表查询写 `query` 对象 + MyBatis-Plus Wrapper 或 `resources/mapper/**/XxxQueryMapper.xml`；跨表统计可用 XML。
- 分页统计与角标（待审核数）必须和列表条件一致。

## 四、测试与验收

- 每个模块至少：`mvn -q -B -pl zhimu-biz -am -DskipTests compile` 通过。
- 关键 Service 写单元测试（JUnit5 + Mockito + AssertJ，固定时钟 `BeijingTime.setClock`），至少覆盖：列表查询、状态流转、版本冲突、权限点。
- 不跑全量 `mvn verify`（主控统一跑）。
- 提供一个可执行的冒烟脚本或 curl 清单（可放 `server/docs/` 或 `server/tools/smoke/`），列出登录管理员后的调用顺序；验证时至少确认接口返回 200 且 JSON 结构正确。管理员登录需要图形验证码：dev 环境可从 Redis 读 `admin:captcha:{id}`，或用 `AdminDevSupport` 的现有做法。
- 若新增 Flyway 脚本，确保在空库和现有库都能执行（只新增，不改旧脚本）。

## 五、交付物

1. 代码（Controller / Service / Query / Mapper / VO / Convert / Request），包名按 `com.zhimu.server.admin.<模块>`；需要的跨模块只调用对方 Service。
2. 新增 Flyway 脚本（如需要）。
3. **接口契约文档**：`server/docs/管理端接口-<范围>.md`，逐接口列出：ADM 编号、方法、路径、权限点、请求参数（含类型/必填/说明）、响应示例（真实字段）、错误码、页面。前端会照此实现，字段名必须准确。
4. 单测与冒烟清单。
5. 完成报告：接口清单、改动文件、未实现/待确认项、测试结果、commit 哈希。

## 六、完成定义

- 前端可以只按契约文档实现页面，不需要再问字段。
- 列表/详情/主操作/角标/导出（如文档要求）都能用。
- 不做“只返回空数组”的占位接口；没有数据的表返回空列表，但查询与分页逻辑完整。
- 与用户端既有接口、既有测试不冲突。
