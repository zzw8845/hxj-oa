package com.hxj.repository;

import com.hxj.entity.BusinessType;
import com.hxj.entity.Company;
import com.hxj.entity.DocumentStatus;
import com.hxj.entity.DocumentType;
import com.hxj.entity.OaAttachment;
import com.hxj.entity.OaDocument;
import com.hxj.entity.SealType;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class DocumentEntityRepositoryTest {

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

    @Test
    void shouldPersistPaymentDocumentWithPreviousDocumentAndAttachment() {
        SysUser applicant = createApplicant("payment-user");

        OaDocument approvedContract = new OaDocument();
        approvedContract.setDocCode("FK202608290001");
        approvedContract.setBusinessType(BusinessType.BUSINESS_PAYMENT);
        approvedContract.setApplicant(applicant);
        approvedContract.setCompany(Company.HAI_XIA_JIN);
        approvedContract.setProjectName("合同审批");
        approvedContract.setAmount(new BigDecimal("50000.00"));
        approvedContract.setStatus(DocumentStatus.APPROVED);
        documentRepository.save(approvedContract);

        OaDocument payment = new OaDocument();
        payment.setDocCode("FK202608290002");
        payment.setBusinessType(BusinessType.BUSINESS_PAYMENT);
        payment.setApplicant(applicant);
        payment.setCompany(Company.HAI_XIA_JIN_SUPPLY_CHAIN);
        payment.setDepartment("业务支持中心");
        payment.setProjectName("供应商货款");
        payment.setAmount(new BigDecimal("88000.50"));
        payment.setInvoiceSummary("增值税专用发票2张，含税金额88,000.50元");
        payment.setReason("支付供应商货款");
        payment.setNeedPostMaterial(true);
        payment.setContractNo("HT-2026-001");
        payment.setStatus(DocumentStatus.APPROVING);
        payment.setCurrentNode("会计主管&内控");
        payment.setLinkedDocument(approvedContract);
        payment.setProcessInstanceId("process-001");
        payment.setRiskFlag(true);

        OaAttachment invoice = new OaAttachment(
                "invoice.pdf", "/data/oa/invoice.pdf", "application/pdf", 4096L);
        invoice.setNodeName("发起人");
        invoice.setUploader(applicant);
        payment.addAttachment(invoice);

        documentRepository.saveAndFlush(payment);
        entityManager.clear();

        OaDocument persisted = documentRepository.findByDocCode("FK202608290002").orElseThrow();
        assertThat(persisted.getBusinessType()).isEqualTo(BusinessType.BUSINESS_PAYMENT);
        assertThat(persisted.getDocumentType()).isEqualTo(DocumentType.PAYMENT_APPLICATION);
        assertThat(persisted.getApplicant().getAccount()).isEqualTo("payment-user");
        assertThat(persisted.getApplicantName()).isEqualTo("测试申请人");
        assertThat(persisted.getCompany()).isEqualTo(Company.HAI_XIA_JIN_SUPPLY_CHAIN);
        assertThat(persisted.getAmount()).isEqualByComparingTo("88000.50");
        assertThat(persisted.isNeedPostMaterial()).isTrue();
        assertThat(persisted.getContractNo()).isEqualTo("HT-2026-001");
        assertThat(persisted.getStatus()).isEqualTo(DocumentStatus.APPROVING);
        assertThat(persisted.getCurrentNode()).isEqualTo("会计主管&内控");
        assertThat(persisted.getLinkedDocument().getDocCode()).isEqualTo("FK202608290001");
        assertThat(persisted.getProcessInstanceId()).isEqualTo("process-001");
        assertThat(persisted.isRiskFlag()).isTrue();
        assertThat(persisted.getAttachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.getFileName()).isEqualTo("invoice.pdf");
            assertThat(attachment.getContentType()).isEqualTo("application/pdf");
            assertThat(attachment.getUploader().getAccount()).isEqualTo("payment-user");
            assertThat(attachment.getDocument().getDocCode()).isEqualTo("FK202608290002");
        });
        assertThat(documentRepository.findByStatus(DocumentStatus.APPROVING)).containsExactly(persisted);
        assertThat(documentRepository.findByRiskFlagTrue()).containsExactly(persisted);
    }

    @Test
    void shouldPersistSealApplicationWithoutAmount() {
        SysUser applicant = createApplicant("seal-user");

        OaDocument sealDocument = new OaDocument();
        sealDocument.setDocCode("YY202608290001");
        sealDocument.setBusinessType(BusinessType.SEAL_APPLICATION);
        sealDocument.setApplicant(applicant);
        sealDocument.setProjectName("非标合同审批及用印");
        sealDocument.setSealProject("供应商合同盖章");
        sealDocument.setSealDepartment("法务部");
        sealDocument.setSealTime(LocalDateTime.of(2026, 8, 30, 10, 0));
        sealDocument.setSealFileName("供应商合同.pdf");
        sealDocument.setSealType(SealType.CONTRACT_SEAL);
        sealDocument.setSealReason("合同签署");

        documentRepository.saveAndFlush(sealDocument);
        entityManager.clear();

        OaDocument persisted = documentRepository.findByDocCode("YY202608290001").orElseThrow();
        assertThat(persisted.getBusinessType()).isEqualTo(BusinessType.SEAL_APPLICATION);
        assertThat(persisted.getDocumentType()).isEqualTo(DocumentType.SEAL_APPLICATION);
        assertThat(persisted.getAmount()).isNull();
        assertThat(persisted.getSealType()).isEqualTo(SealType.CONTRACT_SEAL);
        assertThat(persisted.getSealFileName()).isEqualTo("供应商合同.pdf");
        assertThat(persisted.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    private SysUser createApplicant(String account) {
        SysDataScope scope = dataScopeRepository.save(
                new SysDataScope("OWN_DOCUMENTS_" + account, "本人单据"));

        SysRole role = new SysRole();
        role.setName("普通员工-" + account);
        role.setDepartment("业务支持中心");
        role.setPost("员工");
        role.setDataScope(scope);
        roleRepository.save(role);

        SysUser applicant = new SysUser();
        applicant.setName("测试申请人");
        applicant.setJobNo("JOB-" + account);
        applicant.setAccount(account);
        applicant.setPassword("encoded-password");
        applicant.setDepartment("业务支持中心");
        applicant.setPost("员工");
        applicant.setRole(role);
        return userRepository.save(applicant);
    }
}