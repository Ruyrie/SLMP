# 智牧通 App 开发约定（代码阶段）

本文件记录写代码时定下的工程约定，以及与《03-App技术架构规范》不一致、以实际为准的地方。所有组员与代码生成代理都以本文件为准；03 号文档与各模块技术准备文档仍是业务规则、ViewModel 字段、接口编号的依据。

## 一、已确认的方案（2026-10-21）

| 事项 | 结论 |
|---|---|
| 数据来源 | 服务端未完成前，debug 版由 lib_network 的 MockInterceptor 按“方法 + 路径 + 条件”返回示例 JSON；local.properties 中 `zmt.useMock=false` 后直接请求 `zmt.baseUrl` |
| 高德、极光 | 按文档接入 SDK 与隐私合规初始化；Key 从 local.properties（amap.key、jpush.appKey）读取。高德 Android Key 已配置在负责人电脑上；没有 Key 时地图选点页只显示不可用提示，推送不初始化 |
| 养殖场地址 | 2026-10-22 确认：必须带经纬度与 adcode，不接受手动填写的地址；没有高德 Key 时 F02、F06 不能提交位置 |
| 员工退出养殖场 | 2026-10-22 确认：入口放在 A07“我的养殖场”下方单独一行（场长、员工显示），确认对话框同 F09 |
| L07 扫码 | 2026-10-22 确认：使用 lib_media 公共相机页识别后返回结果（技术文档原写 L07 自持相机） |
| 测试直连（2026-10-22 用户确认） | 服务端完成前，debug 包允许放 Key 做测试：模块07 AI 诊断直连阿里云百炼 qwen3.8-max（OpenAI 兼容接口，图片 base64），失败时用 deepseek-flash 备用；短信验证码直连阿里云号码认证服务（Dypnsapi SendSmsVerifyCode / CheckSmsVerifyCode，签名“恒创联众”、系统模板 100001–100005）。Key 写在 local.properties（ai.dashscopeKey、ai.deepseekKey、aliyun.accessKeyId、aliyun.accessKeySecret、aliyun.smsSignName），只生成到 lib_common 的 debug BuildConfig；release 包为空，直连关闭、走服务端接口。正式上线前改回服务端调用 |
| 测试直连扩展（2026-10-22 用户确认） | 模块11 K01 AI 问答（流式、附图）与模块10 E12 AI 出栏建议也直连千问，DeepSeek 备用；K01 语音转文字直连百炼语音识别；课程视频 Mock 用公开测试 HLS 地址 |
| 麦克风被拒（2026-10-22 用户确认） | 录像时没有麦克风权限：说明“录像需要麦克风”并提供“去设置开启”，不开启就不录像（仍可拍照）；03 号 8.2 节“只录无声视频”作废 |
| 原型图 | 模块01–04 用 .tech-prep/screens；模块05 起直接看 原型导出/Android端/智牧通App原型/智牧通App原型_完整版.pdf（Read 工具按页码查看） |
| 组件单独运行 | 做。gradle.properties 中 `runAlone.module_xxx=true` 后该组件按 application 构建，合并 `src/alone` 下的清单与代码 |
| 代码管理 | 分支 feature/app-m01-m04；公共框架和每个模块编译通过后各提交一次；不推送 |
| 本期范围 | 只做 App；模块01–04（A01–A09、F01–F10、T01–T08、L01–L11）与通用页 U01–U03；Web 与服务端不动 |
| 视觉依据 | 各 UI 设计文档内嵌的原型截图，已导出到 `.tech-prep/screens/`（文件名以页面编号开头，index.md 为目录）；截图用来对照布局与视觉；截图与文档（UI 设计文档、02 号规范、技术准备文档）冲突时以文档为准（文档中标“技术准备补充，原型待改”的内容截图尚未更新） |

## 二、与 03 号文档不一致、以实际为准的地方

| 03 号文档写法 | 实际做法 | 原因 |
|---|---|---|
| app 中一个 AppComponent 汇总全部业务组件的 Module | lib_common 提供单例 CoreComponent（网络、会话、当前养殖场、数据库、字典）；每个业务组件有自己的 XxxComponent（作用域 @FeatureScope，dependencies = CoreComponent），提供本组件的 ViewModel 工厂 | 业务组件要能单独运行，不能依赖 app 汇总 |
| 单独运行时使用 debug 目录下的清单 | 使用 `src/alone/AndroidManifest.xml`，只在单独运行时挂到 debug 源集，与 main 清单合并 | 不影响正常构建的 debug 源集 |
| PushRouterActivity、PushDeviceManager 放在 app | 放在 module_today（推送整体归模块03），Application 通过 IPushService 在用户同意隐私政策后初始化 | 推送代码只有模块03 一个负责人，避免改动壳工程 |
| ApiException 在 lib_network | 放在 lib_base 的 `com.zhimu.slmp.base.error`，lib_network 负责生成 | BaseViewModel 在 lib_base，需要识别错误码 |
| BaseActivity 处理 40104 跳登录页 | SessionManager 发出全局事件，BaseApplication 注册的 ActivityLifecycleCallbacks 在前台页面上打开 A01 并清空返回栈 | lib_base 不依赖 lib_common 的路由与会话 |
| 示例数据放 Apifox | 放在各组件 `src/debug/assets/mock/<组件名>/`，release 包不包含 | 无服务端也能完整演示 |
| ABI 未限定 | 只打包 arm64-v8a、armeabi-v7a（高德只有 ARM 原生库，x86_64 安装时地图黑屏；x86_64 模拟器经 ARM 转译运行） | 地图可用 |
| 视频压缩、上传队列用 Room + WorkManager | 本期只有照片（F02、F06 资质图，L11 死淘照片）：选中后由 UploadManager 压缩并直接上传 API-U05，进度用 LiveData；Room 上传队列与 WorkManager 视频任务在模块07 时加入 | 本期无视频 |

## 三、工程结构与分工

| 组件 | 包名 | 负责（阶段） | 内容 |
|---|---|---|---|
| app | com.zhimu.slmp | 框架核心（阶段一） | ZhimuApp、SplashActivity（隐私政策、冷启动）、MainActivity（按角色装配底部导航）、混淆规则 |
| lib_base | com.zhimu.slmp.base | 核心类：框架核心；res 与 widget、dialog：UI 套件（阶段一） | 基础类、UiState、SingleLiveEvent、错误、格式化、表单校验、BindingAdapter、Room；主题、颜色、字阶、形状、图标、通用控件、五级弹窗 |
| lib_network | com.zhimu.slmp.network | 框架核心（阶段一） | OkHttp 拦截器、令牌刷新、Retrofit、ApiResponse、PageVO、RxResult、MockInterceptor |
| lib_common | com.zhimu.slmp.common | 核心：框架核心；ui.status 与 widget：UI 套件；ui.change：模块01（阶段二） | CoreComponent、会话、FarmContext、ServiceHub 与服务接口、路由、公共 VO、字典仓库、U01–U03、一级页顶部栏 |
| lib_media | com.zhimu.slmp.media | 多媒体（阶段一） | 相机页（拍照、扫码）、照片选择、压缩、上传 API-U05/U07、照片网格 MediaPickerView、全屏预览 |
| lib_map | com.zhimu.slmp.map | 模块02（阶段二） | 高德初始化与隐私合规、F04 地图选点、单次定位 |
| module_account | com.zhimu.slmp.account | 模块01（阶段二） | A01–A09、修改密码、关于、协议；另负责 lib_common 的 U01、U02（common.ui.change） |
| module_farm | com.zhimu.slmp.farm | 模块02（阶段二） | F01–F10（F04 在 lib_map），实现 IFarmContextService、IFarmInviteService |
| module_today | com.zhimu.slmp.today | 模块03（阶段二） | T01–T08、极光推送、PushRouterActivity，实现 ITodoService、IPushService |
| module_herd | com.zhimu.slmp.herd | 模块04（阶段二） | L01–L11（L07 扫码用 lib_media 相机页的扫码模式） |
| module_breed | com.zhimu.slmp.breed | 模块05（阶段三） | B01–B07 |
| module_feed | com.zhimu.slmp.feed | 模块06（阶段三） | G01–G06 |
| module_inspect | com.zhimu.slmp.inspect | 模块07（阶段三） | I01–I09；另负责 lib_media 的录像、视频压缩、后台上传与视频播放，lib_common 的 common.ai（AI 直连） |
| module_health | com.zhimu.slmp.health | 模块08（阶段三） | D01–D09 |
| module_stock | com.zhimu.slmp.stock | 模块09（阶段四） | M01–M11 |
| module_finance | com.zhimu.slmp.finance | 模块10（阶段四） | E01–E14（E12 AI 出栏建议直连） |
| module_ai | com.zhimu.slmp.ai | 模块11（阶段四） | K01–K07（问答流式直连、语音识别直连、课程播放）；另负责 lib_common 的 common.ai 扩展（流式、语音识别） |

规则：

- 每个人（代理）只改“负责”列中属于自己的目录；需要改别人目录里的文件时，在交付说明中列出，由主会话统一处理。
- 资源名必须带组件前缀：base_、common_、media_、map_、account_、farm_、today_、herd_、breed_、feed_、inspect_、health_、stock_、finance_、ai_（lib_base 的图标为 `ic_<名称>` / `ic_<名称>_filled`，主题与样式为 `Theme.Zmt*`、`Widget.Zmt.*`、`TextAppearance.Zmt.*`、`ShapeAppearance.Zmt.*`）。
- `android.nonTransitiveRClass=true`：引用 lib_base 资源时用 `com.zhimu.slmp.base.R`，引用本组件资源用本组件的 R。
- 业务组件之间不互相依赖；跨组件只用 RouterPath 的 action 打开页面，或通过 ServiceHub 取 lib_common 中定义的服务接口。
- 新的跨组件参数键如果 RouterPath 中没有，先在本组件内定义字符串常量，交付时列出，由主会话合并进 RouterPath。

## 四、框架约定（阶段一实现，阶段二直接使用）

### 4.1 依赖注入

| 类 | 说明 |
|---|---|
| common.di.CoreComponent | @Singleton；暴露 Application Context、Gson、默认 Retrofit、上传用 Retrofit（@Named("upload")）、流式 OkHttpClient（@Named("stream")）、SessionManager、FarmContext、AppDatabase、BaseDataRepository |
| common.di.FeatureScope | 业务组件 Component 的作用域；Repository、Api 在组件内为 @FeatureScope 单例 |
| common.di.CoreComponentProvider | Application 实现，`CoreComponent getCoreComponent()`；`CoreInjector.get(context)` 取得 |
| base.mvvm.ViewModelFactory、ViewModelKey | Dagger 多绑定的 ViewModel 工厂；创建时把 SavedStateHandle（含页面 Intent extras / Fragment arguments）交给 BaseViewModel |
| 业务组件 | `XxxComponent`（dependencies = CoreComponent，modules = XxxModule + XxxViewModelModule）、`XxxInjector.factory()` 取 ViewModel 工厂；`XxxModuleEntry implements IModuleEntry` 在 onCreate 中创建组件并向 ServiceHub 注册服务与底部导航标签 |
| 组件入口加载 | BaseApplication 按类名反射加载 IModuleEntry（壳工程列出全部组件，单独运行时只列本组件和需要的 lib 组件）；找不到的类跳过，混淆规则保留实现类的无参构造方法 |

### 4.2 网络与 Mock

- Retrofit 路径写相对路径，例如 `@POST("api/v1/auth/login/password")`；方法返回 `Single<ApiResponse<T>>`，Repository 中用 `RxResult.unwrap()` 取出 data，没有 data 的接口用 `RxResult.completable(...)`。
- 幂等键作为参数 `@Header(Headers.IDEMPOTENCY_KEY) String idemKey` 传入，由 ViewModel 在点击提交时生成（规则见各模块文档）。
- 业务错误统一为 `com.zhimu.slmp.base.error.ApiException`（code、message、fieldErrors、traceId、data）；HTTP 非 2xx 的错误体也转换为 ApiException；网络不可用为 code = -1。
- Mock 文件放在本组件 `src/debug/assets/mock/<组件名>/`：`routes.json` 列出规则，响应文件是完整的 ApiResponse JSON（含 code、message、data、trace_id）。规则格式见 4.3，MockInterceptor 按目录名字母顺序、文件内从上到下匹配第一条。

### 4.3 Mock 规则格式

| 字段 | 说明 |
|---|---|
| method | GET / POST / PUT / PATCH / DELETE |
| path | 不含域名的路径，路径参数写 `{farmId}`，例如 `/api/v1/farms/{farmId}/members` |
| when.path | 可选，路径参数取值，例如 `{"farmId": "1002"}` |
| when.query | 可选，查询参数取值，例如 `{"page": "2", "status": "PENDING"}` |
| when.header | 可选，请求头取值，例如 `{"Authorization": "Bearer mock-staff"}`、`{"X-Farm-Id": "1002"}` |
| when.body | 可选，请求体包含的子串，例如 `"13700005678"` |
| file | 响应文件名（同目录） |
| delay | 可选，毫秒，默认 300 |

HTTP 状态码按 code 的前三位（0 为 200）。员工视图、不同养殖场、分页第 2 页、错误场景都用带条件的规则放在默认规则之前。

### 4.4 Mock 示例数据（全组件统一）

“今天”为 2026-10-19（周一）。各组件示例数据中的 ID、名称必须一致：

| 对象 | 数据 |
|---|---|
| 账号 | 王建国 user_id 10001，手机号 13800006620（138****6620），密码 Zmt12345，令牌 `mock-owner`；刘芳 10002，13900001234，场长；李明 10003，13700005678，员工，令牌 `mock-staff`，负责 3 号育肥舍；张伟 10004，员工，负责 1 号保育舍；任何验证码都用 123456 |
| 养殖场 | 1001 东山猪场（NORMAL，育肥猪，PIG，HEAD 头）：王建国所有者、刘芳场长、李明与张伟员工；1002 西岭鸡场（NORMAL，肉鸡，POULTRY，BIRD 只）：王建国场长；1003 北湾牛场（PENDING，肉牛，CATTLE，HEAD 头）：王建国所有者，10月17日首次提交、10月18日驳回、10月19日重新提交 |
| 栏舍（东山猪场） | 2001 1 号保育舍、2003 3 号育肥舍、2004 4 号育肥舍、2005 5 号育肥舍（空栏 0 / 400 头）、2006 母猪舍、2007 产房 |
| 批次 | 3001 ZM2610-031 杜长大，10月1日 78 日龄入栏 205 头，今天 96 日龄、存栏 200 头，在 3 号育肥舍；3002 ZM2610-019、3003 ZM2610-045、3004 ZM2609-038（其余字段以各模块 UI 文档为准） |
| 修改申请 | CR2610190008：王建国 10月19日 09:28 提交，投喂记录实际投喂量 140 千克 → 160 千克 |

登录 `13700005678` 得到员工令牌 `mock-staff`，员工视图的响应用 `when.header.Authorization = "Bearer mock-staff"` 区分；切换养殖场后的数据用 `when.header.X-Farm-Id` 区分。

### 4.5 页面与 ViewModel

- 页面继承 BaseActivity / BaseFragment；ViewModel 继承 BaseViewModel，字段、方法、事件名称按模块技术准备文档 2.4 节；内部 MutableLiveData、对外 getter 返回 LiveData，表单双向绑定字段直接暴露 MutableLiveData。
- 页面状态用 UiState + StateLayout（骨架屏、空、加载失败、无权查看）；一次性提示用 BaseViewModel 的 message（UiMessage），由基类用 Snackbar 显示，不用 Toast。
- 弹窗只用 lib_base 的五级封装：SnackbarHelper、TipPopup、MoreMenuHelper、BaseBottomSheetDialogFragment、ConfirmDialogFragment、ReasonDialogFragment；结果经 FragmentResult 回传。
- 表单页用 DataBinding 双向绑定；列表项用 ViewBinding + ListAdapter；长列表用 Paging 3。
- 文案、颜色、字号、间距以 UI 设计文档与 02 号规范为准，布局只引用主题属性与 `@color/zm_*`，不写死色值。

## 五、编译与提交

- 编译：`./gradlew :<组件>:assembleDebug --console=plain`（Windows 下在 Git Bash 中执行）；阶段二各代理在独立 worktree 中构建。
- 单元测试：`./gradlew :<组件>:testDebugUnitTest`。
- 提交信息用中文，说明本次完成的页面与要点，末尾附 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`；不推送。
