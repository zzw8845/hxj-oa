package com.hxj.archive;

import com.hxj.entity.*;
import com.hxj.enums.BusinessTypeEnum;
import com.hxj.enums.CompanyEnum;
import com.hxj.enums.DocumentStatusEnum;
import com.hxj.repository.ArchiveLedgerRepository;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 7.1–7.2 归档台账：多条件组合查询与 Excel/CSV 导出。 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(ArchiveLedgerService.class)
class ArchiveLedgerServiceTest {

    @Autowired private ArchiveLedgerService ledgerService;
    @Autowired private ArchiveLedgerRepository ledgerRepository;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private SysUserRepository userRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;

    @BeforeEach
    void setUp() {
        ledgerRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSearchByCombinedConditions() {
        archivedDocument("BX202609010001", "费用报销", "张三", "财务部", new BigDecimal("1200"));
        archivedDocument("FK202609010002", "合作方退款", "李四", "业务部", new BigDecimal("90000"));
        archivedDocument("YY202609010003", "非标合同审批及用印", "张三", "法务部", null);

        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest(null, null, null, null, null))).hasSize(3);
        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest("张三", null, null, null, null)))
                .extracting(ArchiveLedgerItemResponse::docCode)
                .containsExactlyInAnyOrder("BX202609010001", "YY202609010003");
        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest("张三", "财务部", null, null, null)))
                .extracting(ArchiveLedgerItemResponse::docCode)
                .containsExactly("BX202609010001");
        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest(null, null, "FK", null, null)))
                .extracting(ArchiveLedgerItemResponse::docCode)
                .containsExactly("FK202609010002");
        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest(null, null, null,
                LocalDateTime.of(2026, 9, 2, 0, 0), LocalDateTime.of(2026, 9, 3, 0, 0)))).isEmpty();
        assertThat(ledgerService.search(new ArchiveLedgerQueryRequest("王五", null, null, null, null))).isEmpty();
    }

    @Test
    void shouldExportExcelAndCsvWithKeyFields() throws Exception {
        archivedDocument("BX202609010001", "费用报销", "张三", "财务部", new BigDecimal("1200"));

        ArchiveLedgerService.ExportFile excel = ledgerService.export(
                new ArchiveLedgerQueryRequest(null, null, null, null, null), ArchiveLedgerService.ExportFormatEnum.XLSX);
        assertThat(excel.fileName()).isEqualTo("archive-ledger.xlsx");
        assertThat(excel.content()).startsWith(new byte[]{'P', 'K'});

        ArchiveLedgerService.ExportFile csv = ledgerService.export(
                new ArchiveLedgerQueryRequest(null, null, null, null, null), ArchiveLedgerService.ExportFormatEnum.CSV);
        String csvText = new String(csv.content(), StandardCharsets.UTF_8);
        assertThat(csv.fileName()).isEqualTo("archive-ledger.csv");
        assertThat(csvText).startsWith("\ufeff单据编号");
        assertThat(csvText).contains("BX202609010001", "费用报销", "张三", "财务部", "1200");
    }

    private void archivedDocument(String docCode, String projectName, String applicantName, String department, BigDecimal amount) {
        SysUser applicant = new SysUser();
        applicant.setName(applicantName);
        applicant.setJobNo("JOB-" + docCode);
        applicant.setAccount("acct-" + docCode);
        applicant.setPassword("encoded");
        com.hxj.support.DictionaryTestSupport.applyDictionary(applicant,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, department),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "员工"));
        applicant = userRepository.save(applicant);

        OaDocument document = new OaDocument();
        document.setDocCode(docCode);
        document.setBusinessType(docCode.startsWith("YY") ? BusinessTypeEnum.SEAL_APPLICATION
                : docCode.startsWith("FK") ? BusinessTypeEnum.BUSINESS_PAYMENT : BusinessTypeEnum.DAILY_PAYMENT);
        document.setProjectName(projectName);
        document.setApplicant(applicant);
        document.setCompany(CompanyEnum.HAI_XIA_JIN);
        document.setDepartment(department);
        document.setAmount(amount);
        document.setNeedPostMaterial(false);
        document.setStatus(DocumentStatusEnum.APPROVED);
        document = documentRepository.saveAndFlush(document);

        ledgerRepository.save(ArchiveLedger.from(document));
    }
}