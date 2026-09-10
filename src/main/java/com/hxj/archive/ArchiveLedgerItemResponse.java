package com.hxj.archive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 归档台账列表条目。 */
public record ArchiveLedgerItemResponse(
        @Schema(description = "台账条目ID") Long id,
        @Schema(description = "关联单据ID") Long documentId,
        @Schema(description = "单据编号") String docCode,
        @Schema(description = "项目名称") String projectName,
        @Schema(description = "业务类型") String businessType,
        @Schema(description = "单据类型") String documentType,
        @Schema(description = "公司") String company,
        @Schema(description = "申请人") String applicant,
        @Schema(description = "申请部门") String department,
        @Schema(description = "金额") BigDecimal amount,
        @Schema(description = "归档时间") LocalDateTime archivedAt) {
}