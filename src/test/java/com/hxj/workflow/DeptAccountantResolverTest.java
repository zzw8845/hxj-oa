package com.hxj.workflow;

import com.hxj.entity.SysUser;
import com.hxj.repository.SysUserServiceDeptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 「会计（按部门）」路由解析器的裁决规则测试。 */
class DeptAccountantResolverTest {

    private final SysUserServiceDeptRepository serviceDeptRepository = Mockito.mock(SysUserServiceDeptRepository.class);
    private final DeptAccountantResolver resolver = new DeptAccountantResolver(serviceDeptRepository);

    @Test
    void resolvesFirstServingAccountantAccount() {
        Mockito.when(serviceDeptRepository.findServingAccountantAccounts(20L, "核算会计"))
                .thenReturn(List.of("wangyang", "fengxunyi"));
        SysUser applicant = new SysUser();
        applicant.setDepartmentId(20L);

        assertThat(resolver.resolve(applicant)).isEqualTo("wangyang");
    }

    @Test
    void returnsEmptyWhenNoMappingForDepartment() {
        Mockito.when(serviceDeptRepository.findServingAccountantAccounts(99L, "核算会计"))
                .thenReturn(List.of());
        SysUser applicant = new SysUser();
        applicant.setDepartmentId(99L);

        assertThat(resolver.resolve(applicant)).isEqualTo("");
    }

    @Test
    void returnsEmptyWhenApplicantHasNoDepartment() {
        SysUser applicant = new SysUser();

        assertThat(resolver.resolve(applicant)).isEqualTo("");
    }
}
