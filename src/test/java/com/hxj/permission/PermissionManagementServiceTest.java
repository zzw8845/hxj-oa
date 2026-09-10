package com.hxj.permission;

import com.hxj.config.SecurityBeansConfig;
import com.hxj.entity.FlowConfig;
import com.hxj.entity.FlowNodeConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.UserStatusEnum;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysPermissionRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({EmployeeManagementService.class, RoleManagementService.class,
        DepartmentManagementService.class, PostManagementService.class,
        SecurityBeansConfig.class})
class PermissionManagementServiceTest {

    @Autowired private EmployeeManagementService employeeService;
    @Autowired private RoleManagementService roleService;
    @Autowired private DepartmentManagementService departmentService;
    @Autowired private PostManagementService postService;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private SysPermissionRepository permissionRepository;
    @Autowired private SysDepartmentRepository departmentRepository;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private SysDataScope scope;
    private SysPermission permission;
    private SysRole role;
    private Long departmentId;
    private Long postId;

    @BeforeEach
    void setUp() {
        scope = dataScopeRepository.save(new SysDataScope("OWN", "仅本人单据"));
        permission = permissionRepository.save(new SysPermission("VIEW_OWN_FORMS", "查看本人表单"));
        role = new SysRole();
        role.setName("普通员工");
        role.setDepartment("业务部");
        role.setPost("员工");
        role.setDataScope(scope);
        role.addPermission(permission);
        roleRepository.save(role);

        departmentId = departmentService.create(new SaveDepartmentRequest("业务部", null, 1)).id();
        postId = postService.create(new SavePostRequest("专员")).id();
    }

    @Test
    void shouldCreateAndEditEmployeeAccount() {
        EmployeeResponse created = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));

        SysUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(passwordEncoder.matches("password", persisted.getPassword())).isTrue();
        assertThat(persisted.getDepartmentId()).isEqualTo(departmentId);
        assertThat(persisted.getDepartment()).isEqualTo("业务部");
        assertThat(persisted.getPostId()).isEqualTo(postId);
        assertThat(persisted.getPost()).isEqualTo("专员");
        assertThat(persisted.getRoles()).extracting(SysRole::getName).containsExactly("普通员工");
        assertThat(persisted.hasPermission("VIEW_OWN_FORMS")).isTrue();

        Long newDepartmentId = departmentService.create(new SaveDepartmentRequest("财务部", null, 2)).id();
        EmployeeResponse updated = employeeService.update(created.id(), new UpdateEmployeeRequest(
                "张三（离职）", "HXJ100", newDepartmentId, postId, UserStatusEnum.RESIGNED,
                null, List.of(role.getName())));
        assertThat(updated.status()).isEqualTo(UserStatusEnum.RESIGNED);
        assertThat(updated.departmentId()).isEqualTo(newDepartmentId);
        assertThat(userRepository.findById(created.id()).orElseThrow().isLoginEnabled()).isFalse();
    }

    @Test
    void shouldRejectUnknownDepartmentOrPost() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", 999L, postId, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门不存在");

        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, 999L, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位不存在");
    }

    @Test
    void shouldResetEmployeePassword() {
        EmployeeResponse created = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));

        employeeService.resetPassword(created.id(), new ResetPasswordRequest("new-pass"));

        SysUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(passwordEncoder.matches("new-pass", persisted.getPassword())).isTrue();
    }

    @Test
    void shouldRejectIncompleteOrDuplicateEmployeeAccount() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "", "", departmentId, postId, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请完整填写员工、账号和密码信息");

        employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "李四", "HXJ101", "zhangsan", "password", departmentId, postId, List.of(role.getName()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录账号已存在");
    }

    @Test
    void shouldCreateUpdateRoleAndReturnDepartmentTree() {
        SysPermission approve = permissionRepository.save(
                new SysPermission("DEPARTMENT_HEAD_APPROVAL", "部门负责人审批"));

        RoleResponse created = roleService.create(new SaveRoleRequest(
                "部门负责人", "业务部", "经理", scope.getCode(), null, List.of(permission.getCode(), approve.getCode())));
        assertThat(created.permissions()).containsExactlyInAnyOrder("VIEW_OWN_FORMS", "DEPARTMENT_HEAD_APPROVAL");

        RoleResponse updated = roleService.update(created.id(), new SaveRoleRequest(
                "部门经理", "业务部", "经理", scope.getCode(), null, List.of(approve.getCode())));
        assertThat(updated.name()).isEqualTo("部门经理");
        assertThat(updated.permissions()).containsExactly("DEPARTMENT_HEAD_APPROVAL");

        List<DepartmentRoleNodeResponse> tree = roleService.departmentTree();
        assertThat(tree).extracting(DepartmentRoleNodeResponse::department).contains("业务部");
        DepartmentRoleNodeResponse businessDepartment = tree.stream()
                .filter(node -> node.department().equals("业务部")).findFirst().orElseThrow();
        assertThat(businessDepartment.roles()).extracting(RoleTreeNodeResponse::name)
                .contains("普通员工", "部门经理");
    }

    @Test
    void shouldGuardDepartmentAndPostDictionaryOperations() {
        // 部门重名
        assertThatThrownBy(() -> departmentService.create(new SaveDepartmentRequest("业务部", null, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门名称已存在");
        // 有员工的部门禁止删除
        EmployeeResponse employee = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));
        assertThatThrownBy(() -> departmentService.delete(departmentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("部门下存在员工，无法删除");
        // 有员工的岗位禁止删除、岗位重名
        assertThatThrownBy(() -> postService.delete(postId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位下存在员工，无法删除");
        assertThatThrownBy(() -> postService.create(new SavePostRequest("专员")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位名称已存在");

        // 部门改名同步员工快照
        departmentService.update(departmentId, new SaveDepartmentRequest("销售部", null, 1));
        assertThat(userRepository.findById(employee.id()).orElseThrow().getDepartment()).isEqualTo("销售部");

        // 无引用后可删除
        employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ100", departmentId, postId, UserStatusEnum.RESIGNED,
                null, List.of(role.getName())));
        userRepository.delete(userRepository.findById(employee.id()).orElseThrow());
        departmentService.delete(departmentId);
        assertThat(departmentRepository.existsById(departmentId)).isFalse();
    }

    @Test
    void shouldGuardRoleDeletion() {
        // 有员工引用的角色禁止删除
        EmployeeResponse employee = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));
        assertThatThrownBy(() -> roleService.delete(role.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色下存在员工，无法删除");

        // 解除员工引用（转入过渡角色）后，被流程节点引用（含组合角色串的段）仍禁止删除
        RoleResponse bridgeRole = roleService.create(new SaveRoleRequest(
                "过渡角色", "业务部", "助理", scope.getCode(), null, List.of(permission.getCode())));
        employeeService.update(employee.id(), new UpdateEmployeeRequest(
                "张三", "HXJ100", departmentId, postId, UserStatusEnum.RESIGNED,
                null, List.of(bridgeRole.name())));
        userRepository.flush();
        FlowConfig flowConfig = new FlowConfig();
        flowConfig.setType("测试流程");
        flowConfig.setCategory(FlowCategoryEnum.DAILY);
        FlowNodeConfig node = new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL);
        node.setAssigneeRole("二级部门负责人/" + role.getName());
        flowConfig.addNode(node);
        flowConfigRepository.saveAndFlush(flowConfig);
        assertThatThrownBy(() -> roleService.delete(role.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("角色被流程节点引用，无法删除");

        // 名称部分重叠但不同段的角色不受误伤，无引用角色可正常删除
        RoleResponse freeRole = roleService.create(new SaveRoleRequest(
                "二级部门负责人助理", "业务部", "助理", scope.getCode(), null, List.of(permission.getCode())));
        roleService.delete(freeRole.id());
        assertThat(roleRepository.existsById(freeRole.id())).isFalse();
    }

    @Test
    void shouldListPermissionAndDataScopeDictionaries() {
        List<PermissionViews.PermissionPoint> permissions = roleService.listPermissions();
        assertThat(permissions).extracting(PermissionViews.PermissionPoint::code).contains("VIEW_OWN_FORMS");
        assertThat(permissions).allSatisfy(point -> assertThat(point.name()).isNotBlank());

        List<DataScopeViews.Scope> scopes = roleService.listDataScopes();
        assertThat(scopes).extracting(DataScopeViews.Scope::code).contains("OWN");
    }

    @Test
    void shouldFilterEmployeeListByDictionaryAndStatus() {
        employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", departmentId, postId, List.of(role.getName())));

        assertThat(employeeService.list(departmentId, postId, null)).hasSize(1);
        assertThat(employeeService.list(999L, postId, null)).isEmpty();
        assertThat(employeeService.list(null, 999L, null)).isEmpty();
        assertThat(employeeService.list(null, null, UserStatusEnum.RESIGNED)).isEmpty();
        assertThat(employeeService.list(null, null, UserStatusEnum.ACTIVE)).hasSize(1);
        assertThat(employeeService.list(null, null, null)).hasSize(1);
    }
}
