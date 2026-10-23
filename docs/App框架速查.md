# 智牧通 App 框架速查（给模块代理）

工程约定以《App开发约定》为准，本文只列框架核心提供的类和用法。包名前缀 `com.zhimu.slmp` 下文省略。

模块开发以 `.tech-prep/screens` 中对应页面编号的截图为视觉依据。截图只用来对照布局和视觉，与文档冲突时以文档为准（文档中标“技术准备补充，原型待改”的内容截图尚未更新）。

## 一、lib_base（`base.*`）

| 类 | 用途 | 主要方法 |
|---|---|---|
| mvvm.BaseViewModel | ViewModel 基类 | `getMessage()`、`getUiState()`、`getRefreshing()`；`launch(Single/Completable, onSuccess[, onError])` io 执行、主线程回调、自动加入 `disposables`；`load(Single, onSuccess)` 首次加载自动切换 LOADING / CONTENT / EMPTY / ERROR / NO_PERMISSION，已有内容时只刷新；`retry()`（重写为重新请求）；`handleError(Throwable)`；可重写 `onSavedStateAttached(handle)`、`onFieldErrors(map)`、`onDataChanged(e)`（默认 retry）、`onNoPermission(e)`（默认整页无权查看，弹窗内操作改为提示）、`isEmptyData(o)`；`showMessage(...)` |
| mvvm.BaseListViewModel<F, T> | Paging 3 列表 | 构造 `super(初始筛选)`；实现 `createPagingSource(F)`；`getPagingData()`、`setFilter(F)`（变化时重建）、`refresh()` |
| mvvm.ViewModelFactory / ViewModelKey / ViewModelFactoryModule | Dagger 多绑定工厂 | 创建时把 SavedStateHandle（含 Intent extras / Fragment arguments）交给 BaseViewModel |
| mvvm.UiState / UiMessage / SingleLiveEvent / EmptyAware | 页面状态、一次性提示、只消费一次的事件、可判空数据（PageVO 已实现） | `UiMessage.of(text)`、`UiMessage.withAction(text, actionText, actionId)` |
| ui.BaseActivity | Activity 基类 | edge-to-edge，默认把系统栏 insets 加到布局根视图（重写 `applyWindowInsetsToRoot()` 返回 false 自行处理）；点输入框外收起软键盘；`observeBase(vm)`；`obtainViewModel(factory, cls)`；重写 `onMessageAction(actionId)`、`getSnackbarAnchor()` |
| ui.BaseFragment | Fragment 基类 | `observeBase(vm)`（在 onViewCreated 调）；`obtainViewModel`、`obtainActivityViewModel`；同上两个重写点 |
| ui.SnackbarHelper | 第 1 级弹窗 | 4 秒、最多一个操作、新的替换旧的；锚点：给底部操作栏设 `android:tag="snackbar_anchor"` 即自动锚定 |
| ui.InsetsHelper | 系统栏 insets | `applySystemBarsPadding(v, top, bottom)`、`applyTopPadding(v)`、`applyBottomPadding(v)` |
| error.ApiException / ErrorCodes | 统一业务错误 | `getCode()`、`getMessage()`、`getFieldErrors()`、`getTraceId()`、`getData()`、`dataString("reason")`、`dataBoolean("need_confirm")`；`NETWORK = -1` |
| event.GlobalEvents | 全局事件 | `postRelogin(reason)`、`postMessage(...)`、`postUpdateRequired(...)`、`postForcedStep(nextStep)`（BaseApplication 统一处理） |
| util.DateFormatter | 日期显示 | `formatDateTime`“今天 14:20”、`formatDate`“10月19日 周一”、`formatMonth`、`formatDateRange`、`formatTime`、`formatDuration`、`todayString()` |
| util.NumberFormatter | 数量与金额 | `formatQuantity(v, "千克")`“1,200 千克”（中间为不换行空格）、`formatAmount`、`formatAmountSummary`、`formatPercent`、`formatSignedPercent`、`maskPhone` |
| form.FormValidator | 表单校验 | `required`、`maxLength`、`length`、`range`、`maxScale`、`phone`、`password`、`smsCode`、`check`；`validate()` 返回字段 → 文案 |
| util.ClickThrottle / util.IdemKey | 防连点 500 ms / 幂等键 | `ClickThrottle.setOnClickListener(v, l)`；`IdemKey.newKey()` |
| binding.CommonBindingAdapters | 布局绑定 | `app:imageUrl` + `app:imageCacheKey`（file_id）、`app:goneIfNull`、`app:visibleIf`、`app:quantityText` + `app:quantityUnit`、`app:amountText`、`app:dateText`、`app:dateTimeText`、`app:percentText`、`app:onSafeClick` |
| db.AppDatabase | Room zmt.db | `formDraftDao()`、`uploadTaskDao()`、`dictCacheDao()`，DAO 返回 RxJava 类型 |

## 二、lib_network（`network.*`）

| 类 | 用途 |
|---|---|
| model.ApiResponse<T> / model.PageVO<T> | 统一响应 / 分页（list、total、page、size、hasMore、tabCounts） |
| RxResult | `api.x().compose(RxResult.unwrap())` 取 data；无 data 用 `RxResult.completable(api.x())`；`toApiException(t)` |
| Headers | 请求头常量；幂等键 `@Header(Headers.IDEMPOTENCY_KEY) String idemKey` |
| paging.PagedSource<T> | `new PagedSource<>((page, size) -> api.list(..., page, size).compose(RxResult.unwrap()))`，每页 20 条 |
| upload.ProgressRequestBody | multipart 上传进度 |
| 拦截器 | Authorization、X-Farm-Id、X-Client、X-App-Version 自动添加；40101 自动刷新；40104 自动清除会话并回 A01；业务代码不处理这些 |

## 三、lib_common（`common.*`）

| 类 | 用途与方法 |
|---|---|
| di.CoreComponent | `context()`、`gson()`、`retrofit()`、`uploadRetrofit()`、`streamClient()`、`sessionManager()`、`farmContext()`、`database()`、`baseDataRepository()`、`appSettings()`、`installIdProvider()`；`CoreInjector.get(context)` |
| di.FeatureScope | 业务组件内 Api、Repository 的作用域 |
| session.SessionManager | `saveToken(AuthTokenVO)`、`updateUser`、`getUser()`、`currentUser()`、`isLoggedIn()`、`getLoginState()`、`logout()`、`clear(reason)`、`getRecoveryInfo()` |
| session.AppSettings / InstallIdProvider | zmt_settings（隐私、last_phone、video_wifi_only、notify_guide_shown）/ device_id |
| farm.FarmContext | `getContext()`、`current()`、`currentFarmId()`、`farmIdOrZero()`、`role()`、`isOwner()`、`isManager()`、`isStaff()`、`isOwnerOrManager()`、`hasFarm()`、`permissions()`、`update(vo)`、`clear()` |
| service.ServiceHub | `register(Class, impl)`、`get(Class)`（可为 null）、`require(Class)` |
| service 接口 | IModuleEntry（`onCreate`、`onPrivacyAgreed`、`debugPages`）、IAccountService、IFarmContextService（含 `quitFarm`、`listMyFarms`、F01 结果键 `SWITCHER_*`）、IFarmInviteService、ITodoService + TodoChange、IPushService、TabRegistry（TODAY、PRODUCTION、MY_PENS、ASSISTANT、BUSINESS、MINE）、MainTabHost |
| router.RouterPath / RouterTable / Router | 页面常量（`RouterPath.L07`）与参数键（`EXTRA_FARM_ID` 等）；`Router.open(ctx, action[, extras])`、`openPage(ctx, "I06", extras)`、`openClearTask`、`registerForResult` + `openForResult`；未实现页面自动提示“该功能正在开发中” |
| model.vo | 公共 VO：FileVO、MediaItemVO、OptionVO、UserBriefVO、FarmBriefVO、PenBriefVO、BatchBriefVO、ChangeRequest*VO、RecordLogVO、MessageBriefVO、FieldErrorVO、AuthTokenVO、AccountUserVO、SessionVO、RecoveryInfoVO、RecoveryCodeVO、FarmContextVO、FarmPermissionVO、DictItemVO、CategoryVO、CategoryBreedVO |
| data.BaseDataRepository | `getCategories()`（U09）、`getDicts(DictTypes.PEN_TYPE, categoryId)`（U10，缓存 1 小时）、`getRecordLogs(bizType, bizId)`（U14） |
| sms.*（测试直连，只在 debug 包） | `AliyunSmsConfig.fromBuildConfig().isDirectEnabled()`；`new AliyunSmsClient(config)`：`sendVerifyCode(phone, AliyunSmsTemplates.xxx)` 返回 `Single<SmsSendResult>`，`checkVerifyCode(phone, code)` 返回 Completable（不通过为 40103，频率限制为 42901）；签名 `AcsV3Signer`（ACS3-HMAC-SHA256）；模块01 经 `DirectSmsGateway` 接入 |
| ai.*（测试直连，只在 debug 包） | `AiDirectConfig.isEnabled()` / `isSpeechEnabled()`（release 包 Key 为空，自动走服务端接口）；对话 `AiChatClient.getDefault()`：`chat(AiChatRequest)` 返回 `Single<AiChatResult>`（模块07、10 在用，签名不变）；多轮在 builder 上 `.history(List<AiTurn>)`（`AiTurn.user / assistant`）；图片 `AiImage.fromBitmap(bmp, 1024, 80, "照片 1")`；JSON 回答用 `AiJson.extractObject` |
| ai 流式（模块11 K01） | `AiChatClient.getDefault().stream(request)` 返回 `Flowable<AiStreamChunk>`，在 io 线程订阅：`STARTED`（服务商受理，换备用时 `isFallback()`）→ `THINKING`（推理中，不给内容，可当心跳）→ `CONTENT`（`getText()` 为正文增量）→ `DONE`（完整正文、finish_reason）；取消订阅即取消请求；未出正文前网络 / 5xx / 429 / 空回答换 DeepSeek，出正文后中断以 `AiException.Kind.NETWORK` 结束不重试。通用 SSE 读取 `new AiSseReader(bufferedSource).next()` 返回 `AiSseEvent`（`getEvent()`、`getData()`、`isComment()` 心跳、`isDoneMarker()`），`AiSseReader.readAll(text)` 便于测试 |
| ai 语音识别（模块11 K01） | `AiSpeechClient.getDefault()`：`isEnabled()`；`recognize(File wav)` 返回 `Single<AiSpeechResult>`（`isRecognized()`、`getText()`），百炼 qwen3-asr-flash（OpenAI 兼容，音频 base64 放 input_audio，≤10MB）；模型可用 local.properties 的 `ai.dashscopeAsrModel` 覆盖（BuildConfig.DASHSCOPE_ASR_MODEL，为空用代码默认值） |
| app.BaseApplication / PrivacyManager | 组件入口加载、全局事件处理；`PrivacyManager.ensureAgreed(activity, runnable)` |

## 四、新建一个页面

| 步骤 | 做法 |
|---|---|
| 1 Activity | `XxxActivity extends BaseActivity`；`vm = obtainViewModel(XxxInjector.factory(), XxxViewModel.class); observeBase(vm);` |
| 2 布局 | `<组件前缀>_activity_xxx.xml`，DataBinding；颜色用 `?attr/colorXxx` 或 `@color/zm_*`；底部操作栏加 `android:tag="snackbar_anchor"` |
| 3 ViewModel | `XxxViewModel extends BaseViewModel`，`@Inject` 构造方法注入 Repository；页面参数在 `onSavedStateAttached(handle)` 中读取（构造方法中还拿不到） |
| 4 绑定 | XxxViewModelModule：`@Binds @IntoMap @ViewModelKey(XxxViewModel.class) abstract ViewModel bindXxx(XxxViewModel vm);` |
| 5 清单 | `<activity android:name=".ui.xxx.XxxActivity" android:exported="false">` + intent-filter：action `com.zhimu.slmp.page.<编号>` 与 category `android.intent.category.DEFAULT` |
| 6 RouterPath | 编号常量已全部存在；新的跨组件参数键先在本组件定义常量，交付时列出 |
| 7 调试入口 | 在 XxxModuleEntry.debugPages() 加 `new DebugPage("L06 批次档案", RouterPath.L06, extras)` |

一级页 Fragment 在 ModuleEntry 中注册：`TabRegistry.register(TabRegistry.TODAY, TodayFragment::new);`；MainActivity 已处理顶部状态栏 insets，一级页不要再加。

## 五、Api 与 Repository

```java
public interface HerdApi {
    /** API-L15 批次档案（L06） */
    @GET("api/v1/batches/{batchId}")
    Single<ApiResponse<BatchDetailVO>> getBatch(@Path("batchId") long batchId);
}
// HerdModule：@Provides @FeatureScope static HerdApi api(Retrofit r) { return r.create(HerdApi.class); }
// Repository（@FeatureScope + @Inject 构造）：return api.getBatch(id).compose(RxResult.unwrap());
```

路径写相对路径（不以 / 开头）；Retrofit 已在 io 线程执行；VO 放 `<组件>.model.vo`、请求体放 `<组件>.model.request`（混淆规则已按包名保留字段）。

## 六、Mock

| 项目 | 说明 |
|---|---|
| 位置 | `src/debug/assets/mock/<组件名>/routes.json` 与响应文件（完整 ApiResponse JSON） |
| 规则 | JSON 数组，每项 `method`、`path`（`/api/v1/farms/{farmId}`）、可选 `when.path` / `when.query` / `when.header` / `when.body`（字符串或数组，均需包含）、`file`、`delay`（默认 300） |
| 顺序 | 先比不带 {参数} 的路径，再比模板路径；同一类中按目录名字母顺序（account、ai、breed、common、farm、feed、finance、health、herd、inspect、media、stock、today、zz_fallback）、文件内从上到下，命中第一条；带条件的规则放在默认规则前 |
| 状态码 | 取 code 前三位（0 为 200）；未配置返回 50001“Mock 未配置：方法 路径”，Logcat 标签 ZmtMock |
| 已有 | common 目录：A04 两个账号、A08、F02（1001 所有者 / 员工）、U09、U10（PEN_TYPE、DEATH_REASON） |

```json
[{"method": "GET", "path": "/api/v1/farms/{farmId}/members", "when": {"header": {"Authorization": "Bearer mock-staff"}}, "file": "members_staff.json"},
 {"method": "GET", "path": "/api/v1/farms/{farmId}/members", "file": "members.json"}]
```

## 七、单独运行

| 步骤 | 说明 |
|---|---|
| 1 | gradle.properties 中 `runAlone.module_xxx=true`，同步 Gradle |
| 2 | `./gradlew :module_xxx:assembleDebug`，安装后启动“xxx（单独运行）” |
| 3 | 启动页先走隐私政策，点“以所有者登录 / 以员工登录”（Mock A04 + F02），再点 debugPages 列出的页面 |
| 4 | 调试完改回 false；依赖的 lib 入口在 `src/alone/java/.../XxxAloneApp` 中列出 |

## 八、常见坑

| 问题 | 做法 |
|---|---|
| nonTransitiveRClass | 引用 lib_base 资源写 `com.zhimu.slmp.base.R.drawable.ic_add`，本组件资源用本组件 R；XML 中直接 `@drawable/ic_add` |
| 资源重名 | 资源名一律带组件前缀（account_、farm_、today_、herd_） |
| ViewModel 构造中读参数 | 拿不到 SavedStateHandle，改在 `onSavedStateAttached` 中读取 |
| LiveData 线程 | ViewModel 中 `launch` 的回调已在主线程；Repository 中不要 setValue |
| 40101 / 40104 | 由网络层与 BaseApplication 处理，ViewModel 不要提示 |
| 列表空状态 | `load` 对 null、空集合、PageVO 空列表自动切换 EMPTY；其他判断重写 `isEmptyData` |
| 隐式跳转打不开 | 清单 intent-filter 缺少 `android.intent.category.DEFAULT`，或忘了加 action |
| Mock 与令牌刷新 | Mock 响应不经过 TokenAuthenticator，40101 刷新只在真实服务端验证 |
| AppGlideModule | 全应用只有 lib_base 的 ZmtGlideModule，组件不要再定义 |
| 系统栏 insets | 二级页保持 BaseActivity 默认（根视图加状态栏、导航栏、软键盘内边距并消费，base_include_top_app_bar 与 BottomActionBar 不会重复加）；只有内容要延伸到状态栏下的页面（相机、全屏预览、地图选点）重写 `applyWindowInsetsToRoot()` 返回 false 并自行用 InsetsHelper 处理 |
| RecyclerView 头部 | 以 RecyclerView 为 parent 调用 `XxxBinding.inflate(inflater, list, false)` 前必须先 `setLayoutManager`，否则运行时崩溃“RecyclerView has no LayoutManager”（编译期发现不了） |
| Paging 分组标题 | insertSeparators 的顶部标题在首屏数据之后插入，列表会停在第一项而不是标题；注册 AdapterDataObserver，顶部插入且原本在顶部时 scrollToPosition(0)（见 MessageCenterActivity） |
| 顶部栏标题 | base_include_top_app_bar 的 title 绑定为空时不覆盖，代码中用 TopAppBarHelper.setupBack 设置标题即可 |
| 页面跳转提示 | `UiMessage.withPage(text, "查看", RouterPath.U02)`：操作点击后由基类直接打开页面，全局提示（GlobalEvents.postMessage）也可用 |
| 进入页面提示 | 打开页面时带 `RouterPath.EXTRA_SWITCH_TIP`（= BaseActivity.EXTRA_ENTRY_TIP），目标页布局后显示一次 Snackbar |
| 确认后保持打开 | `ConfirmDialogFragment / ReasonDialogFragment.builder(key).keepOpenOnConfirm()`；宿主在 submitting 变化时 `ConfirmDialogFragment.setLoading(fm, key, loading)`，成功后 `dismiss(fm, key)` |
| 标签切换带参数 | `MainTabHost.selectTab(tabKey, args)`；目标 Fragment 监听 `MainTabHost.argsResultKey(tabKey)` |
| 输入框 | 布局中用 `com.zhimu.slmp.base.widget.ZmtTextInputLayout`（出错时描边 2 dp），用法与 TextInputLayout 相同 |
| 危险操作行 | ListRowView `app:rowDanger="true"` + `app:leadingTone="error"`；更多菜单 primary 项 `MoreMenuHelper.addPrimary` |
| 标签页 ViewModel 范围 | 切换或退出养殖场后 MainActivity 会按新角色移除并重建标签页；需要跨重建保留事件的一级页用 `obtainActivityViewModel` |
| 菜单图标颜色 | 图标默认黑色，顶部栏菜单在 menu xml 写 `app:iconTint="?attr/colorOnSurfaceVariant"`，或调用 `TopAppBarHelper.tintMenu` |

## 九、UI 套件速查（lib_base 的 res、base.widget、base.dialog）

| 类别 | 内容 |
|---|---|
| 主题 | `Theme.Zmt`（全应用）、`Theme.Zmt.Media`（全屏媒体页）；颜色用 `?attr/colorXxx`，自定义属性 colorWarning*、colorSuccess*、colorScrim、colorMediaBackdrop、colorOnMedia；`@color/zm_*` |
| 字阶与形状 | `TextAppearance.Zmt.*`（13 级，含等宽数字 `.Number`）、`ShapeAppearance.Zmt.Corner.*`、间距 `@dimen/base_space_*` |
| 组件样式 | `Widget.Zmt.*`：按钮（填充 / 色调 / 描边 / 文字 / 危险 / 图标，默认 40 dp，`.Small` 32 dp、`.Large` 56 dp）、TextInputLayout（含 ReadOnly、Picker、Dropdown）、Chip（Filter、Choice、Dropdown）、连接按钮组、底部导航、顶部栏、卡片、对话框、扩展型 FAB 等；一级页大标题 `Widget.Zmt.TextView.PageTitle` / `PageSubtitle` |
| 图标 | `@drawable/ic_<名称>`、`ic_<名称>_filled`（Material Symbols Rounded，128 个）；缺图标时 `python tools/icons/fetch_icons.py --out <组件>/src/main/res/drawable --prefix <组件前缀> <图标名>` 生成到本组件 |
| 控件 | StateLayout（`render(UiState)`、重试监听）、NoticeView、StatCardView、StatusTagView、SectionHeaderView、ListRowView、KeyValueRowView、AvatarView、BottomActionBar、SkeletonView、ConnectedButtonGroup、GroupedListLayout / GroupedListDecoration（分组圆角）、ButtonLoading（按钮内加载）、TopAppBarHelper、WidgetBindingAdapters（`app:uiState`、`app:loading`） |
| 二级页顶部栏 | `<include layout="@layout/base_include_top_app_bar" app:title="@{...}"/>` 或 `TopAppBarHelper.setupBack(toolbar, title)` |
| 弹窗 | `TipPopup.show / showRich`；`MoreMenuHelper.with(anchor).add / addReview / addDanger`；`BaseBottomSheetDialogFragment`（子类实现 onCreateContentView，`setResultAndDismiss` 回传）；`OptionSheetDialogFragment.builder(key)`；`ConfirmDialogFragment.builder(key)`、`ReasonDialogFragment.builder(key)`（结果 `isConfirmed()` / `getReason()`）；`DateTimePicker.date(key) / time(key)`；`DialogGuard.show`；结果全部经 FragmentResult |
| lib_common 页面与控件 | U03 `common.ui.status.StatusActivity`（参数 RouterPath.EXTRA_STATUS_TYPE、EXTRA_TITLE、EXTRA_MESSAGE、EXTRA_TRACE_ID、EXTRA_PAGE_TITLE、EXTRA_ACTION_TEXT、EXTRA_ERROR_CODE）；一级页顶部栏 `common.widget.FirstLevelTopBar`（`bind(host, owner)`、`setScanVisible`、`setAllFarmsMode`、`bindLiftOnScroll`） |

各类的具体用法以类注释为准。

## 十、lib_media 速查（照片与扫码，`media.*`）

| 用途 | 做法 |
|---|---|
| 照片网格（F02、F06 资质图，L11 死淘照片） | ViewModel 构造注入 `UploadManager.Factory uploads`（依赖都来自 CoreComponent，业务组件不用加 Module）；在 `onSavedStateAttached(h)` 中 `photos = uploads.create(h, "photos", MediaScene.DEATH, 6)`，`onCleared` 中 `photos.release()`；编辑模式 `setInitialFiles(list)`（只生效一次），F06 新增标签 `setNewItemTag("新增", StatusTagView.PRIMARY)` |
| 布局 | `<com.zhimu.slmp.media.picker.MediaPickerView android:id=...>`（必须有 id），可选 `app:mediaCameraTitle`、`mediaShowCount`、`mediaAddIcon`、`mediaDeletedText`；在 Activity.onCreate / Fragment.onViewCreated 中 `binding.photos.bind(this, vm.getPhotos())`（页面 STARTED 之前）；详情页只读用 `setReadOnlyFiles(list, 来源说明)` |
| 提交 | `hasUploading()` 为真时等待；`getSubmitBlockMessage()` 非空时提示并不提交；请求体用 `getFileIds()`（按网格顺序） |
| 扫码（L07） | `registerForActivityResult(new CameraContract(), ...)`，参数 `CameraArgs.scan().title("扫码查看档案").scanHint(...).manualInput("输入批次号", "...")`；结果 `TYPE_SCAN` 用 `getScanText()` 取原文，`TYPE_MANUAL_INPUT` 由调用方打开输入面板；需要在自己页面内连续扫码时可直接用 `scan.QrCodeAnalyzer` |
| 全屏预览 | `MediaViewer.openFiles(ctx, files, index, 来源说明)` |
| Mock 示例图 | `file:///android_asset/mock/media/` 下 qual_license.jpg（9001）、qual_epidemic.jpg（9002）、farm_env.jpg（9003）、death_photo.jpg（9004）、inspection.jpg（9005）、upload_sample.jpg（U05 上传返回）；其他模块 Mock 中的 FileVO 直接引用这些地址与 file_id |
| 图片绑定 | 原图 `app:imageUrl` + `app:imageCacheKey`，缩略图 `app:thumbUrl` + `app:thumbCacheKey`（缓存键分别为 file_{id}、file_{id}_thumb）；只有 http(s) 地址按 file_id 缓存，本地与 asset 地址直接加载 |
| Mock 递增编号 | 响应文件中的 `{{seq}}` 每次请求替换为递增数字（U05 的 file_id 已使用），新增类接口返回新 ID 时也可以用 |
