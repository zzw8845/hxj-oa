package com.hxj.permission;

import com.hxj.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 员工、部门、岗位与角色管理接口（仅管理员）。 */
@Tag(name = "员工与角色管理", description = "员工账号管理、部门/岗位字典管理、角色新增/修改、部门角色架构查询（需 CONFIGURE_FLOW_PERMISSION 权限）")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('CONFIGURE_FLOW_PERMISSION')")
public class PermissionManagementController {

    private final EmployeeManagementService employeeService;
    private final RoleManagementService roleService;
    private final DepartmentManagementService departmentService;
    private final PostManagementService postService;

    public PermissionManagementController(
            EmployeeManagementService employeeService,
            RoleManagementService roleService,
            DepartmentManagementService departmentService,
            PostManagementService postService) {
        this.employeeService = employeeService;
        this.roleService = roleService;
        this.departmentService = departmentService;
        this.postService = postService;
    }

    @Operation(summary = "创建员工账号", description = "创建员工账号，账号密码必填，部门/岗位传字典ID")
    @PostMapping("/employees")
    public ApiResponse<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        return ApiResponse.success(employeeService.create(request));
    }

    @Operation(summary = "编辑员工", description = "编辑员工信息与在职/离职状态，部门/岗位传字典ID")
    @PutMapping("/employees/{id}")
    public ApiResponse<EmployeeResponse> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ApiResponse.success(employeeService.update(id, request));
    }

    @Operation(summary = "重置员工密码", description = "管理员为员工重置登录密码")
    @PostMapping("/employees/{id}/reset-password")
    public ApiResponse<EmployeeResponse> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(employeeService.resetPassword(id, request));
    }

    @Operation(summary = "员工列表", description = "查询员工，支持按部门/岗位/在职状态筛选")
    @GetMapping("/employees")
    public ApiResponse<List<EmployeeResponse>> employees(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long departmentId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long postId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) com.hxj.enums.UserStatusEnum status) {
        return ApiResponse.success(employeeService.list(departmentId, postId, status));
    }

    @Operation(summary = "部门树", description = "返回完整部门树（闭包表模型，支持任意层级）")
    @GetMapping("/departments")
    public ApiResponse<List<DepartmentViews.Department>> departmentTree() {
        return ApiResponse.success(departmentService.tree());
    }

    @Operation(summary = "新增部门", description = "新增部门（parentId 为空表示根部门，支持任意层级）")
    @PostMapping("/departments")
    public ApiResponse<DepartmentViews.Department> createDepartment(@Valid @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.success(departmentService.create(request));
    }

    @Operation(summary = "编辑部门", description = "改名/移动/调排序；编辑时 parentId 为空表示移动为根部门")
    @PutMapping("/departments/{id}")
    public ApiResponse<DepartmentViews.Department> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.success(departmentService.update(id, request));
    }

    @Operation(summary = "删除部门", description = "仅允许删除无下级且无员工的叶部门")
    @DeleteMapping("/departments/{id}")
    public ApiResponse<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "岗位列表", description = "查询全部岗位（按名称排序）")
    @GetMapping("/posts")
    public ApiResponse<List<PostViews.Post>> posts() {
        return ApiResponse.success(postService.list());
    }

    @Operation(summary = "新增岗位", description = "新增岗位字典项")
    @PostMapping("/posts")
    public ApiResponse<PostViews.Post> createPost(@Valid @RequestBody SavePostRequest request) {
        return ApiResponse.success(postService.create(request));
    }

    @Operation(summary = "编辑岗位", description = "修改岗位名称，员工展示快照同步更新")
    @PutMapping("/posts/{id}")
    public ApiResponse<PostViews.Post> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody SavePostRequest request) {
        return ApiResponse.success(postService.update(id, request));
    }

    @Operation(summary = "删除岗位", description = "被员工引用的岗位禁止删除")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.success(null);
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

    @Operation(summary = "删除角色", description = "被员工引用或被流程节点引用的角色禁止删除")
    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "部门角色架构", description = "按部门树形返回角色、岗位、权限")
    @GetMapping("/roles/tree")
    public ApiResponse<List<DepartmentRoleNodeResponse>> roleTree() {
        return ApiResponse.success(roleService.departmentTree());
    }

    @Operation(summary = "权限点字典", description = "只读权限点列表（角色编辑下拉数据源，不开放写）")
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionViews.PermissionPoint>> permissions() {
        return ApiResponse.success(roleService.listPermissions());
    }

    @Operation(summary = "数据范围字典", description = "只读数据范围列表（角色编辑下拉数据源，不开放写）")
    @GetMapping("/data-scopes")
    public ApiResponse<List<DataScopeViews.Scope>> dataScopes() {
        return ApiResponse.success(roleService.listDataScopes());
    }
}
