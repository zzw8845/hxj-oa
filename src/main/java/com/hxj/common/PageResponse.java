package com.hxj.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页响应。
 *
 * @param <T> 数据泛型
 */
public record PageResponse<T>(
        @Schema(description = "当前页数据列表") List<T> content,
        @Schema(description = "当前页码，从 1 开始") int page,
        @Schema(description = "每页条数") int size,
        @Schema(description = "总记录数") long totalElements,
        @Schema(description = "总页数") int totalPages) {

    /**
     * 紧凑构造器：数据列表防御性拷贝为不可变列表，null 归一化为不可变空列表。
     *
     * <p>分页内容常直接来自 JPA {@code Page#getContent()}，不拷贝会让调用方
     * 拿到持久化上下文持有的可变集合。
     */
    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}