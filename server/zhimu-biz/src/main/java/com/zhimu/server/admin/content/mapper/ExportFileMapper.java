package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.common.entity.FileEntity;

/** 管理端内容运营导出文件（写 t_file，scene = EXPORT）。 */
public interface ExportFileMapper extends BaseMapper<FileEntity> {
}
