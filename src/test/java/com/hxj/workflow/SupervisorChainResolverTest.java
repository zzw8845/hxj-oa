package com.hxj.workflow;

import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysUser;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysPostRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.support.DictionaryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主管链解析器测试（钉钉模式）：
 * 人工直属主管优先 → 本部门负责人 → 沿部门闭包自近及远找负责人，防环、上限 10 级。
 *
 * <p>部门树：公司 → 中心 → 部门；闭包表路径经仓库方法维护（与生产同路径）。
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(SupervisorChainResolver.class)
class SupervisorChainResolverTest {

    @Autowired private SysUserRepository userRepository;
    @Autowired private SysDepartmentRepository departmentRepository;
    @Autowired private SysPostRepository postRepository;
    @Autowired private SupervisorChainResolver resolver;

    private SysUser companyLeader;
    private SysUser centerHead;
    private SysUser deptHead;
    private SysUser employee;
    private SysUser employeeWithManager;

    @BeforeEach
    void setUp() {
        // 先建部门树（saveUser 复用"部门"），再建用户，最后设负责人
        Long companyId = createDepartment("公司", null);
        Long centerId = createDepartment("中心", companyId);
        Long deptId = createDepartment("部门", centerId);

        companyLeader = saveUser("company-leader");
        centerHead = saveUser("center-head");
        deptHead = saveUser("dept-head");
        employee = saveUser("employee");

        departmentRepository.findById(companyId).ifPresent(d -> d.setLeaderUserId(companyLeader.getId()));
        departmentRepository.findById(centerId).ifPresent(d -> d.setLeaderUserId(centerHead.getId()));
        departmentRepository.findById(deptId).ifPresent(d -> d.setLeaderUserId(deptHead.getId()));
        departmentRepository.findAll().forEach(departmentRepository::save);

        // 部门负责人自己也走同一规则：无人工主管 → 本部门负责人是本人 → 跳过 → 沿祖先找中心负责人
        employeeWithManager = saveUser("employee-m");
        employeeWithManager.setManagerId(deptHead.getId());
        userRepository.save(employeeWithManager);
    }

    @Test
    void shouldPreferExplicitManagerOverDepartmentLeader() {
        // 人工指定的直属主管优先于部门负责人（保留虚线汇报等现实灵活性）
        assertThat(resolver.resolveChain(employeeWithManager))
                .containsExactly("dept-head", "center-head", "company-leader");
    }

    @Test
    void shouldFallBackToDepartmentLeaderWhenNoManager() {
        assertThat(resolver.resolveChain(employee))
                .containsExactly("dept-head", "center-head", "company-leader");
    }

    @Test
    void shouldWalkUpAncestorDepartmentsWhenDirectDeptHasNoLeader() {
        // 子部门直属的父部门未设负责人：沿闭包跨级兜底到中心负责人
        Long subDeptId = createDepartment("子部门", findByName("部门"));
        departmentRepository.findById(findByName("部门")).ifPresent(d -> {
            d.setLeaderUserId(null);
            departmentRepository.save(d);
        });
        SysUser orphan = saveUser("orphan");
        orphan.setDepartmentId(subDeptId);
        userRepository.save(orphan);

        assertThat(resolver.resolveFirstAccount(orphan)).isEqualTo("center-head");
        assertThat(resolver.resolveChain(orphan))
                .containsExactly("center-head", "company-leader");
    }

    @Test
    void shouldReturnEmptyChainWhenNoSourceResolvable() {
        // 顶层公司也无负责人：孤悬用户解析为空链（由提交校验前置拦截）
        departmentRepository.findById(findByName("公司")).ifPresent(d -> {
            d.setLeaderUserId(null);
            departmentRepository.save(d);
        });
        SysUser topUser = saveUser("top-user");
        topUser.setDepartmentId(findByName("公司"));
        userRepository.save(topUser);

        assertThat(resolver.resolveChain(topUser)).isEmpty();
    }

    @Test
    void shouldNotLoopOnSelfReferencingManager() {
        // 脏数据：manager 指向自己——不产生"自己审批自己"的链
        employee.setManagerId(employee.getId());
        userRepository.save(employee);

        // 人工主管是本人 → 跳过 → 兜底到部门负责人链，而非死循环或空链
        assertThat(resolver.resolveChain(employee))
                .containsExactly("dept-head", "center-head", "company-leader");
    }

    private Long findByName(String name) {
        return departmentRepository.findByName(name).orElseThrow().getId();
    }

    private Long createDepartment(String name, Long parentId) {
        SysDepartment department = new SysDepartment();
        department.setName(name);
        department.setParentId(parentId);
        SysDepartment saved = departmentRepository.saveAndFlush(department);
        departmentRepository.insertSelfPath(saved.getId());
        if (parentId != null) {
            departmentRepository.attachUnderParent(saved.getId(), parentId);
        }
        return saved.getId();
    }

    private SysUser saveUser(String account) {
        SysUser user = new SysUser();
        user.setName(account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded");
        DictionaryTestSupport.applyDictionary(user,
                DictionaryTestSupport.ensureDepartment(departmentRepository, "部门"),
                DictionaryTestSupport.ensurePost(postRepository, "员工"));
        return userRepository.save(user);
    }
}
