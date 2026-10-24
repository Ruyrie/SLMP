package com.zhimu.server.admin.base.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-D29 兽药疫苗目录导入结果。 */
@Data
public class DrugImportResultVO {

    private Integer total = 0;
    private Integer success = 0;
    private Integer failed = 0;
    private List<ImportError> errors = new ArrayList<>();
    private String fileName;

    /** 逐行错误；row 为 Excel 行号（表头为第 1 行）。 */
    @Data
    public static class ImportError {
        private Integer row;
        private String message;

        public ImportError() {
        }

        public ImportError(Integer row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
