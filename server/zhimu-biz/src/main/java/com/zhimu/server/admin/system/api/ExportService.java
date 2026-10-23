package com.zhimu.server.admin.system.api;

import com.zhimu.server.admin.system.vo.ExportVO;

/**
 * 统一导出（管理端-04 2.5.9、S14/S19/S32 与 R22 使用）。第 2 批 4B 实现；
 * 4A 只定义接口，供其他管理端模块按依赖注入使用（没有实现时调用方应使用 ObjectProvider）。
 */
public interface ExportService {

    /** 生成 xlsx 并返回下载信息；文件名、条数上限与日志由实现负责 */
    ExportVO export(ExportRequest request);
}
