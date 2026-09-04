package com.hxj.document;

import com.hxj.entity.BusinessType;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单据摘要 DTO：列表页展示的单条单据概要信息。
 *
 * <p>用于单据列表查询接口，包含审批流程中用户最关心的核心字段。
 */
public record DocumentSummary(
        @Schema(description = "单据主键 ID")
        Long id,
        @Schema(description = "单据编号（如 OA-PUR-20260903-0001）")
        String docCode,
        @Schema(description = "项目名称")
        String projectName,
        @Schema(description = "业务类型（枚举）")
        BusinessType businessType,
        @Schema(description = "单据类型（枚举）")
        DocumentType documentType,
        @Schema(description = "申请人姓名")
        String applicant,
        @Schema(description = "申请人部门")
        String department,
        @Schema(description = "金额（元）")
        BigDecimal amount,
        @Schema(description = "单据状态（枚举）")
        DocumentStatus status,
        @Schema(description = "当前审批节点名称")
        String currentNode,
        @Schema(description = "风险标记（金额超阈值时标红）")
        boolean riskFlag,
        @Schema(description = "最后更新时间")
        LocalDateTime updatedAt) {
}
