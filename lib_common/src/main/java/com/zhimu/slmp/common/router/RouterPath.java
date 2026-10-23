package com.zhimu.slmp.common.router;

/**
 * 页面路由常量：常量名即页面编号，值为清单中 intent-filter 的 action（"com.zhimu.slmp.page." + 编号）；
 * 页面清单见《01-项目概述与页面清单》第四节，共 104 个编号页面，另有 MAIN 与 4 个无编号页面。
 * 参数键与 JSON 字段同名（下划线小写）。
 */
public final class RouterPath {

    private RouterPath() {
    }

    /** action 前缀 */
    public static final String PREFIX = "com.zhimu.slmp.page.";

    // ===== 公共数据 · 通用页面 =====
    /** U01 提交修改申请 */
    public static final String U01 = PREFIX + "U01";
    /** U02 我的修改申请 */
    public static final String U02 = PREFIX + "U02";
    /** U03 通用状态页 */
    public static final String U03 = PREFIX + "U03";

    // ===== 模块01 账号与个人中心 =====
    /** A01 登录 */
    public static final String A01 = PREFIX + "A01";
    /** A02 注册账号 */
    public static final String A02 = PREFIX + "A02";
    /** A03 重置密码 */
    public static final String A03 = PREFIX + "A03";
    /** A04 保存恢复码 */
    public static final String A04 = PREFIX + "A04";
    /** A05 找回账号 */
    public static final String A05 = PREFIX + "A05";
    /** A06 找回后首次登录 */
    public static final String A06 = PREFIX + "A06";
    /** A07 我的 */
    public static final String A07 = PREFIX + "A07";
    /** A08 账号与安全 */
    public static final String A08 = PREFIX + "A08";
    /** A09 注销账号 */
    public static final String A09 = PREFIX + "A09";

    // ===== 模块02 养殖场与成员管理 =====
    /** F01 切换养殖场 */
    public static final String F01 = PREFIX + "F01";
    /** F02 新建养殖场 */
    public static final String F02 = PREFIX + "F02";
    /** F03 审核结果 */
    public static final String F03 = PREFIX + "F03";
    /** F04 养殖场位置选点 */
    public static final String F04 = PREFIX + "F04";
    /** F05 养殖场信息 */
    public static final String F05 = PREFIX + "F05";
    /** F06 资料变更 */
    public static final String F06 = PREFIX + "F06";
    /** F07 成员管理 */
    public static final String F07 = PREFIX + "F07";
    /** F08 邀请成员 */
    public static final String F08 = PREFIX + "F08";
    /** F09 成员详情 */
    public static final String F09 = PREFIX + "F09";
    /** F10 转让所有权 */
    public static final String F10 = PREFIX + "F10";

    // ===== 模块03 今日首页、日历待办与消息中心 =====
    /** T01 今日首页 */
    public static final String T01 = PREFIX + "T01";
    /** T02 快捷登记面板 */
    public static final String T02 = PREFIX + "T02";
    /** T03 养殖日历 */
    public static final String T03 = PREFIX + "T03";
    /** T04 待办详情 */
    public static final String T04 = PREFIX + "T04";
    /** T05 提醒设置 */
    public static final String T05 = PREFIX + "T05";
    /** T06 消息中心 */
    public static final String T06 = PREFIX + "T06";
    /** T07 通知详情 */
    public static final String T07 = PREFIX + "T07";
    /** T08 关联记录已失效 */
    public static final String T08 = PREFIX + "T08";

    // ===== 模块04 栏舍与存栏 =====
    /** L01 生产页 */
    public static final String L01 = PREFIX + "L01";
    /** L02 我的栏舍 */
    public static final String L02 = PREFIX + "L02";
    /** L03 存栏总览 */
    public static final String L03 = PREFIX + "L03";
    /** L04 栏舍详情 */
    public static final String L04 = PREFIX + "L04";
    /** L05 新增栏舍 */
    public static final String L05 = PREFIX + "L05";
    /** L06 批次档案 */
    public static final String L06 = PREFIX + "L06";
    /** L07 扫码查看档案 */
    public static final String L07 = PREFIX + "L07";
    /** L08 批次入栏 */
    public static final String L08 = PREFIX + "L08";
    /** L09 入栏成功 */
    public static final String L09 = PREFIX + "L09";
    /** L10 转栏登记 */
    public static final String L10 = PREFIX + "L10";
    /** L11 死淘登记 */
    public static final String L11 = PREFIX + "L11";

    // ===== 模块05 繁育管理 =====
    /** B01 种畜与待产 */
    public static final String B01 = PREFIX + "B01";
    /** B02 待产与待出雏 */
    public static final String B02 = PREFIX + "B02";
    /** B03 登记配种 / 妊检 */
    public static final String B03 = PREFIX + "B03";
    /** B04 登记产仔 */
    public static final String B04 = PREFIX + "B04";
    /** B05 登记断奶 */
    public static final String B05 = PREFIX + "B05";
    /** B06 种畜档案 */
    public static final String B06 = PREFIX + "B06";
    /** B07 禽类孵化 */
    public static final String B07 = PREFIX + "B07";

    // ===== 模块06 饲喂与生长称重 =====
    /** G01 今日饲喂任务 */
    public static final String G01 = PREFIX + "G01";
    /** G02 登记投喂 */
    public static final String G02 = PREFIX + "G02";
    /** G03 饲喂方案 */
    public static final String G03 = PREFIX + "G03";
    /** G04 饲喂执行情况 */
    public static final String G04 = PREFIX + "G04";
    /** G05 称重登记 */
    public static final String G05 = PREFIX + "G05";
    /** G06 生长曲线 */
    public static final String G06 = PREFIX + "G06";

    // ===== 模块07 日常巡检与异常上报 AI 诊断 =====
    /** I01 今日巡检 */
    public static final String I01 = PREFIX + "I01";
    /** I02 登记巡检 */
    public static final String I02 = PREFIX + "I02";
    /** I03 补录巡检 */
    public static final String I03 = PREFIX + "I03";
    /** I04 巡检历史 */
    public static final String I04 = PREFIX + "I04";
    /** I05 异常上报 */
    public static final String I05 = PREFIX + "I05";
    /** I06 诊断详情 */
    public static final String I06 = PREFIX + "I06";
    /** I07 登记处理结果 */
    public static final String I07 = PREFIX + "I07";
    /** I08 拍照 / 录像 */
    public static final String I08 = PREFIX + "I08";
    /** I09 照片和视频全屏预览 */
    public static final String I09 = PREFIX + "I09";

    // ===== 模块08 免疫、用药与消毒 =====
    /** D01 免疫计划 */
    public static final String D01 = PREFIX + "D01";
    /** D02 添加免疫项目 */
    public static final String D02 = PREFIX + "D02";
    /** D03 登记接种 */
    public static final String D03 = PREFIX + "D03";
    /** D04 登记用药 */
    public static final String D04 = PREFIX + "D04";
    /** D05 用药档案 */
    public static final String D05 = PREFIX + "D05";
    /** D06 消毒计划 */
    public static final String D06 = PREFIX + "D06";
    /** D07 新建消毒计划 */
    public static final String D07 = PREFIX + "D07";
    /** D08 登记消毒 */
    public static final String D08 = PREFIX + "D08";
    /** D09 消毒任务 */
    public static final String D09 = PREFIX + "D09";

    // ===== 模块09 饲料与兽药疫苗库存 =====
    /** M01 饲料库存 */
    public static final String M01 = PREFIX + "M01";
    /** M02 饲料档案 */
    public static final String M02 = PREFIX + "M02";
    /** M03 饲料入库 */
    public static final String M03 = PREFIX + "M03";
    /** M04 盘点与报损 */
    public static final String M04 = PREFIX + "M04";
    /** M05 兽药与疫苗库存 */
    public static final String M05 = PREFIX + "M05";
    /** M06 批号详情 */
    public static final String M06 = PREFIX + "M06";
    /** M07 登记报废 */
    public static final String M07 = PREFIX + "M07";
    /** M08 兽药与疫苗入库 */
    public static final String M08 = PREFIX + "M08";
    /** M09 药品档案 */
    public static final String M09 = PREFIX + "M09";
    /** M10 饲料其他出库 */
    public static final String M10 = PREFIX + "M10";
    /** M11 兽药与疫苗出库 */
    public static final String M11 = PREFIX + "M11";

    // ===== 模块10 收支、出栏、行情与报表 =====
    /** E01 经营页 */
    public static final String E01 = PREFIX + "E01";
    /** E02 收支记账 */
    public static final String E02 = PREFIX + "E02";
    /** E03 记一笔 */
    public static final String E03 = PREFIX + "E03";
    /** E04 收支流水 */
    public static final String E04 = PREFIX + "E04";
    /** E05 支出分类 */
    public static final String E05 = PREFIX + "E05";
    /** E06 登记出栏单 */
    public static final String E06 = PREFIX + "E06";
    /** E07 出栏单详情 */
    public static final String E07 = PREFIX + "E07";
    /** E08 出栏单修改申请 */
    public static final String E08 = PREFIX + "E08";
    /** E09 待收款 */
    public static final String E09 = PREFIX + "E09";
    /** E10 批次核算 */
    public static final String E10 = PREFIX + "E10";
    /** E11 市场行情 */
    public static final String E11 = PREFIX + "E11";
    /** E12 AI 出栏建议 */
    public static final String E12 = PREFIX + "E12";
    /** E13 经营报表 */
    public static final String E13 = PREFIX + "E13";
    /** E14 出栏单列表 */
    public static final String E14 = PREFIX + "E14";

    // ===== 模块11 AI 养殖助手与养殖课堂 =====
    /** K01 AI 问答 */
    public static final String K01 = PREFIX + "K01";
    /** K02 历史会话 */
    public static final String K02 = PREFIX + "K02";
    /** K03 知识原文 */
    public static final String K03 = PREFIX + "K03";
    /** K04 养殖课堂 */
    public static final String K04 = PREFIX + "K04";
    /** K05 视频播放 · 竖屏 */
    public static final String K05 = PREFIX + "K05";
    /** K06 全屏播放 · 横屏 */
    public static final String K06 = PREFIX + "K06";
    /** K07 专题详情 */
    public static final String K07 = PREFIX + "K07";

    // ===== 无编号页面 =====
    /** 主页（底部导航，app 壳工程） */
    public static final String MAIN = PREFIX + "MAIN";
    /** 修改密码页（A08 进入，无编号） */
    public static final String A08_PASSWORD = PREFIX + "A08_PASSWORD";
    /** 关于（A07 进入，无编号） */
    public static final String A07_ABOUT = PREFIX + "A07_ABOUT";
    /** 协议全文（关于页、注册页、隐私政策对话框进入；参数 EXTRA_MODE） */
    public static final String A07_AGREEMENT = PREFIX + "A07_AGREEMENT";

    // ===== 参数键 =====
    /** 养殖场 ID（long） */
    public static final String EXTRA_FARM_ID = "farm_id";
    /** 栏舍 ID（long） */
    public static final String EXTRA_PEN_ID = "pen_id";
    /** 批次 ID（long） */
    public static final String EXTRA_BATCH_ID = "batch_id";
    /** 任务 ID（long） */
    public static final String EXTRA_TASK_ID = "task_id";
    /** 待办 ID（long） */
    public static final String EXTRA_TODO_ID = "todo_id";
    /** 消息 ID（long） */
    public static final String EXTRA_MESSAGE_ID = "message_id";
    /** 用户 ID（long） */
    public static final String EXTRA_USER_ID = "user_id";
    /** 邀请 ID（long） */
    public static final String EXTRA_INVITE_ID = "invite_id";
    /** 转让 ID（long） */
    public static final String EXTRA_TRANSFER_ID = "transfer_id";
    /** 修改申请 ID（long） */
    public static final String EXTRA_REQUEST_ID = "request_id";
    /** 业务记录类型 BIZ_TYPE（String） */
    public static final String EXTRA_BIZ_TYPE = "biz_type";
    /** 业务记录 ID（long） */
    public static final String EXTRA_BIZ_ID = "biz_id";
    /** 页面模式（String，各页面自定义取值） */
    public static final String EXTRA_MODE = "mode";
    /** 目标页面编号（String，推送与消息） */
    public static final String EXTRA_ROUTE = "route";
    /** 原因（String；A01 为登录失效原因 PASSWORD_CHANGED 等） */
    public static final String EXTRA_REASON = "reason";
    /** U03 状态类型（String） */
    public static final String EXTRA_STATUS_TYPE = "status_type";
    /** 页面标题（String） */
    public static final String EXTRA_TITLE = "title";
    /** 链路编号（String，U03 加载失败） */
    public static final String EXTRA_TRACE_ID = "trace_id";
    /** 手机号（String，A01–A05 预填） */
    public static final String EXTRA_PHONE = "phone";
    /** 强制步骤 next_step（String） */
    public static final String EXTRA_NEXT_STEP = "next_step";
    /** 日期 yyyy-MM-dd（String） */
    public static final String EXTRA_DATE = "date";
    /** 网页地址（String） */
    public static final String EXTRA_URL = "url";
    /** 说明文字（String，U03） */
    public static final String EXTRA_MESSAGE = "message";
    /** 顶部栏标题（String，U03 保留目标页标题） */
    public static final String EXTRA_PAGE_TITLE = "page_title";
    /** 操作按钮文字（String，U03 无权查看版“返回消息中心”） */
    public static final String EXTRA_ACTION_TEXT = "action_text";
    /** 错误码（int，U03 加载失败按错误码选择默认说明，-1 为网络不可用） */
    public static final String EXTRA_ERROR_CODE = "error_code";
    /** 打开 MAIN 时切到的底部标签（String，TabRegistry 的 key） */
    public static final String EXTRA_MAIN_TAB = "main_tab";
    /** 打开 MAIN 时转交给该标签的参数（Bundle，标签页用 MainTabHost.argsResultKey 接收） */
    public static final String EXTRA_MAIN_TAB_ARGS = "main_tab_args";
    /** 推荐问题（String，T01 AI 入口打开 K01） */
    public static final String EXTRA_QUESTION = "question";
    /** 待办类别（String，TODO_CATEGORY，T01 进入 T03 时预选） */
    public static final String EXTRA_CATEGORY = "category";
    /** 跨养殖场切换后的提示（String，例如“已切换到西岭鸡场”） */
    public static final String EXTRA_SWITCH_TIP = com.zhimu.slmp.base.ui.BaseActivity.EXTRA_ENTRY_TIP;
    /** 文件编号（long，打开 I09 预览） */
    public static final String EXTRA_FILE_ID = "file_id";
    /** T08 关联记录已失效的标题与说明（String） */
    public static final String EXTRA_DELETED_TITLE = "deleted_title";
    public static final String EXTRA_DELETED_DESC = "deleted_desc";
    /** 成员编号（long，消息、待办打开 F09 成员详情） */
    public static final String EXTRA_MEMBER_ID = "member_id";
    /** 批次事件编号（long，入栏、转栏、死淘记录） */
    public static final String EXTRA_EVENT_ID = "event_id";
    /** L03 存栏总览视图（String：PEN / CATEGORY / BATCH） */
    public static final String EXTRA_VIEW_TYPE = "view_type";

    /**
     * EXTRA_MODE 取值：以 Activity Result 打开 A01（推送冷启动未登录时）；登录成功且没有强制步骤时
     * setResult(RESULT_OK) 并关闭，由调用方继续原来的跳转
     */
    public static final String MODE_LOGIN_FOR_RESULT = "push";

    /** 记录编号（long，投喂、接种、用药、消毒、繁育等记录的修改与查看，模块05–08 共用） */
    public static final String EXTRA_RECORD_ID = "record_id";
    /** 计划编号（long，免疫计划、消毒计划） */
    public static final String EXTRA_PLAN_ID = "plan_id";
    /** 种畜编号（long，B06） */
    public static final String EXTRA_ANIMAL_ID = "animal_id";
    /** 孵化编号（long，B07） */
    public static final String EXTRA_INCUBATION_ID = "incubation_id";
    /** 巡检记录编号（long，I02 修改与查看） */
    public static final String EXTRA_INSPECTION_ID = "inspection_id";
    /** 异常上报编号（long，I06；D04 关联上报） */
    public static final String EXTRA_REPORT_ID = "report_id";
    /** 诊断编号（long，I06、I07；D04 关联诊断） */
    public static final String EXTRA_DIAGNOSIS_ID = "diagnosis_id";
    /** 称重记录编号（long，G05） */
    public static final String EXTRA_WEIGH_RECORD_ID = "weigh_record_id";
    /** 饲料编号（long，打开 M03 时预填饲料） */
    public static final String EXTRA_FEED_ID = "feed_id";
    /** 页内分段（String，例如 B02 的 FARROW / HATCH） */
    public static final String EXTRA_TAB = "tab";

    /** EXTRA_MODE 取值：E13 经营报表只统计本人创建的养殖场（A09 注销前导出） */
    public static final String MODE_OWNED_FARMS = "OWNED_FARMS";

    /** 全部页面编号（RouterTable 使用） */
    static final String[] PAGE_CODES = {
            "U01", "U02", "U03", "A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09",
            "F01", "F02", "F03", "F04", "F05", "F06", "F07", "F08", "F09", "F10", "T01", "T02",
            "T03", "T04", "T05", "T06", "T07", "T08", "L01", "L02", "L03", "L04", "L05", "L06",
            "L07", "L08", "L09", "L10", "L11", "B01", "B02", "B03", "B04", "B05", "B06", "B07",
            "G01", "G02", "G03", "G04", "G05", "G06", "I01", "I02", "I03", "I04", "I05", "I06",
            "I07", "I08", "I09", "D01", "D02", "D03", "D04", "D05", "D06", "D07", "D08", "D09",
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11", "E01",
            "E02", "E03", "E04", "E05", "E06", "E07", "E08", "E09", "E10", "E11", "E12", "E13",
            "E14", "K01", "K02", "K03", "K04", "K05", "K06", "K07", "MAIN", "A08_PASSWORD", "A07_ABOUT", "A07_AGREEMENT"
    };
}
