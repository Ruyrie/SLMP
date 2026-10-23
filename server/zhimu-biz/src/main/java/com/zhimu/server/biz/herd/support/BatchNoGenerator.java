package com.zhimu.server.biz.herd.support;

import com.zhimu.server.biz.herd.entity.Batch;
import com.zhimu.server.biz.herd.mapper.BatchMapper;
import com.zhimu.server.common.util.BeijingTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 批次号生成：ZM + 生成时北京年月 YYMM + “-” + 全平台当月流水号（3 位补零）。
 * 第 3 批先用数据库当月最大流水号 + 1（Redis 不可用时的兜底口径），唯一索引兜底；事务回滚造成的跳号不补。
 */
@Component
@RequiredArgsConstructor
public class BatchNoGenerator {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyMM");

    private final BatchMapper batchMapper;

    public String nextBatchNo() {
        LocalDate today = BeijingTime.today();
        String month = today.format(MONTH);
        int seq = batchMapper.maxSeqOfMonth(month) + 1;
        return batchNo(month, seq);
    }

    /** 由月与流水号拼批次号；超过 999 自然扩为 4 位 */
    public static String batchNo(String month, int seq) {
        return "ZM" + month + "-" + String.format("%03d", seq);
    }

    /** 批次所属月份（生成时的北京年月） */
    public static String currentMonth() {
        return BeijingTime.today().format(MONTH);
    }

    public static Batch withNo(Batch batch, String month, int seq) {
        batch.setNoMonth(month);
        batch.setNoSeq(seq);
        batch.setBatchNo(batchNo(month, seq));
        return batch;
    }
}
