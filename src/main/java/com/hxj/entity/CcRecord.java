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

/** 流程节点或发起人自选产生的抄送记录。 */
@Entity
@Table(name = "cc_record")
public class CcRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OaDocument document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private SysUser targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_role_id")
    private SysRole targetRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cc_source", nullable = false, length = 20)
    private CcSource source;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CcRecord() {
    }

    public static CcRecord toUser(OaDocument document, SysUser user, CcSource source) {
        CcRecord record = new CcRecord();
        record.document = document;
        record.targetUser = user;
        record.source = source;
        return record;
    }

    public static CcRecord toRole(OaDocument document, SysRole role, CcSource source) {
        CcRecord record = new CcRecord();
        record.document = document;
        record.targetRole = role;
        record.source = source;
        return record;
    }

    public Long getId() { return id; }
    public OaDocument getDocument() { return document; }
    public SysUser getTargetUser() { return targetUser; }
    public SysRole getTargetRole() { return targetRole; }
    public CcSource getSource() { return source; }
    public String getTargetName() {
        return targetUser != null ? targetUser.getName() : targetRole == null ? null : targetRole.getName();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}