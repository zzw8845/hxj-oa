package com.hxj.document;

import com.hxj.entity.BusinessType;
import com.hxj.entity.Company;
import com.hxj.entity.SealType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交单据请求 DTO。
 *
 * <p>用于 {@code POST /api/documents} 接口，封装用户提交单据时填写的全部信息。
 * 包含业务基本信息、用印信息、关联单据、抄送人等。
 */
public record SubmitDocumentRequest(
        @Schema(description = "业务类型（枚举）")
        @NotNull BusinessType businessType,
        @Schema(description = "项目名称（必填）")
        @NotBlank String projectName,
        @Schema(description = "所属公司（枚举）")
        Company company,
        @Schema(description = "金额（元，精确到分）")
        BigDecimal amount,
        @Schema(description = "发票摘要/事由简述")
        String invoiceSummary,
        @Schema(description = "申请事由/详细说明")
        String reason,
        @Schema(description = "是否需要后补材料")
        boolean needPostMaterial,
        @Schema(description = "合同编号（涉及合同时填写）")
        String contractNo,
        @Schema(description = "关联单据 ID（如续签、变更时关联原单据）")
        Long linkedDocumentId,
        @Schema(description = "是否涉及资金往来")
        boolean involvesFunds,
        @Schema(description = "是否需要行政审核")
        boolean requiresAdminReview,
        @Schema(description = "业务模式（如：线上、线下、混合）")
        String businessMode,
        @Schema(description = "用印项目")
        String sealProject,
        @Schema(description = "用印部门")
        String sealDepartment,
        @Schema(description = "用印时间")
        LocalDateTime sealTime,
        @Schema(description = "用印文件名")
        String sealFileName,
        @Schema(description = "印章类型（枚举）")
        SealType sealType,
        @Schema(description = "用印事由")
        String sealReason,
        @Schema(description = "抄送人用户 ID 列表")
        List<Long> ccUserIds) {

    /**
     * 紧凑构造器：集合组件做防御性拷贝。
     *
     * <p>record 只是"浅不可变"——组件引用不可换，但引用对象本身可能被外部持有者修改。
     * 此处统一拷贝为不可变列表，null 归一化为不可变空列表，下游可直接遍历无需判空。
     */
    public SubmitDocumentRequest {
        ccUserIds = ccUserIds == null ? List.of() : List.copyOf(ccUserIds);
    }
}
