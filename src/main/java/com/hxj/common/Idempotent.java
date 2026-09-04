package com.hxj.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解：标注在写接口上，配合请求头 {@code Idempotency-Key} 使用。
 * 相同 key 的重复请求直接返回首次结果，防止网络重试导致重复提交/重复审批。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}