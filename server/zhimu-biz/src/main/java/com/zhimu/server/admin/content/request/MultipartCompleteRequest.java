package com.zhimu.server.admin.content.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** ADM-S36 合并分片并进入转码。 */
@Data
public class MultipartCompleteRequest {

    @NotEmpty(message = "分片不完整，请重新上传")
    @Valid
    private List<PartRequest> parts = new ArrayList<>();

    @Data
    public static class PartRequest {
        @NotNull(message = "分片不完整，请重新上传")
        private Integer partNo;
        @NotNull(message = "分片不完整，请重新上传")
        private String etag;
    }
}
