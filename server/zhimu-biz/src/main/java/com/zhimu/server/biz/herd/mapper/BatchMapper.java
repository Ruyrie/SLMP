package com.zhimu.server.biz.herd.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhimu.server.biz.herd.entity.Batch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 批次 Mapper：批次号取号与扫码查找用自定义 SQL（调用方包在 TenantSkip 内自行按养殖场判断）。 */
public interface BatchMapper extends BaseMapper<Batch> {

    /** 全平台当月已用的最大流水号（含已删除，删除后不复用） */
    @Select("SELECT COALESCE(MAX(no_seq), 0) FROM t_batch WHERE no_month = #{noMonth}")
    int maxSeqOfMonth(@Param("noMonth") String noMonth);

    /** 按批次号查（含已删除，自行判断 deleted） */
    @Select("SELECT * FROM t_batch WHERE batch_no = #{batchNo} LIMIT 1")
    Batch selectByBatchNoIncludingDeleted(@Param("batchNo") String batchNo);

    /** 按二维码令牌查（含已删除） */
    @Select("SELECT * FROM t_batch WHERE qr_token = #{qrToken} LIMIT 1")
    Batch selectByQrTokenIncludingDeleted(@Param("qrToken") String qrToken);

    /** 按 ID 查（含已删除，用于区分 40401 与 41001） */
    @Select("SELECT * FROM t_batch WHERE id = #{id} LIMIT 1")
    Batch selectByIdIncludingDeleted(@Param("id") Long id);

    /** 批次是否已有入栏之外的流水（后续记录口径，含已作废 SALE） */
    @Select("SELECT COUNT(1) FROM t_batch_event WHERE batch_id = #{batchId} AND event_type <> 'ENTRY' AND deleted = 0")
    long countFollowUpEvents(@Param("batchId") Long batchId);
}
