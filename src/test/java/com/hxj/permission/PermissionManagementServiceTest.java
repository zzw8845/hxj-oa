package com.hxj.permission;

import com.hxj.config.SecurityBeansConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.entity.UserStatus;
import com.hxj.exception.BusinessException;
import com.hxj.repository.SysDataScopeRepository;
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
@Import({EmployeeManagementService.class, RoleManagementService.class, SecurityBeansConfig.class})
class PermissionManagementServiceTest {

    @Autowired private EmployeeManagementService employeeService;
    @Autowired private RoleManagementService roleService;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private SysPermissionRepository permissionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private SysDataScope scope;
    private SysPermission permission;
    private SysRole role;

    @BeforeEach
    void setUp() {
        scope = dataScopeRepository.save(new SysDataScope("OWN_DOCUMENTS", "本人单据"));
        permission = permissionRepository.save(new SysPermission("VIEW_OWN_FORMS", "查看本人表单"));
        role = new SysRole();
        role.setName("普通员工");
        role.setDepartment("业务部");
        role.setPost("员工");
        role.setDataScope(scope);
        role.addPermission(permission);
        roleRepository.save(role);
    }

    @Test
    void shouldCreateAndEditEmployeeAccount() {
        EmployeeResponse created = employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", "业务部", "专员", List.of(role.getId())));

        SysUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(passwordEncoder.matches("password", persisted.getPassword())).isTrue();
        assertThat(persisted.getRoles()).extracting(SysRole::getName).containsExactly("普通员工");
        assertThat(persisted.hasPermission("VIEW_OWN_FORMS")).isTrue();

        EmployeeResponse updated = employeeService.update(created.id(), new UpdateEmployeeRequest(
                "张三（离职）", "HXJ100", "业务部", "专员", UserStatus.RESIGNED,
                null, List.of(role.getId())));
        assertThat(updated.status()).isEqualTo(UserStatus.RESIGNED);
        assertThat(userRepository.findById(created.id()).orElseThrow().isLoginEnabled()).isFalse();
    }

    @Test
    void shouldRejectIncompleteOrDuplicateEmployeeAccount() {
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "", "", "业务部", "专员", List.of(role.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请完整填写员工、账号和密码信息");

        employeeService.create(new CreateEmployeeRequest(
                "张三", "HXJ100", "zhangsan", "password", "业务部", "专员", List.of(role.getId())));
        assertThatThrownBy(() -> employeeService.create(new CreateEmployeeRequest(
                "李四", "HXJ101", "zhangsan", "password", "业务部", "专员", List.of(role.getId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录账号已存在");
    }

    @Test
    void shouldCreateUpdateRoleAndReturnDepartmentTree() {
        SysPermission approve = permissionRepository.save(
                new SysPermission("DEPARTMENT_HEAD_APPROVAL", "部门负责人审批"));

        RoleResponse created = roleService.create(new SaveRoleRequest(
                "部门负责人", "业务部", "经理", scope.getId(), List.of(permission.getId(), approve.getId())));
        assertThat(created.permissions()).containsExactlyInAnyOrder("VIEW_OWN_FORMS", "DEPARTMENT_HEAD_APPROVAL");

        RoleResponse updated = roleService.update(created.id(), new SaveRoleRequest(
                "部门经理", "业务部", "经理", scope.getId(), List.of(approve.getId())));
        assertThat(updated.name()).isEqualTo("部门经理");
        assertThat(updated.permissions()).containsExactly("DEPARTMENT_HEAD_APPROVAL");

        List<DepartmentRoleNode> tree = roleService.departmentTree();
        assertThat(tree).extracting(DepartmentRoleNode::department).contains("业务部");
        DepartmentRoleNode businessDepartment = tree.stream()
                .filter(node -> node.department().equals("业务部")).findFirst().orElseThrow();
        assertThat(businessDepartment.roles()).extracting(RoleTreeNode::name)
                .contains("普通员工", "部门经理");
    }
}