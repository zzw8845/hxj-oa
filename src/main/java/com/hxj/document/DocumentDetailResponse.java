package com.hxj.document;

import com.hxj.enums.ApprovalActionEnum;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.enums.DocumentTypeEnum;
import com.hxj.workflow.WorkflowHistoryItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单据详情 DTO：单据详情页展示的完整信息。
 *
 * <p>用于 {@code GET /api/documents/{id}} 接口，包含单据基本信息、关联单据、
 * 附件列表、审批记录、抄送记录、流程历史等完整数据。
 */
public record DocumentDetailResponse(
        @Schema(description = "单据主键 ID")
        Long id,
        @Schema(description = "单据编号")
        String docCode,
        @Schema(description = "项目名称")
        String projectName,
        @Schema(description = "业务类型（枚举）")
        BusinessTypeEnum businessType,
        @Schema(description = "单据类型（枚举）")
        DocumentTypeEnum documentType,
        @Schema(description = "申请人姓名")
        String applicant,
        @Schema(description = "申请人部门")
        String department,
        @Schema(description = "金额（元）")
        BigDecimal amount,
        @Schema(description = "单据状态（枚举）")
        DocumentStatusEnum status,
        @Schema(description = "当前审批节点名称")
        String currentNode,
        @Schema(description = "Flowable 流程实例 ID")
        String processInstanceId,
        @Schema(description = "关联单据（如续签时关联原单据）")
        LinkedDocument linkedDocument,
        @Schema(description = "附件列表")
        List<Attachment> attachments,
        @Schema(description = "审批记录列表")
        List<Approval> approvals,
        @Schema(description = "抄送记录列表")
        List<Cc> ccRecords,
        @Schema(description = "流程历史（含各节点操作记录）")
        List<WorkflowHistoryItemResponse> workflowHistory,
        @Schema(description = "表单字段（模板定义与值合并，按模板顺序）")
        List<FormTemplateManagementService.FieldValueView> formFields) {

    /**
     * 紧凑构造器：集合组件防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>详情接口的列表数据直接来自 JPA 查询结果，若不拷贝，
     * 调用方对返回值的修改会写回持久化上下文持有的集合。
     */
    public DocumentDetailResponse {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        approvals = approvals == null ? List.of() : List.copyOf(approvals);
        ccRecords = ccRecords == null ? List.of() : List.copyOf(ccRecords);
        workflowHistory = workflowHistory == null ? List.of() : List.copyOf(workflowHistory);
        formFields = formFields == null ? List.of() : List.copyOf(formFields);
    }

    /** 关联单据摘要 */
    public record LinkedDocument(
            @Schema(description = "关联单据 ID")
            Long id,
            @Schema(description = "关联单据编号")
            String docCode,
            @Schema(description = "关联单据项目名称")
            String projectName,
            @Schema(description = "合同编号")
            String contractNo) {}

    /** 附件项 */
    public record Attachment(
            @Schema(description = "附件 ID")
            Long id,
            @Schema(description = "文件名")
            String fileName,
            @Schema(description = "MIME 类型（如 application/pdf）")
            String contentType,
            @Schema(description = "文件大小（字节）")
            Long fileSize,
            @Schema(description = "上传时所在审批节点")
            String nodeName,
            @Schema(description = "上传人姓名")
            String uploader,
            @Schema(description = "上传时间")
            LocalDateTime createdAt) {}

    /** 审批记录项 */
    public record Approval(
            @Schema(description = "审批记录 ID")
            Long id,
            @Schema(description = "审批节点名称")
            String nodeName,
            @Schema(description = "审批人姓名")
            String approver,
            @Schema(description = "审批操作（枚举）")
            ApprovalActionEnum action,
            @Schema(description = "审批意见")
            String comment,
            @Schema(description = "操作时间")
            LocalDateTime createdAt) {}

    /** 抄送记录项 */
    public record Cc(
            @Schema(description = "抄送记录 ID")
            Long id,
            @Schema(description = "抄送对象姓名")
            String targetName,
            @Schema(description = "抄送来源（枚举：流程抄送/手动抄送等）")
            String source,
            @Schema(description = "抄送时间")
            LocalDateTime createdAt) {}
}
