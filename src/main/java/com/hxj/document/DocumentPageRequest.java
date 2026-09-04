package com.hxj.document;

import com.hxj.common.PageableRequest;
import com.hxj.entity.BusinessType;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.DocumentType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

/**
 * 单据分页查询请求：组合筛选条件 + 分页参数。
 *
 * <p>分页参数不复用基类，而是直接声明为 record 组件并由 {@link PageableRequest}
 * 提供 {@code toPageable}：既保持不可变，又不改变前端扁平传参 {@code page=1&size=10}。
 */
public record DocumentPageRequest(
        @Schema(description = "关键字，模糊匹配单据编号、项目名称、申请人姓名")
        String keyword,
        @Schema(description = "单据状态（枚举：DRAFT/PENDING/APPROVING/APPROVED/REJECTED/SUPPLEMENT_REQUIRED）")
        DocumentStatus status,
        @Schema(description = "业务类型（枚举，如 DAILY_PAYMENT/BUSINESS_PAYMENT/SEAL_APPLICATION）")
        BusinessType businessType,
        @Schema(description = "单据类型（枚举，如 NORMAL/CONTRACT/SEAL）")
        DocumentType documentType,
        @Schema(description = "申请人用户 ID")
        Long applicantId,
        @Schema(description = "页码，从 1 开始，默认 1")
        @Min(value = 1, message = "页码最小值为 1")
        Integer page,
        @Schema(description = "每页条数，默认 10、最大 1000；传 -1 表示不分页返回全部")
        Integer size) implements PageableRequest {

    /**
     * 紧凑构造器：仅对「未传」的分页参数补默认值。
     *
     * <p>非法值不在此处静默纠正——否则调用方传入 {@code size=0} 会被悄悄变成 10，
     * 既掩盖了错误的调用，也让行为无法预测。非法值一律交由 Bean Validation 拒绝。
     */
    public DocumentPageRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    /**
     * 每页条数合法性：-1（不分页）或 1~1000。
     *
     * <p>仅靠 {@code @Min} 无法表达「-1 或 1~1000」这种非连续区间：
     * 用 {@code @Min(-1)} 会放过 0，而 0 会让 {@code PageRequest.of} 抛异常。
     */
    @JsonIgnore
    @AssertTrue(message = "每页条数必须为 -1（不分页）或 1~1000 之间的整数")
    public boolean isSizeValid() {
        return SIZE_NONE == size || (size >= 1 && size <= MAX_SIZE);
    }

    /** 转换为查询条件 DTO（分页参数不参与筛选）。 */
    public DocumentSearchCriteria toCriteria() {
        return new DocumentSearchCriteria(keyword, status, businessType, documentType, applicantId);
    }
}
