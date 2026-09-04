package com.hxj.entity;

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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** 审批操作的业务留痕。 */
@Entity
@Table(name = "approval_record")
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OaDocument document;

    @Column(name = "node_name", length = 100)
    private String nodeName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private SysUser approver;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private ApprovalAction action;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "reject_target", length = 100)
    private String rejectTarget;

    @Column(name = "reject_materials", length = 500)
    private String rejectMaterials;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "supplement_mode", length = 20)
    private SupplementMode supplementMode;

    @Column(name = "supplement_target", length = 100)
    private String supplementTarget;

    @Column(name = "supplement_materials", length = 500)
    private String supplementMaterials;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_user_id")
    private SysUser signUser;

    @Column(name = "sign_reason", length = 500)
    private String signReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_file_id")
    private OaAttachment evidenceFile;

    /** 补充材料要求是否已被后续上传解决；仅 SUPPLEMENT 记录使用。 */
    @Column(nullable = false)
    private boolean resolved = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public OaDocument getDocument() { return document; }
    public void setDocument(OaDocument document) { this.document = document; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public SysUser getApprover() { return approver; }
    public void setApprover(SysUser approver) { this.approver = approver; }
    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getRejectTarget() { return rejectTarget; }
    public void setRejectTarget(String rejectTarget) { this.rejectTarget = rejectTarget; }
    public String getRejectMaterials() { return rejectMaterials; }
    public void setRejectMaterials(String rejectMaterials) { this.rejectMaterials = rejectMaterials; }
    public SupplementMode getSupplementMode() { return supplementMode; }
    public void setSupplementMode(SupplementMode supplementMode) { this.supplementMode = supplementMode; }
    public String getSupplementTarget() { return supplementTarget; }
    public void setSupplementTarget(String supplementTarget) { this.supplementTarget = supplementTarget; }
    public String getSupplementMaterials() { return supplementMaterials; }
    public void setSupplementMaterials(String supplementMaterials) { this.supplementMaterials = supplementMaterials; }
    public SysUser getSignUser() { return signUser; }
    public void setSignUser(SysUser signUser) { this.signUser = signUser; }
    public String getSignReason() { return signReason; }
    public void setSignReason(String signReason) { this.signReason = signReason; }
    public OaAttachment getEvidenceFile() { return evidenceFile; }
    public void setEvidenceFile(OaAttachment evidenceFile) { this.evidenceFile = evidenceFile; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}