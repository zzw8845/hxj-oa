package com.hxj.archive;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hxj.common.PageableRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * 归档台账分页查询请求：组合筛选条件 + 分页参数。
 *
 * <p>分页参数不复用基类，而是直接声明为 record 组件并由 {@link PageableRequest}
 * 提供 {@code toPageable}：既保持不可变，又不改变前端扁平传参 {@code page=1&size=10}。
 */
public record ArchiveLedgerPageRequest(
        @Schema(description = "申请人姓名，模糊匹配")
        String applicant,
        @Schema(description = "申请部门，模糊匹配")
        String department,
        @Schema(description = "单据编号，模糊匹配")
        String docCode,
        @Schema(description = "归档时间起（含），格式 yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime archivedFrom,
        @Schema(description = "归档时间止（含），格式 yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime archivedTo,
        @Schema(description = "页码，从 1 开始，默认 1")
        @Min(value = 1, message = "页码最小值为 1")
        Integer page,
        @Schema(description = "每页条数，默认 10、最大 1000；传 -1 表示不分页返回全部")
        Integer size) implements PageableRequest {

    /**
     * 紧凑构造器：仅对「未传」的分页参数补默认值，非法值交由 Bean Validation 拒绝。
     */
    public ArchiveLedgerPageRequest {
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    /** 每页条数合法性：-1（不分页）或 1~1000；仅靠 @Min 无法表达该非连续区间。 */
    @JsonIgnore
    @AssertTrue(message = "每页条数必须为 -1（不分页）或 1~1000 之间的整数")
    public boolean isSizeValid() {
        return SIZE_NONE == size || (size >= 1 && size <= MAX_SIZE);
    }

    /** 转换为查询条件 DTO（分页参数不参与筛选）。 */
    public ArchiveLedgerQuery toQuery() {
        return new ArchiveLedgerQuery(applicant, department, docCode, archivedFrom, archivedTo);
    }
}
