package com.hxj.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 单据及审批节点上传文件的元数据。 */
@Entity
@Table(name = "oa_attachment")
public class OaAttachment {

    /** 主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属单据。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doc_id", nullable = false)
    private OaDocument document;

    /** 所属节点（上传该文件时所在的流程节点）。 */
    @Column(name = "field_key", length = 50)
    private String fieldKey;
    @Column(name = "node_name", length = 100)
    private String nodeName;

    /** 原始文件名。 */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** 存储路径。 */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** 文件 MIME 类型。 */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 文件大小（字节）。 */
    @Column(name = "file_size")
    private Long fileSize;

    /** 上传人。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private SysUser uploader;

    /** 上传时间。 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OaAttachment() {
    }

    public OaAttachment(String fileName, String filePath, String contentType, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public Long getId() { return id; }
    public OaDocument getDocument() { return document; }
    void setDocument(OaDocument document) { this.document = document; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public SysUser getUploader() { return uploader; }
    public void setUploader(SysUser uploader) { this.uploader = uploader; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
}
