package com.hxj.workflow;

import com.hxj.entity.SysUser;
import com.hxj.repository.SysUserRepository;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「角色 + 按发起人部门」范围解析器的裁决规则测试。
 *
 * <p>该解析器是钉钉同构的关键：核算会计、部门HR 等一切"按部门的业务角色"共用它，
 * 不新增审批人类型——本测试覆盖其三级优先级与空值兜底。
 */
class DeptScopedRoleResolverTest {

    private final SysUserServiceDeptRepository serviceDeptRepository =
            Mockito.mock(SysUserServiceDeptRepository.class);
    private final SysUserRepository userRepository = Mockito.mock(SysUserRepository.class);
    private final DeptScopedRoleResolver resolver =
            new DeptScopedRoleResolver(serviceDeptRepository, userRepository);

    private static final List<Long> ACCOUNTANT_ROLE = List.of(33L);

    @Test
    void prefersExplicitServiceMappingOverOwnDepartment() {
        // 显式分工优先：成员坐席财务条线但服务业务部门（共享服务中心模式）
        Mockito.when(serviceDeptRepository.findServedAccountsByRoleIds(20L, ACCOUNTANT_ROLE))
                .thenReturn(List.of("fengxunyi", "wangyang"));
        SysUser applicant = new SysUser();
        applicant.setDepartmentId(20L);

        assertThat(resolver.resolve(applicant, ACCOUNTANT_ROLE))
                .containsExactly("fengxunyi", "wangyang");
        assertThat(resolver.resolveFirst(applicant, ACCOUNTANT_ROLE)).isEqualTo("fengxunyi");
        // 有显式分工时不再回落到所属部门查询
        Mockito.verify(userRepository, Mockito.never())
                .findAccountsByRoleIdsAndDepartmentId(Mockito.any(), Mockito.any());
    }

    @Test
    void fallsBackToMemberOwnDepartmentWhenNoServiceMapping() {
        // 无显式分工：角色成员本身归属该部门（钉钉原语义，如各部门HR）
        Mockito.when(serviceDeptRepository.findServedAccountsByRoleIds(28L, List.of(40L)))
                .thenReturn(List.of());
        Mockito.when(userRepository.findAccountsByRoleIdsAndDepartmentId(List.of(40L), 28L))
                .thenReturn(List.of("dept-hr"));
        SysUser applicant = new SysUser();
        applicant.setDepartmentId(28L);

        assertThat(resolver.resolve(applicant, List.of(40L))).containsExactly("dept-hr");
    }

    @Test
    void returnsEmptyWhenNeitherMappingNorOwnDepartmentMatches() {
        Mockito.when(serviceDeptRepository.findServedAccountsByRoleIds(99L, ACCOUNTANT_ROLE))
                .thenReturn(List.of());
        Mockito.when(userRepository.findAccountsByRoleIdsAndDepartmentId(ACCOUNTANT_ROLE, 99L))
                .thenReturn(List.of());
        SysUser applicant = new SysUser();
        applicant.setDepartmentId(99L);

        assertThat(resolver.resolve(applicant, ACCOUNTANT_ROLE)).isEmpty();
        assertThat(resolver.resolveFirst(applicant, ACCOUNTANT_ROLE)).isEmpty();
    }

    @Test
    void returnsEmptyWhenApplicantHasNoDepartmentOrNoRole() {
        SysUser noDepartment = new SysUser();
        assertThat(resolver.resolve(noDepartment, ACCOUNTANT_ROLE)).isEmpty();

        SysUser applicant = new SysUser();
        applicant.setDepartmentId(20L);
        assertThat(resolver.resolve(applicant, List.of())).isEmpty();
        assertThat(resolver.resolve(applicant, null)).isEmpty();
    }
}
