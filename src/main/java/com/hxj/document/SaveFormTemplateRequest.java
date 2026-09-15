package com.hxj.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 保存表单模板请求（字段整体替换）。
 *
 * <p>契约哲学（对齐钉钉 formComponents 模型）：调用方只声明意图，可推导项由服务端收编——
 * 分类（category）与单号前缀（docPrefix）由 businessType 派生；字段排序由数组顺序推导；
 * 提升字段（reserved）由服务端白名单标记。调用方传多余字段将被忽略。
 */
public record SaveFormTemplateRequest(
        @Schema(description = "业务类型键 DAILY_PAYMENT/BUSINESS_PAYMENT/SEAL_APPLICATION——"
                + "决定单号前缀（BX/FK/YY）、台账分类与工作台卡片归属") String businessType,
        @Schema(description = "模板名（显示名，唯一）") String name,
        @Schema(description = "绑定的流程配置 ID（必须为已存在的流程）") Long flowConfigId,
        @Schema(description = "必附材料清单") List<String> attachmentRequirements,
        @Schema(description = "字段清单（整体替换；排序 = 数组顺序）") List<FieldPayload> fields) {

    public record FieldPayload(
            @Schema(description = "字段键（稳定标识，历史单据渲染依赖；同时是流程条件变量名）") String fieldKey,
            @Schema(description = "显示名") String label,
            @Schema(description = "控件类型 TEXT/TEXTAREA/NUMBER/DATE/SELECT/BOOLEAN/IMAGE/ATTACHMENT/TABLE") String controlType,
            @Schema(description = "是否必填") Boolean required,
            @Schema(description = "SELECT 选项 / TABLE 列结构（JSON）") List<Object> options,
            @Schema(description = "是否启用") Boolean enabled,
            @Schema(description = "是否参与流程条件（字段即变量）；不传按保留键默认规则推导") Boolean processVariable) {

        /** 兼容未声明条件参与的调用方（保留键默认参与，其余默认不参与）。 */
        public FieldPayload(String fieldKey, String label, String controlType,
                            Boolean required, List<Object> options, Boolean enabled) {
            this(fieldKey, label, controlType, required, options, enabled, null);
        }
    }
}
