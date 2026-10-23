# 智牧通 App 接口对接与待确认清单

用途：App 模块01–11 已完成，数据全部来自 Mock。第一部分供服务端对齐 App 正在使用的字段、类名与参数；第二部分供小组过目 App 自定的做法。
原则 1：同一接口在两份文档中写法不同时，以接口所属模块的服务端文档为准（例：API-M27 属模块09，`purpose` 取 `VACCINATE` / `MEDICATE` / `DISINFECT`，`DrugLotOptionVO.stock` 为 `QtyVO`）。
原则 2：UI 文档没给的文案、原型没画的页面、文档之间的冲突，代码保持现在的做法，本清单写明当前做法，由小组过目。
原则 3：已由用户拍板的事项列在文末“已定事项”，无需再确认。
说明：类名照代码写；字段名写 JSON 名（代码中 Gson 按下划线与驼峰互转）。

## 第一部分 给服务端：需要对齐或补充的接口

### 公共

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-U09 品类品种树 | `BaseDataApi.getCategories` → `List<CategoryVO>`，品种含 `incubation_days` | 确认字段与管理端-03 一致 |
| API-U10 字典 | `getDicts(dict_type, species, category_id)` → `List<DictItemVO>` | 确认字段与管理端-03 一致 |
| API-U14 修改记录 | `getRecordLogs(biz_type, biz_id)` → `List<RecordLogVO>`，不分页 | 确认不分页 |
| API-U01 / U02 修改申请 | `ChangeRequestFormVO`、`ChangeRequestVO`；提交 `new_value`、`resubmit_of_id` | 确认与管理端-01 一致 |
| 推送附加字段 | `PushExtras`：`route`、`farm_id`、`biz_type`、`biz_id`、`message_id`、`push_id`、`todo_id`、`channel` | 各模块需另带的字段见模块06、08、10 |

### 模块01 账号与个人中心

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-A01、A03 的 40904 | 无 `data.reason`；message 含“已经注册”时才给“去登录” | 补 `data.reason`，区分“已注册”与“注销未满 30 天” |
| API-A03、A11、A13 幂等重试 | 响应丢失后用同一幂等键重试 | 明确重试时能否再次返回恢复码明文（明文不进幂等缓存） |
| API-A19 `CancellationPreviewVO.block_reasons` | `List<String>`，只知道 `OWNED_FARM_ACTIVE`；横幅由处置情况与 `cancel_available_date` 推导 | 给出 CANCEL_BLOCK_REASON 完整取值 |

### 模块02 养殖场与成员管理

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| 客户端暂定类名（module_farm） | `ChangeBriefVO`、`ChangeSnapshotVO`、`MemberRemoveResultVO`、`TransferFarmVO`、`TransferCandidateVO`、`MemberActionsVO` | 对齐类名与字段 |
| 客户端暂定类名（lib_common） | `CategoryVO`、`MediaItemVO` | 对齐类名与字段 |

### 模块03 今日首页、日历待办与消息中心

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-T03、T05、T12、T14、T17 响应 | 暂定 `TodoCalendarResult`、`TodoWeeklyResult`、`MessagePageVO`、`ReadAllResult`、`MessageDeleteResult` | 给出类名 |
| `*_names` 字段（如 `OwnerMetricsVO.warning_names`） | 按 `List<String>` | 确认类型 |
| `MessageBriefVO.date_group` | 按 `TODAY` / `YESTERDAY` / `EARLIER` 分组 | 写明取值 |
| API-T20 推送别名 | App 不调用 SDK 设别名 | 确认由 T20 在服务端绑定 |
| 厂商通道点击 | 统一打开 `PushRouterActivity`，action `com.zhimu.slmp.action.PUSH_OPEN` | 在推送中指定该 Intent（服务端 2.8.4 只写了开启厂商通道） |
| 日历“全部养殖场”处理他场待办 | 不切换养殖场直接提交，按服务端判权；进登记页时才切换 | 确认允许不切换直接处理 |

### 模块04 栏舍与存栏

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-L14 扫码解析 | 传 `qr_content`（扫码原文，≤200 字）或 `batch_no` → `BatchLookupVO` | 确认传原文（03 号 8.7 节、服务端 2.7 节写“取令牌调用”） |
| `ProductionOverviewVO` | L01 预警卡取 `reminder_total`；`warning_count` 未使用 | 确认 `warning_count` 用途（与模块03 T01 口径） |
| `TimelineItemVO.detail` | `List<KeyValue>`，JSON 暂按 `key`、`value` | 确认字段名 |

### 模块05 繁育管理

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| `BreedingRecordVO.version` | 客户端补充，`Integer` 可空；API-B13、B16、B21 修改时提交 | 在 VO 中补 `version` |
| 档案表单品种候选 | 取 API-U09 中同畜种、用途为种用且设有妊娠期的品种 | 确认用 U09，或给出接口编号 |
| `IncubationDetailVO.timeline` | 每步“本步数值”暂取详情顶层字段 | 列出每步数值的字段名 |
| B07 入孵推算孵化期 | `FlockOptionVO.breed_id` 查 U09 品种的 `incubation_days` | 确认两个字段都返回 |

### 模块06 饲喂与生长称重

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-G23 `version` | `WeighCycleRequest.version`；`WeighFormVO`、`WeighCycleVO` 无此字段，暂不传 | 给出 `version` 的返回位置 |
| 投喂修改模式的选项 | `FeedRecordDetailVO` 无 `feed_options`、`executor_options`；按 `task_id` 调 API-G03 取 | 确认已登记任务仍返回这两项 |
| `FeedStdStageVO.meal_times` | 按 `HH:mm` 字符串数组 | 确认元素类型 |
| 称重提醒推送 | `biz_id` 为 `t_batch_growth.id`；G05 需 `batch_id`，没有时先选批次 | 推送或 API-T15 另带 `batch_id` |

### 模块07 日常巡检与异常上报 AI 诊断

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-I06 巡检模板 | 用 `template` 内的巡检项，不调 API-U13 | 确认无需调 U13 |
| `InspectionFormVO.farm_latitude`、`farm_longitude` | 客户端暂定，用于登记前现场判定 | 补字段，或确认 `FarmBriefVO` 含 `latitude`、`longitude` |
| `ReportFormVO.ReportBatch.breed_title`、`day_age` | 客户端补充，用于 AI 诊断提示词，可不返回 | 确认是否返回 |
| `PenInspectionRowVO.result_summary` | 客户端补充（“正常”或异常项，如“粪便 水样”），可不返回 | 确认是否返回 |
| 员工打开他人当天的记录 | API-I06 MODIFY 返回 40303 时按只读显示 | 确认错误码 |

### 模块08 免疫、用药与消毒

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| API-M27 批号下拉 | `purpose` 取 `VACCINATE` / `MEDICATE` / `DISINFECT`，`DrugLotOptionVO.stock` 为 `QtyVO` | 已按此原则统一（以模块09 服务端文档为准） |
| D07、D08 栏舍选择 | 复用 API-L07（`usage=OPTIONS`）→ `PenOptionVO` | 确认只含本场启用中栏舍 |
| IMMUNE_MISSED 消息 | route 为 D01，D01 按 `batch_id` 加载 | `biz_id` 为计划 ID 时另带 `batch_id` |
| D20、D26、D27、D31、D34 响应 | D20 `MedicationDrugOptionVO.ListResult`（含 `banned_hint`）；D26 `ToastVO`；D27 `NotifyResultVO`（`notified_names`）；D31、D34 `DisinfectPlanVO`（含 `task_count`） | 给出外层结构 |
| API-D07 `immune_round`、`due_time`；API-D31 `due_time` | 界面无输入项，暂不传 | 确认可不传、用默认值 |
| 客户端补充的可空字段 | `ImmuneRecordVO.drug_id`；`MedicationRecordVO.drug_lot_id`、`executor_id`；`DisinfectRecordVO.drug_id`、`executor_id`；`DisinfectantOptionVO.stock_unit` | 补进服务端 VO |

### 模块09 饲料与兽药疫苗库存

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| `stock_version` | 可空 `Integer`，加在 `FeedOptionVO`、`DrugLotOptionVO`、`DrugLotDetailVO`；M07、M10、M11 提交用 | 在三个 VO 中返回 |
| API-M34、M35 响应 | 暂定 `DrugArchiveListResult`、`DrugOptionListResult` | 给出类名 |
| 字典与品类 | 饲料类型取 U10 `FEED_TYPE`；报废原因取 U10 `SCRAP_REASON`；适用品类取 U09 | 确认来源 |
| M11 下拉 | 批次 API-L06（`status=ACTIVE`）→ `PageVO<StockBatchOptionVO>`；栏舍 API-L07（`usage=OPTIONS`）→ `List<StockPenOptionVO>`（`disabled` 为空栏）；成员 API-F16 → `PageVO<StockMemberVO>` | 确认接口与字段 |
| 栏舍下拉的在栏批次数 | API-L07 无此字段，App 用批次列表算出 | 确认是否补字段 |
| API-U11 目录搜索 | `CatalogResultVO`：`id`、`name`、`alias`、`drug_category`、`spec`、`measure_unit`、`pack_unit`、`pack_size`、`banned_hit`；已建档按 API-M34 的 `catalog_id` 判断 | 确认返回字段 |
| `StockFlowItemVO.biz_type` | `FEED_RECORD`、`IMMUNE_RECORD`、`MEDICATION_RECORD`、`DISINFECT_RECORD`、`INBOUND`、`OUTBOUND`、`LOSS`、`STOCKTAKE`；接种、用药、消毒跳 D03 / D04 / D08 带 `record_id` | 确认取值与 `biz_id` 含义 |
| `FeedSaveRequest` | 无单位字段，M02 单位只读显示千克 | 确认饲料记账单位固定为千克，或补字段 |

### 模块10 收支、出栏、行情与报表

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| REPORT_READY 推送 / 消息 | T06 打开 E13 只带 `report_id`；E13 选中该期还需 `report_type`、`period_start` | 在推送附加字段与消息 route 参数中补这两项 |
| A09 → E13（`MODE_OWNED_FARMS`） | 暂按 `scope=ALL` | 确认是否加“只算本人创建的养殖场”参数 |
| API-E15 | 收款面板不带 `batch_id`、`sale_date` 调用，取 `SalePrefillVO.last_payment_method` | 确认该用法 |
| API-E42 `ReportExportVO.download_url` | 交系统 DownloadManager 下载，不带令牌 | 返回无需访问令牌的签名地址 |
| 类型待核对 | `BusinessReportVO.to_date` 为 String；`SaleOrderDetailVO.pen_allocations` 为 `List<PenAllocation>`；`MarketFilterVO.price_types` 为 `List<PriceTypeOption>`；`FinanceCategoryVO.mark_only` 为 boolean | 联调核对 |
| API-U02 改出栏数量 | 只有一个 `new_value`，传 JSON，例如 `{"quantity":96,"total_weight":11520}` | 确认按 JSON 解析 |

### 模块11 AI 养殖助手与养殖课堂

| 接口 / 对象 | App 当前用法 | 需要服务端做什么 |
|---|---|---|
| `answer_text` 关键结论加粗 | 无标记约定；只在 `answer_type` 为 `NO_BASIS` 时加粗首句，其余纯文本 | 定义加粗的表示方式 |
| 打开会话时的 ANSWERING 轮次 | 每 5 秒调 API-K08 刷新该轮，最多 125 秒 | 确认 K08 可用于轮询 |

## 第二部分 给小组过目：App 自定的做法

### 公共

- 图片上传校验 — 压缩后再校验 5MB；HEIC、WebP 直接拒收，提示只支持 JPG 或 PNG。
- 隐私政策版本 — `PrivacyManager.POLICY_VERSION` 为 `2026-10-01`。
- 草稿恢复询问文案 UI 文档未给出 — 各模块自拟，见模块04、06、08。

### 模块01 账号与个人中心

- 03 号 7.1 节 zmt_settings 未列 `install_id`、`last_phone` — App 已使用：`install_id` 作登录 `device_id`，`last_phone` 用于登录页预填。

### 模块02 养殖场与成员管理

- 地图停止后逆地理编码等待：UI 文档 300 ms，03 号 9.2 节 500 ms — 按 300 ms（`MapPickViewModel.DEBOUNCE_MS`）。
- F10“撤回转让”按钮与确认文案原型未画 — 自拟：按钮“撤回转让”，对话框“撤回转让请求？”“撤回后，{成员}收到的转让请求失效，你仍是{养殖场}的所有者。”，成功提示“已撤回转让请求”。
- 03 号 9.4 节的 F05 位置小地图与 F04 只读模式，App 原型没有 — 本期未实现。

### 模块03 今日首页、日历待办与消息中心

- 03 号 11.1 节写“登录成功后设置别名”，与 API-T20 服务端绑定冲突 — 只由服务端绑定，App 不调 SDK。
- 带 `category` 进入 T03 的筛选提示原型未画 — 透传 `category`，不加提示。
- 同意邀请时“邀请已过期” — 错误码以模块02 为准，按接口返回的 message 提示。

### 模块04 栏舍与存栏

- L11 拍照：UI 文档写“调用系统相机”，03 号 8.1 节为 CameraX 相机页 — 按 03 号，用 lib_media 相机页。
- L11 死淘登记草稿恢复对话框 — 自拟“恢复上次未提交的内容？”“上次填写的死淘登记还没有保存，可以继续填写。”，按钮“恢复”“不用了”。
- L01“预警”指标卡原型为 2 项 — 取 `reminder_total`（休药 + 预警项）。
- 服务端 1.2 节列出 L06 使用 API-U14 — App UI 未画入口，L06 未做。

### 模块05 繁育管理

- “查看修改记录”整页（API-U14）的归属与路由未定 — 暂作 module_breed 内页面，从 B06 更多菜单与记录菜单进入。

### 模块06 饲喂与生长称重

- 只读提示 `readonly_reason` 为 VOIDED、PENDING_REQUEST 时的文案 UI 未给 — 自拟“这条投喂记录已作废，关联的饲料出库已同步作废。”“这条记录有修改申请正在审核，审核完成前不能修改。”
- 草稿恢复 Snackbar 文案 UI 未给 — 自拟“上次没有提交的投喂登记已保存为草稿”“上次没有提交的称重登记已保存为草稿”，按钮“恢复”。

### 模块07 日常巡检与异常上报 AI 诊断

- 无（麦克风被拒的处理见“已定事项”）。

### 模块08 免疫、用药与消毒

- 草稿恢复对话框 — 自拟“恢复上次未提交的内容？”“上次离开时填写的内容已保存为草稿（保留 7 天）。”，按钮“恢复”“不恢复”。

### 模块09 饲料与兽药疫苗库存

- 盘点记录、单据只读页（报损、报废、盘点）原型未画 — 按只读分组列表补做。
- 5 个选择面板 — 合并为一个通用的 `PickerSheet`。
- M09 原型没有“每包装折合数”输入框 — 非疫苗的 `pack_size` 取目录值提交。
- 流水中的饲喂领用打开 L06 — L06 不读 `record_id`，定位不到具体的投喂记录。
- M06“停用通知”行打开 T06 — 带 `recall_message_id` 作为 `message_id`；T06 能否按它定位到该通知未确认。
- 从消息打开 M06 — 不带 `message_id`。

### 模块10 收支、出栏、行情与报表

- 导出文件的分享入口 UI 只定义了 Snackbar“打开” — 放在 E13 顶部栏更多菜单，名为“分享报表”。
- 03 号第二节把 WorkManager 列为“导出下载”知识点 — 改用系统 DownloadManager。
- `IFinanceService` — 未建；在 `FinanceModuleEntry` 中监听登录状态，清理导出文件。
- 金额输入框 — 未做输入时实时加千分位。
- E12 测试直连 — 不读称重记录，生成的建议都标“依据不足”。

### 模块11 AI 养殖助手与养殖课堂

- 页面实现 — 用 ViewBinding（开发约定的例外）。
- 录音控制 — 放在 View 层。
- K03 内嵌视频点全屏 — 打开 K05 横屏。
- 课程搜索栏 — 放在 K04 内容区顶部。
- 打开会话时出现 ANSWERING 轮次的显示 UI 未定义 — 显示“正在思考…”，按第一部分的方式轮询。
- K01 拍照：UI 文档写“系统相机”，03 号 8.1 节把 K01 列入 CameraX 相机页 — 用 lib_media 相机页拍照模式。
- K04 专题区排序与数量未定义 — 用 `DEFAULT`、每页 20。
- “仅在 Wi-Fi 下播放”设置入口未定义 — 存本机偏好，只在 K05 播放设置中修改。
- K01 测试直连的回答类型 — 提示词要求模型第一行输出 `[[NORMAL]]`、`[[NO_BASIS]]`、`[[OFF_TOPIC]]`、`[[BANNED_DRUG]]`、`[[EPIDEMIC]]` 之一；没有知识库来源 Chip。
- K07 分享地址 — 占位域名 `https://web.zhimutong.example.com/course/topics/`。

## 第三部分 已定事项

- 员工退出养殖场的入口放在 A07，单独一行。
- 养殖场地址必须有经纬度和 adcode。
- L07 扫码沿用相机页。
- 麦克风被拒时引导去设置，不录像。
- 测试直连：AI 诊断、K01、E12 直连千问，DeepSeek 备用；语音识别直连百炼；短信直连阿里云 Dypnsapi；只用于 debug 包。
