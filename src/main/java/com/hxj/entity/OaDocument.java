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

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 单据编号（全局唯一，按业务类型前缀生成）。 */
    @Column(name = "doc_code", nullable = false, unique = true, length = 50, updatable = false)
    private String docCode;

    /** 业务类型（日常付款/业务付款/用印申请）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessType businessType;

    /** 单据类型（日常申请单/付款申请单/用印申请单），由业务类型推导。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "doc_type", nullable = false, length = 50)
    private DocumentType documentType;

    /** 对应项目（申请事项）。 */
    @Column(name = "project_name", length = 200)
    private String projectName;

    /** 关联合同编号（业务付款类填写）。 */
    @Column(name = "contract_no", length = 100)
    private String contractNo;

    /** 申请人。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private SysUser applicant;

    /** 所属公司（海峡金/海峡金供应链）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 50)
    private Company company;

    /** 所属部门。 */
    @Column(length = 100)
    private String department;

    /** 申请金额。 */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    /** 发票明细。 */
    @Column(name = "invoice_summary", length = 500)
    private String invoiceSummary;

    /** 申请事由。 */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** 付款后需补材料。 */
    @Column(name = "need_post_material", nullable = false)
    private boolean needPostMaterial;

    /** 用印项目。 */
    @Column(name = "seal_project", length = 200)
    private String sealProject;

    /** 用印部门。 */
    @Column(name = "seal_department", length = 100)
    private String sealDepartment;

    /** 用印时间。 */
    @Column(name = "seal_time")
    private LocalDateTime sealTime;

    /** 用印文件名称。 */
    @Column(name = "file_name", length = 200)
    private String sealFileName;

    /** 用章类型。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "seal_type", length = 50)
    private SealType sealType;

    /** 用印原因。 */
    @Column(name = "seal_reason", columnDefinition = "TEXT")
    private String sealReason;

    /** 前置关联单据（如续签、变更时关联原单据）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_doc_id")
    private OaDocument linkedDocument;

    /** 单据状态（PENDING待提交/APPROVING审批中/APPROVED已通过/REJECTED已驳回/SUPPLEMENT_REQUIRED待补充材料）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    /** 当前审批节点。 */
    @Column(name = "current_node", length = 100)
    private String currentNode;

    /** Flowable 流程实例 ID。 */
    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    /** 提交时的流程配置 ID（驳回时按历史配置解析目标节点）。 */
    @Column(name = "flow_config_id")
    private Long flowConfigId;

    /** 风险标记。 */
    @Column(name = "risk_flag", nullable = false)
    private boolean riskFlag;

    /** 单据附件集合。 */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OaAttachment> attachments = new LinkedHashSet<>();

    /** 创建时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
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