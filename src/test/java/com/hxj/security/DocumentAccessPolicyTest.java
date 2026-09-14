package com.hxj.security;

import com.hxj.enums.BusinessTypeEnum;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysDepartment;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysDepartmentRepository;
import com.hxj.repository.SysPostRepository;
import com.hxj.repository.SysRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据范围策略测试（V12 收敛后的 5 种类型）：
 * ALL / OWN / DEPT / DEPT_AND_CHILD / CUSTOM，本人单据为基线权利。
 *
 * <p>部门树：中心 → 业务部、财务部（闭包表维护子树路径）。
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(DocumentAccessPolicy.class)
class DocumentAccessPolicyTest {

    @Autowired
    private OaDocumentRepository documentRepository;

    @Autowired
    private SysDepartmentRepository departmentRepository;

    @Autowired
    private SysPostRepository postRepository;

    @Autowired
    private SysDataScopeRepository dataScopeRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private DocumentAccessPolicy accessPolicy;

    private Long applicantAId;
    private String centerName = "中心";
    private String businessName = "业务部";
    private String financeName = "财务部";

    @BeforeEach
    void setUp() {
        createDepartment(centerName, null);
        createDepartment(businessName, centerName);
        createDepartment(financeName, centerName);

        SysUser applicantA = persistUser("user-a", businessName);
        SysUser applicantB = persistUser("user-b", businessName);
        SysUser applicantC = persistUser("user-c", financeName);
        applicantAId = applicantA.getId();

        persistDocument("BX202608290101", applicantA, BusinessTypeEnum.DAILY_PAYMENT);
        persistDocument("BX202608290102", applicantB, BusinessTypeEnum.DAILY_PAYMENT);
        persistDocument("FK202608290103", applicantC, BusinessTypeEnum.BUSINESS_PAYMENT);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void shouldLimitOwnDataScopeToCurrentApplicant() {
        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "user-a", businessName, List.of("测试角色"), List.of(DocumentAccessPolicy.OWN));

        assertThat(findVisibleCodes(currentUser)).containsExactly("BX202608290101");
    }

    @Test
    void shouldLimitDepartmentDataScopeToCurrentDepartment() {
        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "manager", businessName, List.of("测试角色"), List.of(DocumentAccessPolicy.DEPT));

        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "BX202608290102");
    }

    @Test
    void shouldExpandDepartmentAndChildScopeViaClosure() {
        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "center-head", centerName, List.of("测试角色"),
                List.of(DocumentAccessPolicy.DEPT_AND_CHILD));

        // 中心子树覆盖业务部与财务部，全部单据可见
        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "BX202608290102", "FK202608290103");
    }

    @Test
    void shouldLimitCustomScopeToConfiguredDepartments() {
        SysDataScope custom = dataScopeRepository.save(new SysDataScope("CUSTOM", "自定义部门集合"));
        SysRole role = new SysRole();
        role.setName("自定义范围角色");
        role.setDepartment(centerName);
        role.setPost("员工");
        role.setDataScope(custom);
        role.getScopeDepartments().add(departmentRepository.findByName(financeName).orElseThrow());
        roleRepository.saveAndFlush(role);

        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "custom-user", centerName, List.of("自定义范围角色"),
                List.of(DocumentAccessPolicy.CUSTOM));

        // 自定义集合只配了财务部：财务部单据 + 本人单据基线
        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "FK202608290103");
    }

    @Test
    void shouldAllowAllScopeToViewEverything() {
        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "admin", centerName, List.of("测试角色"), List.of(DocumentAccessPolicy.ALL));

        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "BX202608290102", "FK202608290103");
    }

    @Test
    void shouldFallbackToOwnBaselineWhenScopeUnknown() {
        AuthenticatedUserResponse currentUser = principal(
                applicantAId, "unknown", businessName, List.of("测试角色"), List.of("UNSUPPORTED_SCOPE"));

        // 未知范围收敛为本人单据基线
        assertThat(findVisibleCodes(currentUser)).containsExactly("BX202608290101");
    }

    @Test
    void shouldDenyAllWhenPrincipalMissing() {
        assertThat(findVisibleCodes(null)).isEmpty();
    }

    private List<String> findVisibleCodes(AuthenticatedUserResponse currentUser) {
        return documentRepository.findAll(accessPolicy.visibleTo(currentUser)).stream()
                .map(OaDocument::getDocCode)
                .sorted()
                .toList();
    }

    private AuthenticatedUserResponse principal(
            Long userId,
            String account,
            String department,
            List<String> roles,
            List<String> dataScopes) {
        return new AuthenticatedUserResponse(
                userId,
                account,
                account,
                department,
                "测试岗位",
                roles,
                List.of("VIEW_OWN_FORMS"),
                dataScopes);
    }

    /** 创建部门并维护闭包路径（自身 + 挂到父部门）。 */
    private SysDepartment createDepartment(String name, String parentName) {
        SysDepartment department = new SysDepartment();
        department.setName(name);
        if (parentName != null) {
            department.setParentId(departmentRepository.findByName(parentName).orElseThrow().getId());
        }
        SysDepartment saved = departmentRepository.saveAndFlush(department);
        departmentRepository.insertSelfPath(saved.getId());
        if (saved.getParentId() != null) {
            departmentRepository.attachUnderParent(saved.getId(), saved.getParentId());
        }
        return saved;
    }

    private SysUser persistUser(String account, String department) {
        SysUser user = new SysUser();
        user.setName(account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded-password");
        user.setDepartmentId(departmentRepository.findByName(department).orElseThrow().getId());
        user.setDepartment(department);
        user.setPostId(com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "员工").getId());
        user.setPost("员工");
        entityManager.persist(user);
        return user;
    }

    private void persistDocument(String code, SysUser applicant, BusinessTypeEnum businessType) {
        OaDocument document = new OaDocument();
        document.setDocCode(code);
        document.setBusinessType(businessType);
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setProjectName("测试事项");
        entityManager.persist(document);
    }
}
