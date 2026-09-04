package com.hxj.repository;

import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.entity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rbac;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class RbacEntityRepositoryTest {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private SysDataScopeRepository dataScopeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistUserRolePermissionsAndDataScopeAssociations() {
        SysDataScope ownScope = dataScopeRepository.save(
                new SysDataScope("OWN_DOCUMENTS", "本人单据"));
        SysPermission viewOwn = permissionRepository.save(
                new SysPermission("VIEW_OWN_FORMS", "查看本人表单"));
        SysPermission submitAll = permissionRepository.save(
                new SysPermission("SUBMIT_ALL_FORMS", "提交全部表单"));

        SysRole employeeRole = new SysRole();
        employeeRole.setName("普通员工");
        employeeRole.setDepartment("业务支持中心");
        employeeRole.setPost("员工");
        employeeRole.setDataScope(ownScope);
        employeeRole.addPermission(viewOwn);
        employeeRole.addPermission(submitAll);
        roleRepository.save(employeeRole);

        SysUser user = new SysUser();
        user.setName("测试员工");
        user.setJobNo("E0001");
        user.setAccount("employee01");
        user.setPassword("encoded-password");
        user.setDepartment("业务支持中心");
        user.setPost("员工");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(employeeRole);
        userRepository.saveAndFlush(user);

        entityManager.clear();

        SysUser persisted = userRepository.findByAccount("employee01").orElseThrow();
        assertThat(persisted.getRole().getName()).isEqualTo("普通员工");
        assertThat(persisted.getRole().getDataScope().getCode()).isEqualTo("OWN_DOCUMENTS");
        assertThat(persisted.getRole().getPermissions())
                .extracting(SysPermission::getCode)
                .containsExactlyInAnyOrder("VIEW_OWN_FORMS", "SUBMIT_ALL_FORMS");
        assertThat(persisted.hasPermission("VIEW_OWN_FORMS")).isTrue();
        assertThat(persisted.hasPermission("APPROVE_ALL_NODES")).isFalse();

        SysRole persistedRole = roleRepository.findByName("普通员工").orElseThrow();
        assertThat(persistedRole.getMembers())
                .extracting(SysUser::getAccount)
                .containsExactly("employee01");
    }

    @Test
    void shouldExposeInactiveEmploymentStatusAsNotLoginEnabled() {
        SysUser resignedUser = new SysUser();
        resignedUser.setName("离职员工");
        resignedUser.setJobNo("E0002");
        resignedUser.setAccount("resigned01");
        resignedUser.setPassword("encoded-password");
        resignedUser.setStatus(UserStatus.RESIGNED);

        assertThat(resignedUser.isLoginEnabled()).isFalse();
    }
}