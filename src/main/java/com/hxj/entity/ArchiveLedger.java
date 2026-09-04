package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 已通过单据在台账中的不可变业务快照。 */
@Entity
@Table(name = "archive_ledger")
public class ArchiveLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false, unique = true)
    private OaDocument document;

    @Column(name = "doc_code", nullable = false, length = 50)
    private String docCode;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "doc_type", length = 50)
    private String documentType;

    @Column(length = 100)
    private String company;

    @Column(length = 50)
    private String applicant;

    @Column(length = 100)
    private String department;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "archived_at", insertable = false, updatable = false)
    private LocalDateTime archivedAt;

    protected ArchiveLedger() {
    }

    public static ArchiveLedger from(OaDocument document) {
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new IllegalArgumentException("仅已通过单据可归档");
        }
        ArchiveLedger ledger = new ArchiveLedger();
        ledger.document = document;
        ledger.docCode = document.getDocCode();
        ledger.projectName = document.getProjectName();
        ledger.businessType = document.getBusinessType().name();
        ledger.documentType = document.getDocumentType().name();
        ledger.company = document.getCompany() == null ? null : document.getCompany().name();
        ledger.applicant = document.getApplicantName();
        ledger.department = document.getDepartment();
        ledger.amount = document.getAmount();
        return ledger;
    }

    public Long getId() { return id; }
    public OaDocument getDocument() { return document; }
    public String getDocCode() { return docCode; }
    public String getProjectName() { return projectName; }
    public String getBusinessType() { return businessType; }
    public String getDocumentType() { return documentType; }
    public String getCompany() { return company; }
    public String getApplicant() { return applicant; }
    public String getDepartment() { return department; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
}