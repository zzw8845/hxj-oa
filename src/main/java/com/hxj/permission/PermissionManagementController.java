package com.hxj.permission;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 员工与角色管理接口（仅管理员）。 */
@Tag(name = "员工与角色管理", description = "员工账号创建/编辑、角色新增/修改、部门角色架构查询（需 CONFIGURE_FLOW_PERMISSION 权限）")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class PermissionManagementController {

    private final EmployeeManagementService employeeService;
    private final RoleManagementService roleService;

    public PermissionManagementController(
            EmployeeManagementService employeeService,
            RoleManagementService roleService) {
        this.employeeService = employeeService;
        this.roleService = roleService;
    }

    @Operation(summary = "创建员工账号", description = "创建员工账号，账号密码必填")
    @PostMapping("/employees")
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return ApiResponse.success(employeeService.create(request));
    }

    @Operation(summary = "编辑员工", description = "编辑员工信息与在职/离职状态")
    @PutMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.success(employeeService.update(id, request));
    }

    @Operation(summary = "员工列表", description = "查询全部员工")
    @GetMapping("/employees")
    public ApiResponse<List<EmployeeResponse>> employees() {
        return ApiResponse.success(employeeService.list());
    }

    @Operation(summary = "新增角色", description = "新增角色，含权限点与数据范围")
    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody SaveRoleRequest request) {
        return ApiResponse.success(roleService.create(request));
    }

    @Operation(summary = "修改角色", description = "修改角色，含权限点与数据范围")
    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody SaveRoleRequest request) {
        return ApiResponse.success(roleService.update(id, request));
    }

    @Operation(summary = "角色列表", description = "查询全部角色")
    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.success(roleService.list());
    }

    @Operation(summary = "部门角色架构", description = "按部门树形返回角色、岗位、权限")
    @GetMapping("/roles/tree")
    public ApiResponse<List<DepartmentRoleNode>> roleTree() {
        return ApiResponse.success(roleService.departmentTree());
    }
}
