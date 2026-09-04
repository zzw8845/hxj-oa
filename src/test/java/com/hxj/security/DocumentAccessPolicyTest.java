package com.hxj.security;

import com.hxj.entity.BusinessType;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SysUser;
import com.hxj.repository.OaDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private DocumentAccessPolicy accessPolicy;

    private Long applicantAId;

    @BeforeEach
    void setUp() {
        SysUser applicantA = persistUser("user-a", "业务部");
        SysUser applicantB = persistUser("user-b", "业务部");
        SysUser applicantC = persistUser("user-c", "财务部");
        applicantAId = applicantA.getId();

        persistDocument("BX202608290101", applicantA, BusinessType.DAILY_PAYMENT);
        persistDocument("BX202608290102", applicantB, BusinessType.DAILY_PAYMENT);
        persistDocument("FK202608290103", applicantC, BusinessType.BUSINESS_PAYMENT);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void shouldLimitOwnDataScopeToCurrentApplicant() {
        AuthenticatedUser currentUser = principal(
                applicantAId, "user-a", "业务部", List.of("OWN_DOCUMENTS"));

        assertThat(findVisibleCodes(currentUser)).containsExactly("BX202608290101");
    }

    @Test
    void shouldLimitDepartmentDataScopeToCurrentDepartment() {
        AuthenticatedUser currentUser = principal(
                applicantAId, "manager", "业务部", List.of("OWN_DEPARTMENT_DOCUMENTS"));

        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "BX202608290102");
    }

    @Test
    void shouldAllowAllDepartmentsAndAllNodesToViewEverything() {
        AuthenticatedUser currentUser = principal(
                applicantAId, "admin", "总经办", List.of("ALL_DEPARTMENTS_ALL_NODES"));

        assertThat(findVisibleCodes(currentUser))
                .containsExactlyInAnyOrder("BX202608290101", "BX202608290102", "FK202608290103");
    }

    @Test
    void shouldDenyByDefaultWhenTokenHasNoKnownDataScope() {
        AuthenticatedUser currentUser = principal(
                applicantAId, "unknown", "业务部", List.of("UNSUPPORTED_SCOPE"));

        assertThat(findVisibleCodes(currentUser)).isEmpty();
    }

    private List<String> findVisibleCodes(AuthenticatedUser currentUser) {
        return documentRepository.findAll(accessPolicy.visibleTo(currentUser)).stream()
                .map(OaDocument::getDocCode)
                .sorted()
                .toList();
    }

    private AuthenticatedUser principal(
            Long userId,
            String account,
            String department,
            List<String> dataScopes) {
        return new AuthenticatedUser(
                userId,
                account,
                account,
                department,
                "测试岗位",
                List.of("测试角色"),
                List.of("VIEW_OWN_FORMS"),
                dataScopes);
    }

    private SysUser persistUser(String account, String department) {
        SysUser user = new SysUser();
        user.setName(account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded-password");
        user.setDepartment(department);
        user.setPost("员工");
        entityManager.persist(user);
        return user;
    }

    private void persistDocument(String code, SysUser applicant, BusinessType businessType) {
        OaDocument document = new OaDocument();
        document.setDocCode(code);
        document.setBusinessType(businessType);
        document.setApplicant(applicant);
        document.setDepartment(applicant.getDepartment());
        document.setProjectName("测试事项");
        entityManager.persist(document);
    }
}