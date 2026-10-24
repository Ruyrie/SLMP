# AI 会话管理 + 今日饲喂任务补生成

本次补齐 K01 会话管理缺口（新建 / 重命名 / 删除），并增加“今日饲喂任务”手动补生成与启动补生成。只改 Java 与文档，不改 `web/`、不改管理端、未新增 Flyway。

## 一、AI 会话管理

### 1. 新建空会话

`POST /api/v1/ai/sessions`（当前用户 + 当前养殖场，`X-Farm-Id`）

```json
{
  "title": "可选，1–40 字；不传或空白时显示“新对话”",
  "batch_id": 3001
}
```

`batch_id` 可省略。返回 `AiSessionDetailVO`（含 `id`、`title`、`batch_link_state`、`batch`、`last_round_no=0` 等）。

- 不传 `title` 时入库为“新对话”，列表与详情按该标题展示。
- 传入 `batch_id` 时复用 K04 的批次权限校验：员工只能关联负责栏舍的在栏批次；不存在返回 40401，栏舍越权返回 40304。
- 新建空会话 `round_count = 0`，`last_asked_at` 为当前时间，会出现在会话列表“今天”分组。
- 之后在 App 里直接使用该 `session_id` 提第一个问题：若标题仍是“新对话”，自动替换为首问摘要；用户自定义标题不会被覆盖。

### 2. 修改会话（标题 / 关联批次）

`PATCH /api/v1/ai/sessions/{id}`

```json
{ "title": "新的标题" }
```

```json
{ "batch_id": 3001 }
```

```json
{ "batch_id": null }
```

```json
{ "title": "新的标题", "batch_id": 3001 }
```

- `title`：去首尾空白后 1–40 字；为空/全空白返回 40001，超长返回 40001。
- `batch_id`：**字段是否出现在请求体里**决定语义。只传 `title` 时保留原关联批次；显式传 `batch_id: null` 才取消关联。旧接口 `{"batch_id": 3001|null}` 行为不变。
- 只能改本人会话：他人会话或不存在统一 40401；已删除会话 40401。
- 空 body 或两个字段都不传：不修改任何内容，返回当前详情。

### 3. 删除会话

`DELETE /api/v1/ai/sessions/{id}`

- 只能删本人会话；他人/不存在返回 40401；重复删除幂等返回成功。
- 返回 `{"code":0,"message":"ok","data":null}`。

**逻辑删除实现说明（未新增列 / 未新增 Flyway）**

`t_ai_session` 与 `t_ai_message` 现有表结构没有 `deleted` 列（见 `V1_11__init_ai.sql`，且模块 11 设计文档明确“不设 deleted”）。为满足“软删除且不改表”：

| 表 | 标记字段 | 删除值 | 读取侧处理 |
| --- | --- | --- | --- |
| `t_ai_session` | `batch_link_state` | `DELETED` | 列表过滤；详情、消息、PATCH、提问、重新生成、删除以外的入口一律 40401 |
| `t_ai_message` | `status` | `DELETED` | 会话删除后消息不可再通过用户端读取（K08 详情、K09 评价、重新生成统一 40401）；后台生成收尾/失败/重试不会把墓碑状态改回来 |

- 历史会话/消息行仍在库中，管理端问答查询不增加过滤，历史记录不丢。
- 该方案是“状态字段复用”，后续如果产品要求标准逻辑删除，建议单独新增 `deleted` 列与 Flyway 脚本，再把 `DELETED` 标记迁移过去。

### 4. 列表标题

`AiSessionItemVO.title` 已存在，本次复用；空会话（`round_count = 0`）与标题缺失时统一显示“新对话”，详情 `title` 同样兜底。

## 二、今日饲喂任务生成

### 1. 手动补生成

`POST /api/v1/feed-tasks/generate-today`（仅 `OWNER` / `MANAGER`，`X-Farm-Id`）

返回：

```json
{ "code": 0, "message": "ok", "data": { "generated": 4, "skipped": 2 } }
```

- 只为**当前养殖场**的**在养批次**按饲喂方案生成**今天**的任务；复用 `FeedTaskService.generateDailyForFarm(farmId, today)`，与每日 00:05 的 `FeedTaskGenerateJob` 同一套计算，不复制逻辑。
- 已存在（同批次 + 栏舍 + 日期 + 餐次）的任务跳过，不覆盖已生成任务、不改历史数据。
- `generated`：本次新插入的任务数；`skipped`：命中去重条件而跳过的候选餐次数（并发撞唯一键也计入）。
- 员工调用由 `@FarmRoles` 返回 40303。

### 2. 启动后幂等补生成

`FeedTaskStartupBackfill` 监听 `ApplicationReadyEvent`：

- 对每个有在养批次的养殖场，先查 `t_feed_task` 当天是否已有任意任务；
- 只有“今天一条任务都没有”的养殖场才调用 `FeedTaskGenerateJob.generateForFarm(farmId, today)`（同时刷新模块 03 待办）；
- 只插入当天任务，不更新/删除历史任务；单场失败只记 WARN，不影响启动；
- 与 00:05 定时任务、手动接口共用同一生成方法，多实例并发由 `uk_feed_task` 兜底。

## 三、涉及文件

新增：

- `zhimu-biz/src/main/java/com/zhimu/server/biz/ai/request/AiSessionCreateRequest.java`
- `zhimu-biz/src/main/java/com/zhimu/server/biz/feeding/job/FeedTaskStartupBackfill.java`

修改（服务端）：

- `biz/ai/controller/AiSessionController.java`
- `biz/ai/service/AiSessionService.java`
- `biz/ai/service/impl/AiSessionServiceImpl.java`
- `biz/ai/service/impl/AiAskServiceImpl.java`
- `biz/ai/service/impl/AiMessageServiceImpl.java`
- `biz/ai/service/impl/AiRoundService.java`
- `biz/ai/convert/AiSessionConvert.java`
- `biz/ai/request/AiSessionBatchRequest.java`
- `biz/ai/support/AiCodes.java`
- `biz/ai/support/AiText.java`
- `biz/feeding/controller/FeedTaskController.java`
- `biz/feeding/service/FeedTaskService.java`
- `biz/feeding/job/FeedTaskGenerateJob.java`
- `biz/feeding/vo/FeedTaskVO.java`
- 测试：`zhimu-biz/src/test/java/com/zhimu/server/biz/ai/controller/AiControllerMockMvcTest.java`、`zhimu-biz/src/test/java/com/zhimu/server/biz/feeding/controller/FeedingControllerMappingTest.java`

## 四、验证方式

1. 编译：`mvn -q -B -pl zhimu-biz -am -Dmaven.test.skip=true compile`（由主控统一 clean 构建/重启，避免并行开发时清空 target）。
2. 会话：
   - `POST /api/v1/ai/sessions` 建空会话 → 列表首条 `title` 为“新对话”；
   - `PATCH` 只传 `title` → 标题变化、`batch_link_state` 不变；
   - `PATCH` 只传 `batch_id:null` → 批次解除，且库里 `batch_id`、`batch_snapshot_json` 变为 NULL、`batch_link_state='NONE'`（不能只看响应，MyBatis-Plus 默认跳过 null 字段的问题已在本接口用显式 SET 处理）；
   - `DELETE` 后再 `GET /sessions` 不含该会话，`GET /sessions/{id}` 返回 40401，库中行仍存在且 `batch_link_state='DELETED'`、消息 `status='DELETED'`；
   - 用该 `session_id` 再提问 → 40401；用 App 原有“首问自动建会话”流程 → 正常。
3. 饲喂：
   - 空态下 `POST /api/v1/feed-tasks/generate-today` → `generated>0`，`GET /feed-tasks/today` 出现任务；
   - 再次调用 → `generated=0`、`skipped>0`，任务数不变；
   - 重启服务（今天已有任务）→ 启动日志无新增生成，任务不重复。
   - 注意：服务重启时启动补生成会先把“今天完全没有任务”的养殖场补齐，因此重启后再调手动接口通常看到 `generated=0`、`skipped>0`；这是幂等预期。若要验证手动接口的 `generated>0`，需在测试库先清掉当天的 `t_feed_task`（仅限演示/测试库）。
