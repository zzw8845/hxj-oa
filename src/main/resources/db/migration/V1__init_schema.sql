-- 海峡金 OA 审批系统 初始表结构

-- 角色表
CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '角色名称',
    department VARCHAR(100) COMMENT '对应部门',
    post VARCHAR(100) COMMENT '对应岗位',
    data_scope VARCHAR(100) COMMENT '数据范围',
    permissions TEXT COMMENT '权限点集合(JSON)',
    members TEXT COMMENT '成员列表(JSON)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户表
CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    job_no VARCHAR(50) COMMENT '工号',
    account VARCHAR(50) NOT NULL UNIQUE COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT '登录密码(BCrypt)',
    department VARCHAR(100) COMMENT '所属部门',
    post VARCHAR(100) COMMENT '岗位',
    role_id BIGINT COMMENT '分配角色ID',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 流程配置表
CREATE TABLE flow_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(200) NOT NULL UNIQUE COMMENT '业务单据类型',
    category VARCHAR(50) COMMENT '分类(日常/业务/用章)',
    nodes TEXT NOT NULL COMMENT '节点链(JSON数组)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程配置表';

-- 快捷单据目录表
CREATE TABLE quick_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型(日常付款/业务付款/用印申请)',
    name VARCHAR(200) NOT NULL COMMENT '单据名称',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快捷单据目录表';

-- 单据表
CREATE TABLE oa_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_code VARCHAR(50) NOT NULL UNIQUE COMMENT '单据编号',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型(日常付款/业务付款/用印申请)',
    project_name VARCHAR(200) COMMENT '对应项目(申请事项)',
    doc_type VARCHAR(50) NOT NULL COMMENT '单据类型(日常申请单/付款申请单/用印申请单)',
    contract_no VARCHAR(100) COMMENT '关联合同编号(业务付款类)',
    applicant VARCHAR(50) NOT NULL COMMENT '申请人',
    company VARCHAR(100) COMMENT '所属公司',
    department VARCHAR(100) COMMENT '所属部门',
    amount DECIMAL(15,2) DEFAULT 0 COMMENT '申请金额',
    invoice_summary VARCHAR(500) COMMENT '发票明细',
    reason TEXT COMMENT '申请事由',
    need_post_material TINYINT(1) DEFAULT 0 COMMENT '付款后需补材料',
    seal_project VARCHAR(200) COMMENT '用印项目',
    seal_department VARCHAR(100) COMMENT '用印部门',
    seal_time DATETIME COMMENT '用印时间',
    file_name VARCHAR(200) COMMENT '用印文件名称',
    seal_type VARCHAR(50) COMMENT '用章类型',
    seal_reason TEXT COMMENT '用印原因',
    linked_doc_id BIGINT COMMENT '前置关联单据ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING/APPROVING/APPROVED/REJECTED)',
    current_node VARCHAR(100) COMMENT '当前节点',
    process_instance_id VARCHAR(64) COMMENT 'Flowable流程实例ID',
    risk_flag TINYINT(1) DEFAULT 0 COMMENT '风险标记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_doc_link FOREIGN KEY (linked_doc_id) REFERENCES oa_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据表';

-- 附件表
CREATE TABLE oa_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '单据ID',
    node_name VARCHAR(100) COMMENT '所属节点',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '存储路径',
    file_size BIGINT COMMENT '文件大小',
    uploader VARCHAR(50) COMMENT '上传人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attach_doc FOREIGN KEY (doc_id) REFERENCES oa_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件表';

-- 审批记录表
CREATE TABLE approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '单据ID',
    node_name VARCHAR(100) COMMENT '节点名称',
    approver VARCHAR(50) COMMENT '审批人',
    action VARCHAR(50) COMMENT '操作(APPROVE/REJECT/SUPPLEMENT/SIGN)',
    comment TEXT COMMENT '审批意见',
    reject_target VARCHAR(100) COMMENT '驳回目标层级',
    reject_materials VARCHAR(500) COMMENT '驳回需补充材料',
    supplement_mode VARCHAR(20) COMMENT '补材料模式(BEFORE_PAY/AFTER_PAY)',
    supplement_target VARCHAR(100) COMMENT '补材料指定对象',
    supplement_materials VARCHAR(500) COMMENT '需补充材料清单',
    sign_user VARCHAR(50) COMMENT '加签人员',
    sign_reason VARCHAR(500) COMMENT '加签说明',
    evidence_file_id BIGINT COMMENT '审批凭证附件ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_record_doc FOREIGN KEY (doc_id) REFERENCES oa_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 抄送记录表
CREATE TABLE cc_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '单据ID',
    cc_target VARCHAR(200) COMMENT '抄送对象',
    cc_source VARCHAR(20) DEFAULT 'FLOW' COMMENT '抄送来源(FLOW流程配置/SELF发起人自选)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cc_doc FOREIGN KEY (doc_id) REFERENCES oa_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄送记录表';

-- 单据编号序列表(保证并发唯一)
CREATE TABLE doc_seq (
    seq_date VARCHAR(8) NOT NULL PRIMARY KEY COMMENT '日期yyyyMMdd',
    seq_value BIGINT NOT NULL DEFAULT 0 COMMENT '当日流水号'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据编号序列表';

-- 归档台账表
CREATE TABLE archive_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '单据ID',
    doc_code VARCHAR(50) NOT NULL COMMENT '单据编号',
    project_name VARCHAR(200) COMMENT '申请事项',
    business_type VARCHAR(50) COMMENT '业务类型',
    doc_type VARCHAR(50) COMMENT '单据类型',
    company VARCHAR(100) COMMENT '所属公司',
    applicant VARCHAR(50) COMMENT '申请人',
    department VARCHAR(100) COMMENT '申请部门',
    amount DECIMAL(15,2) DEFAULT 0 COMMENT '金额',
    archived_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    CONSTRAINT fk_archive_doc FOREIGN KEY (doc_id) REFERENCES oa_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归档台账表';