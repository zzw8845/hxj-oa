package com.hxj.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页查询请求的统一契约。
 *
 * <p><b>为什么是接口而不是抽象基类</b>：record 隐式 {@code final} 不能继承类，但可以实现接口。
 * 把分页参数转换逻辑放进接口默认方法，各分页查询请求 DTO 就能保持 record（不可变），
 * 不必为了复用 {@code page}/{@code size} 而退化成可变 class。
 *
 * <p>实现方需自行声明 {@code page} / {@code size} 组件（Spring MVC 按参数名绑定，
 * 因此不能藏在父类里，否则前端传参要写成 {@code page.page=1}，破坏 API 契约），
 * 并在紧凑构造器中归一化默认值。
 */
public interface PageableRequest {

    /** 默认页码 */
    int DEFAULT_PAGE = 1;

    /** 默认每页条数 */
    int DEFAULT_SIZE = 10;

    /** 每页最大条数 */
    int MAX_SIZE = 1000;

    /** 不分页标志：size 取该值时返回全部数据 */
    int SIZE_NONE = -1;

    /** 页码，从 1 开始 */
    Integer page();

    /** 每页条数，{@link #SIZE_NONE} 表示不分页 */
    Integer size();

    /** 转为 Spring Data 分页对象（PageRequest 的页码从 0 开始，此处减 1）。 */
    default Pageable toPageable(Sort sort) {
        if (SIZE_NONE == size()) {
            return Pageable.unpaged();
        }
        return PageRequest.of(page() - 1, size(), sort);
    }
}
