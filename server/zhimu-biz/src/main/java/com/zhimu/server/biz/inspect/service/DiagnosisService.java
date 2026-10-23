package com.zhimu.server.biz.inspect.service;

import com.zhimu.server.biz.inspect.entity.AbnormalReport;
import com.zhimu.server.biz.inspect.entity.AiDiagnosis;

/** AI 诊断执行：创建尝试、异步调用、失败文案。 */
public interface DiagnosisService {

    /** 创建一次 RUNNING 尝试并把上报置为诊断中。 */
    AiDiagnosis createAttempt(AbnormalReport report, String triggerType, Long requestedBy);

    /** 异步执行（@Async("aiExecutor")）。 */
    void executeAsync(Long diagnosisId);

    /** 同步执行一次尝试（单元测试直接调用）。 */
    void execute(Long diagnosisId);

    /** 最近一次失败的中文说明；没有失败返回 null。 */
    String lastFailureText(AbnormalReport report);
}
