package com.hxj.entity;

import com.hxj.enums.ApprovalActionEnum;
import com.hxj.enums.SupplementModeEnum;
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

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属单据。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OaDocument document;

    /** 节点名称。 */
    @Column(name = "node_name", length = 100)
    private String nodeName;

    /** 审批人。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id", nullable = false)
    private SysUser approver;

    /** 审批操作（APPROVE通过/REJECT驳回/SUPPLEMENT补充材料/SIGN加签）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 50)
    private ApprovalActionEnum action;

    /** 审批意见。 */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /** 驳回目标层级。 */
    @Column(name = "reject_target", length = 100)
    private String rejectTarget;

    /** 驳回需补充材料。 */
    @Column(name = "reject_materials", length = 500)
    private String rejectMaterials;

    /** 补材料模式（BEFORE_PAY付款前/AFTER_PAY付款后）。 */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "supplement_mode", length = 20)
    private SupplementModeEnum supplementMode;

    /** 补材料指定对象。 */
    @Column(name = "supplement_target", length = 100)
    private String supplementTarget;

    /** 需补充材料清单。 */
    @Column(name = "supplement_materials", length = 500)
    private String supplementMaterials;

    /** 加签人员。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sign_user_id")
    private SysUser signUser;

    /** 加签说明。 */
    @Column(name = "sign_reason", length = 500)
    private String signReason;

    /** 审批凭证附件。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_file_id")
    private OaAttachment evidenceFile;

    /** 补充材料要求是否已被后续上传解决；仅 SUPPLEMENT 记录使用。 */
    @Column(nullable = false)
    private boolean resolved = true;

    /** 操作时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public OaDocument getDocument() { return document; }
    public void setDocument(OaDocument document) { this.document = document; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public SysUser getApprover() { return approver; }
    public void setApprover(SysUser approver) { this.approver = approver; }
    public ApprovalActionEnum getAction() { return action; }
    public void setAction(ApprovalActionEnum action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getRejectTarget() { return rejectTarget; }
    public void setRejectTarget(String rejectTarget) { this.rejectTarget = rejectTarget; }
    public String getRejectMaterials() { return rejectMaterials; }
    public void setRejectMaterials(String rejectMaterials) { this.rejectMaterials = rejectMaterials; }
    public SupplementModeEnum getSupplementMode() { return supplementMode; }
    public void setSupplementMode(SupplementModeEnum supplementMode) { this.supplementMode = supplementMode; }
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