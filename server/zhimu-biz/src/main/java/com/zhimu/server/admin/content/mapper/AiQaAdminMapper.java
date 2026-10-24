package com.zhimu.server.admin.content.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhimu.server.admin.content.query.ContentQueries;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** AI 问答记录管理端查询与状态更新。 */
public interface AiQaAdminMapper {

    IPage<QaRow> selectPage(Page<QaRow> page, @Param("q") ContentQueries.QaQuery q,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                           @Param("farmIds") List<Long> farmIds, @Param("userIds") List<Long> userIds,
                           @Param("segment") String segment, @Param("onlySpotCheck") boolean onlySpotCheck);

    long countPage(@Param("q") ContentQueries.QaQuery q,
                   @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                   @Param("farmIds") List<Long> farmIds, @Param("userIds") List<Long> userIds,
                   @Param("segment") String segment, @Param("onlySpotCheck") boolean onlySpotCheck);

    /** ADM-C65 导出用：同样的筛选条件，不分页，最多 5 万行 */
    List<QaRow> selectForExport(@Param("q") ContentQueries.QaQuery q,
                                @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                @Param("farmIds") List<Long> farmIds, @Param("userIds") List<Long> userIds,
                                @Param("segment") String segment, @Param("onlySpotCheck") boolean onlySpotCheck);

    QaDetailRow selectDetail(@Param("messageId") Long messageId);

    List<QaRoundRow> selectRounds(@Param("sessionId") Long sessionId);

    List<CitationRow> selectCitations(@Param("messageIds") List<Long> messageIds);

    QaSummaryRow selectSummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    int updateHandleStatus(@Param("messageId") Long messageId, @Param("oldStatus") String oldStatus,
                           @Param("newStatus") String newStatus);

    int recomputeSessionCorrection(@Param("sessionId") Long sessionId);

    long countSimilarNoBasis(@Param("from") LocalDateTime from, @Param("keywords") List<String> keywords,
                             @Param("excludeMessageId") Long excludeMessageId);
}
