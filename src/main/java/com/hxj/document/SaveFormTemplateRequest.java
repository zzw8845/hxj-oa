package com.hxj.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 保存表单模板请求（字段整体替换）。 */
public record SaveFormTemplateRequest(
        @Schema(description = "业务类型键（唯一）") String businessType,
        @Schema(description = "模板名（显示名）") String name,
        @Schema(description = "单号前缀") String docPrefix,
        @Schema(description = "绑定的流程配置 ID") Long flowConfigId,
        @Schema(description = "粗分类（DAILY_PAYMENT/BUSINESS_PAYMENT/SEAL_APPLICATION）") String category,
        @Schema(description = "必附材料清单") List<String> attachmentRequirements,
        @Schema(description = "字段清单（整体替换）") List<FieldPayload> fields) {

    public record FieldPayload(
            @Schema(description = "字段键（稳定标识）") String fieldKey,
            @Schema(description = "显示名") String label,
            @Schema(description = "控件类型 TEXT/TEXTAREA/NUMBER/DATE/SELECT/BOOLEAN/IMAGE/ATTACHMENT/TABLE") String controlType,
            @Schema(description = "是否必填") Boolean required,
            @Schema(description = "SELECT 选项 / TABLE 列结构（JSON）") List<Object> options,
            @Schema(description = "是否提升字段（不可删除）") Boolean reserved,
            @Schema(description = "排序号") Integer sortOrder,
            @Schema(description = "是否启用") Boolean enabled) {
    }
}
