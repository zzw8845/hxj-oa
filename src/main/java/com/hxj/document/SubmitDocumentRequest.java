package com.hxj.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 提交单据请求 DTO（表单模板化 v2）。
 *
 * <p>表单字段值全部经 fieldValues 按模板校验与存储；
 * 提升字段（amount/title/involvesFunds/requiresAdminReview/businessMode/needPostMaterial）
 * 由后端从 fieldValues 提取到结构化列或流程变量。
 *
 * <p>approverAccounts 供「发起人自选」节点消费：申请人提交时指定该类节点的
 * 审批人（按选择顺序串行审批）；账号须真实存在，保存时校验。
 */
public record SubmitDocumentRequest(
        @Schema(description = "表单模板 ID（决定表单字段与绑定流程）")
        @NotNull Long templateId,
        @Schema(description = "表单字段值（key → value，按模板定义校验）")
        @NotNull Map<String, Object> fieldValues,
        @Schema(description = "抄送人用户 ID 列表")
        List<Long> ccUserIds,
        @Schema(description = "「发起人自选」节点的审批人账号（按选择顺序串行审批）")
        List<String> approverAccounts,
        @Schema(description = "关联单据 ID（如续签、变更时关联原单据）")
        Long linkedDocumentId) {

    public SubmitDocumentRequest {
        ccUserIds = ccUserIds == null ? List.of() : List.copyOf(ccUserIds);
        approverAccounts = approverAccounts == null ? List.of() : List.copyOf(approverAccounts);
        fieldValues = fieldValues == null ? Map.of() : Map.copyOf(fieldValues);
    }
}
