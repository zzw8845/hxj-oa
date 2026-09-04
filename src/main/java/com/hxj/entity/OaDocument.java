package com.hxj.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 日常付款、业务付款和用印申请的统一单据实体。 */
@Entity
@Table(name = "oa_document")
public class OaDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_code", nullable = false, unique = true, length = 50, updatable = false)
    private String docCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "doc_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private SysUser applicant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 50)
    private Company company;

    @Column(length = 100)
    private String department;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "invoice_summary", length = 500)
    private String invoiceSummary;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "need_post_material", nullable = false)
    private boolean needPostMaterial;

    @Column(name = "seal_project", length = 200)
    private String sealProject;

    @Column(name = "seal_department", length = 100)
    private String sealDepartment;

    @Column(name = "seal_time")
    private LocalDateTime sealTime;

    @Column(name = "file_name", length = 200)
    private String sealFileName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seal_type", length = 50)
    private SealType sealType;

    @Column(name = "seal_reason", columnDefinition = "TEXT")
    private String sealReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_doc_id")
    private OaDocument linkedDocument;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "current_node", length = 100)
    private String currentNode;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "flow_config_id")
    private Long flowConfigId;

    @Column(name = "risk_flag", nullable = false)
    private boolean riskFlag;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OaAttachment> attachments = new LinkedHashSet<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public String getDocCode() { return docCode; }
    public void setDocCode(String docCode) { this.docCode = docCode; }
    public BusinessType getBusinessType() { return businessType; }
    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
        this.documentType = businessType == null ? null : businessType.toDocumentType();
        if (businessType == BusinessType.SEAL_APPLICATION) {
            this.amount = null;
        }
    }
    public DocumentType getDocumentType() { return documentType; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getContractNo() { return contractNo; }
    public void setContractNo(String contractNo) { this.contractNo = contractNo; }
    public SysUser getApplicant() { return applicant; }
    public void setApplicant(SysUser applicant) { this.applicant = applicant; }
    public String getApplicantName() { return applicant == null ? null : applicant.getName(); }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getInvoiceSummary() { return invoiceSummary; }
    public void setInvoiceSummary(String invoiceSummary) { this.invoiceSummary = invoiceSummary; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isNeedPostMaterial() { return needPostMaterial; }
    public void setNeedPostMaterial(boolean needPostMaterial) { this.needPostMaterial = needPostMaterial; }
    public String getSealProject() { return sealProject; }
    public void setSealProject(String sealProject) { this.sealProject = sealProject; }
    public String getSealDepartment() { return sealDepartment; }
    public void setSealDepartment(String sealDepartment) { this.sealDepartment = sealDepartment; }
    public LocalDateTime getSealTime() { return sealTime; }
    public void setSealTime(LocalDateTime sealTime) { this.sealTime = sealTime; }
    public String getSealFileName() { return sealFileName; }
    public void setSealFileName(String sealFileName) { this.sealFileName = sealFileName; }
    public SealType getSealType() { return sealType; }
    public void setSealType(SealType sealType) { this.sealType = sealType; }
    public String getSealReason() { return sealReason; }
    public void setSealReason(String sealReason) { this.sealReason = sealReason; }
    public OaDocument getLinkedDocument() { return linkedDocument; }
    public void setLinkedDocument(OaDocument linkedDocument) { this.linkedDocument = linkedDocument; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public String getCurrentNode() { return currentNode; }
    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public Long getFlowConfigId() { return flowConfigId; }
    public void setFlowConfigId(Long flowConfigId) { this.flowConfigId = flowConfigId; }
    public boolean isRiskFlag() { return riskFlag; }
    public void setRiskFlag(boolean riskFlag) { this.riskFlag = riskFlag; }
    public Set<OaAttachment> getAttachments() { return attachments; }
    public void addAttachment(OaAttachment attachment) {
        attachments.add(attachment);
        attachment.setDocument(this);
    }
    public void removeAttachment(OaAttachment attachment) {
        attachments.remove(attachment);
        attachment.setDocument(null);
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}