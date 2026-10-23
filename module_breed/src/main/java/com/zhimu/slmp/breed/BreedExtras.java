package com.zhimu.slmp.breed;

import com.zhimu.slmp.common.router.RouterPath;

/**
 * 本组件新增的页面参数键、结果键与内部页面 action（RouterPath 中没有的，交付时由主会话合并进 RouterPath）。
 * 通用参数（pen_id、batch_id、todo_id、biz_type、biz_id、mode 等）直接用 RouterPath。
 */
public final class BreedExtras {

    private BreedExtras() {
    }

    /** 繁育记录（String，BreedingRecordVO 的 JSON）：B03 / B05 修改模式的初值 */
    public static final String EXTRA_RECORD_JSON = "record_json";
    /** 本组件页面以“需要结果”打开时为 true：登记成功后 setResult 带回提示，由上一页显示 Snackbar */
    public static final String EXTRA_FOR_RESULT = "breed_for_result";

    public static final String TAB_FARROW = "FARROW";
    public static final String TAB_HATCH = "HATCH";

    /** B01 的 EXTRA_MODE：已淘汰和死亡的母猪（只读列表） */
    public static final String MODE_EXITED = "EXITED";

    /** 档案表单（B06 编辑档案 / B01 新增母猪，无编号页面） */
    public static final String ACTION_ANIMAL_FORM = RouterPath.PREFIX + "B06_FORM";

    /** 结果：提示文案（String） */
    public static final String RESULT_MESSAGE = "breed_result_message";
    /** 结果：新生成的自繁批次（long，Snackbar“查看批次”） */
    public static final String RESULT_BATCH_ID = "breed_result_batch_id";
    /** 结果：死亡淘汰后返回 B01（boolean） */
    public static final String RESULT_TO_LIST = "breed_result_to_list";
}
