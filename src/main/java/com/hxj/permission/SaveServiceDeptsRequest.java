package com.hxj.permission;

import java.util.List;

/** 设置员工职能服务分工的请求体。 */
public record SaveServiceDeptsRequest(List<Long> departmentIds) {
}
