package com.hxj.repository;

import com.hxj.entity.*;
import com.hxj.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ProcessPersistenceRepositoryTest {

    @Autowired
    private ApprovalRecordRepository approvalRecordRepository;

    @Autowired
    private CcRecordRepository ccRecordRepository;

    @Autowired
    private ArchiveLedgerRepository archiveLedgerRepository;

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private OaDocumentRepository documentRepository;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysDataScopeRepository dataScopeRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private com.hxj.repository.SysDepartmentRepository departmentRepository;

    @Autowired
    private com.hxj.repository.SysPostRepository postRepository;

    @Test
    void shouldPersistApprovalCcAndArchiveRecordsWithDomainAssociations() {
        SysUser applicant = createUser("applicant", "普通员工");
        SysUser approver = createUser("approver", "会计主管&内控");

        OaDocument document = new OaDocument();
        document.setDocCode("FK202608290010");
        document.setBusinessType(BusinessTypeEnum.BUSINESS_PAYMENT);
        document.setApplicant(applicant);
        document.setDepartment("业务支持中心");
        document.setProjectName("应付款申请");
        document.setAmount(new BigDecimal("120000.00"));
        document.setStatus(DocumentStatusEnum.APPROVED);

        OaAttachment evidence = new OaAttachment(
                "approval-proof.pdf", "/data/oa/approval-proof.pdf", "application/pdf", 2048L);
        evidence.setUploader(approver);
        evidence.setNodeName("会计主管&内控");
        document.addAttachment(evidence);
        documentRepository.saveAndFlush(document);

        ApprovalRecord approvalRecord = new ApprovalRecord();
        approvalRecord.setDocument(document);
        approvalRecord.setNodeName("会计主管&内控");
        approvalRecord.setApprover(approver);
        approvalRecord.setAction(ApprovalActionEnum.APPROVE);
        approvalRecord.setComment("资料齐全，同意付款");
        approvalRecord.setEvidenceFile(evidence);
        approvalRecordRepository.save(approvalRecord);

        CcRecord ccToUser = CcRecord.toUser(document, applicant, CcSourceEnum.SELF_SELECTED);
        CcRecord ccToRole = CcRecord.toRole(document, approver.getRole(), CcSourceEnum.FLOW);
        ccRecordRepository.save(ccToUser);
        ccRecordRepository.save(ccToRole);

        ArchiveLedger ledger = ArchiveLedger.from(document);
        archiveLedgerRepository.saveAndFlush(ledger);
        entityManager.clear();

        ApprovalRecord persistedApproval = approvalRecordRepository
                .findByDocumentIdOrderByCreatedAtAsc(document.getId()).get(0);
        assertThat(persistedApproval.getApprover().getAccount()).isEqualTo("approver");
        assertThat(persistedApproval.getAction()).isEqualTo(ApprovalActionEnum.APPROVE);
        assertThat(persistedApproval.getEvidenceFile().getFileName()).isEqualTo("approval-proof.pdf");

        assertThat(ccRecordRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()))
                .extracting(CcRecord::getTargetName)
                .containsExactlyInAnyOrder("测试用户-applicant", "会计主管&内控");

        ArchiveLedger persistedLedger = archiveLedgerRepository.findByDocumentId(document.getId()).orElseThrow();
        assertThat(persistedLedger.getDocCode()).isEqualTo("FK202608290010");
        assertThat(persistedLedger.getApplicant()).isEqualTo("测试用户-applicant");
        assertThat(persistedLedger.getDepartment()).isEqualTo("业务支持中心");
        assertThat(persistedLedger.getAmount()).isEqualByComparingTo("120000.00");
        assertThat(persistedLedger.getDocument().getStatus()).isEqualTo(DocumentStatusEnum.APPROVED);
    }

    @Test
    void shouldPersistOrderedFlowNodesAndConditionBranchRules() {
        FlowConfig config = new FlowConfig();
        config.setType("采购申请");
        config.setCategory(FlowCategoryEnum.DAILY);
        config.addNode(new FlowNodeConfig("发起人", FlowNodeTypeEnum.START));
        config.addNode(new FlowNodeConfig("直属主管", FlowNodeTypeEnum.APPROVAL));
        config.addNode(new FlowNodeConfig("执行总经理", FlowNodeTypeEnum.APPROVAL));
        config.addNode(new FlowNodeConfig("采购办理", FlowNodeTypeEnum.HANDLER));
        config.addConditionRule(new FlowConditionRule(
                "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000", "执行总经理"));

        flowConfigRepository.saveAndFlush(config);
        entityManager.clear();

        FlowConfig persisted = flowConfigRepository.findByType("采购申请").orElseThrow();
        assertThat(persisted.getCategory()).isEqualTo(FlowCategoryEnum.DAILY);
        assertThat(persisted.getNodes())
                .extracting(FlowNodeConfig::getName)
                .containsExactly("发起人", "直属主管", "执行总经理", "采购办理");
        assertThat(persisted.getConditionRules()).singleElement().satisfies(rule -> {
            assertThat(rule.getVariableName()).isEqualTo("amount");
            assertThat(rule.getOperator()).isEqualTo(ConditionOperatorEnum.GREATER_THAN_OR_EQUAL);
            assertThat(rule.getExpectedValue()).isEqualTo("20000");
            assertThat(rule.getTargetNodeName()).isEqualTo("执行总经理");
        });
    }

    private SysUser createUser(String account, String roleName) {
        SysDataScope scope = dataScopeRepository.save(
                new SysDataScope("SCOPE_" + account, "测试数据范围"));

        SysRole role = new SysRole();
        role.setName(roleName);
        role.setDepartment("财务中心");
        role.setPost(roleName);
        role.setDataScope(scope);
        roleRepository.save(role);

        SysUser user = new SysUser();
        user.setName("测试用户-" + account);
        user.setJobNo("JOB-" + account);
        user.setAccount(account);
        user.setPassword("encoded-password");
        com.hxj.support.DictionaryTestSupport.applyDictionary(user,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, "财务中心"),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, roleName));
        user.setRole(role);
        return userRepository.save(user);
    }
}