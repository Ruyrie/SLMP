package com.zhimu.server.biz.herd.mapper;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchMapperSqlTest {
    @Test
    void qrLookupSqlCanBeParsedByTheTenantInterceptorsParser() throws Exception {
        Select annotation = BatchMapper.class.getMethod("selectByQrTokenIncludingDeleted", String.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", annotation.value()).replace("#{qrToken}", "?");
        assertThat(CCJSqlParserUtil.parse(sql)).isInstanceOf(net.sf.jsqlparser.statement.select.Select.class);
    }
}
