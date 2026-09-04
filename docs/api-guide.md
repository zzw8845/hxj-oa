# 海峡金 OA 审批系统 API 对接说明

## 1. 基本信息

- **Base URL**: `http://localhost:8080`
- **接口格式**: JSON（附件上传/下载除外）
- **字符编码**: UTF-8
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

## 2. 认证方式

系统采用 **JWT Bearer Token** 认证。

### 2.1 登录获取 Token

```
POST /api/auth/login
Content-Type: application/json
```

**请求体**：

```json
{
  "account": "admin",
  "password": "123456"
}
```

**响应 200**：

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 14400,
  "user": {
    "id": 1,
    "jobNo": "JOB001",
    "account": "admin",
    "name": "超级管理员",
    "department": "总经办",
    "post": "管理员",
    "roles": ["超级管理员"],
    "permissions": ["SUBMIT_ALL_FORMS", "CONFIGURE_FLOW_PERMISSION", "..."],
    "dataScopes": ["ALL"]
  }
}
```

### 2.2 携带 Token 访问受保护接口

所有接口（除登录外）均需在请求头携带：

```
Authorization: Bearer <accessToken>
```

### 2.3 获取当前用户信息

```
GET /api/auth/me
Authorization: Bearer <accessToken>
```

### 2.4 安全规范

- **Token 有效期**：Access Token 4 小时（14400 秒），Refresh Token 7 天。
- **Token 刷新**：Access Token 过期后，调用 `POST /api/auth/refresh` 携带 Refresh Token 换取新 Token（同时轮换 Refresh Token）：

```json
// 请求
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }

// 响应 200（与登录响应结构一致）
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 14400,
  "user": { ... }
}
```

- **密码传输**：密码通过 HTTPS 传输（生产环境必须启用 TLS，由网关/反向代理终结 SSL）。应用层不做额外加密，依赖传输层安全。
- **密码存储**：后端使用 BCrypt 加密存储，数据库不保存明文。
- **生产部署检查项**：更换 `app.jwt.secret` 为强随机密钥；启用 HTTPS；数据库账号设置密码。

## 3. 接口清单

### 3.1 认证（/api/auth）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/auth/login | 登录，获取 JWT + Refresh Token | 公开 |
| POST | /api/auth/refresh | 刷新 Token（Refresh Token 换新） | 公开（校验 Refresh Token） |
| GET | /api/auth/me | 当前登录用户信息 | 已认证 |

### 3.2 单据管理（/api/documents）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/documents | 提交单据 | SUBMIT_ALL_FORMS / VIEW_OWN_FORMS |
| GET | /api/documents | 查询单据列表（组合筛选） | 已认证 |
| GET | /api/documents/page | 分页查询单据列表（page/size） | 已认证 |
| GET | /api/documents/{id} | 单据详情 | 已认证（数据范围） |
| GET | /api/documents/link-candidates | 前置单据关联候选 | 已认证 |
| GET | /api/documents/quick | 快捷单据目录 | 已认证 |
| POST | /api/documents/{id}/repeat | 再次提交 | 已认证 |
| POST | /api/documents/{id}/attachments | 上传附件 | 已认证 |
| GET | /api/documents/attachments/{attachmentId} | 下载附件 | 已认证 |
| GET | /api/documents/attachment-requirements | 必传附件清单 | 已认证 |

**附件上传规范**（POST /api/documents/{id}/attachments）：

- 请求格式：`multipart/form-data`
- 表单字段：`file`（必填，文件本体）、`nodeName`（可选，关联的流程节点名）
- 大小限制：单文件 ≤ 20MB（服务端 `spring.servlet.multipart.max-file-size` 限制）
- 返回结构：

```json
{
  "code": 0,
  "data": {
    "id": 12,
    "fileName": "发票.pdf",
    "contentType": "application/pdf",
    "size": 102400
  },
  "msg": null
}
```

**提交单据请求体示例**：

```json
{
  "businessType": "DAILY_PAYMENT",
  "projectName": "办公用品采购",
  "company": "HAI_XIA_JIN",
  "amount": 5000.00,
  "invoiceSummary": "增值税普通发票 3 张",
  "reason": "日常办公采购",
  "needPostMaterial": false,
  "contractNo": null,
  "linkedDocumentId": null,
  "involvesFunds": true,
  "requiresAdminReview": false,
  "ccUserIds": [2, 3]
}
```

### 3.3 审批操作（/api/documents/{documentId}/actions）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/documents/{id}/actions/approve | 通过审批 | 当前审批人/超管 |
| POST | /api/documents/{id}/actions/reject | 指定层级驳回 | 当前审批人/超管 |
| POST | /api/documents/{id}/actions/supplement | 通过但补材料 | 当前审批人/超管 |
| POST | /api/documents/{id}/actions/sign | 加签 | 当前审批人/超管 |
| POST | /api/documents/{id}/actions/sign-comment | 加签意见 | 加签人 |
| POST | /api/documents/{id}/actions/supplement-materials | 补充材料回传 | 申请人 |
| POST | /api/documents/{id}/actions/stamped-file | 用印盖章文件回传 | 申请人 |
| GET | /api/documents/{id}/actions/history | 流程历史查询 | 已认证（数据范围） |

**通过审批请求体示例**：

```json
{
  "comment": "同意",
  "evidenceFileId": 12
}
```

**驳回请求体示例**：

```json
{
  "comment": "金额有误，请核实",
  "rejectTarget": "直属主管",
  "rejectMaterials": "补充发票明细"
}
```

**通过但补材料请求体示例**：

```json
{
  "comment": "通过，需补充付款凭证",
  "supplementMode": "BEFORE_PAYMENT",
  "supplementTarget": "申请人",
  "supplementMaterials": "付款凭证"
}
```

**加签请求体示例**：

```json
{
  "signUserId": 5,
  "comment": "请财务经理会签"
}
```

### 3.4 归档台账（/api/archive-ledger）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/archive-ledger | 归档查询（多条件组合） | 已认证 |
| GET | /api/archive-ledger/page | 分页归档查询（page/size） | 已认证 |
| GET | /api/archive-ledger/export | 台账导出（XLSX/CSV） | 已认证 |

**查询参数**：`applicant`、`department`、`docCode`、`archivedFrom`、`archivedTo`、`format`（XLSX/CSV）

**时间参数格式**：全系统统一使用 ISO8601 日期格式 `yyyy-MM-dd`（如 `archivedFrom=2026-09-01`）；响应体中的时间字段统一为 ISO8601 完整格式 `yyyy-MM-dd'T'HH:mm:ss`（如 `2026-09-02T16:00:00`），时区为 Asia/Shanghai。

### 3.5 员工与角色管理（/api/admin）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /api/admin/employees | 创建员工账号 | CONFIGURE_FLOW_PERMISSION |
| PUT | /api/admin/employees/{id} | 编辑员工 | CONFIGURE_FLOW_PERMISSION |
| GET | /api/admin/employees | 员工列表 | CONFIGURE_FLOW_PERMISSION |
| POST | /api/admin/roles | 新增角色 | CONFIGURE_FLOW_PERMISSION |
| PUT | /api/admin/roles/{id} | 修改角色 | CONFIGURE_FLOW_PERMISSION |
| GET | /api/admin/roles | 角色列表 | CONFIGURE_FLOW_PERMISSION |
| GET | /api/admin/roles/tree | 部门角色架构树 | CONFIGURE_FLOW_PERMISSION |

### 3.6 流程配置（/api/flow-configs）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/flow-configs | 流程配置列表 | 已认证 |
| GET | /api/flow-configs/by-type?type=费用报销 | 按类型查询节点链 | 已认证 |
| GET | /api/flow-configs/{id} | 流程配置详情 | 已认证 |
| POST | /api/admin/flow-configs | 新增流程配置 | CONFIGURE_FLOW_PERMISSION |
| PUT | /api/admin/flow-configs/{id} | 修改流程配置 | CONFIGURE_FLOW_PERMISSION |

### 3.7 统计看板与风险预警（/api/dashboard）

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | /api/dashboard/home | 首页统计（数量/环比/合规率/角标） | 已认证 |
| GET | /api/dashboard/todos | 首页待办审批列表（临近超时优先） | 已认证 |
| GET | /api/dashboard/board | 工作看板统计 | 已认证 |
| GET | /api/dashboard/trend | 近7天流程趋势 | 已认证 |
| GET | /api/dashboard/risks | 风险预警列表（金额 ≥ 8 万） | 已认证 |

## 4. 响应格式

所有接口（成功和错误）统一返回 `ApiResponse` 格式：

### 成功响应

```json
{
  "code": 0,
  "data": {},
  "msg": null
}
```

`data` 字段为接口具体的业务数据结构。

### 错误响应

```json
{
  "code": 400,
  "data": null,
  "msg": "错误描述",
  "error": "DOCUMENT_NOT_FOUND"
}
```

| code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数校验失败或业务规则校验失败 |
| 401 | 未登录或 Token 无效/过期 |
| 403 | 无权限执行此操作 |
| 500 | 服务器内部错误 |

`error` 字段为业务错误码（仅业务校验失败时返回），前端可据此做精细化提示。常见业务错误码：

| error | 说明 |
|-------|------|
| DOCUMENT_NOT_FOUND | 单据不存在或无权查看 |
| FLOW_CONFIG_NOT_FOUND | 流程配置不存在 |
| APPROVAL_NOT_ALLOWED | 当前用户不是该单据的审批人 |
| ROLE_EXISTS / ACCOUNT_EXISTS / JOB_NO_EXISTS | 角色/账号/工号已存在 |
| EVIDENCE_REQUIRED | 请上传当前节点凭证 |
| FILE_NOT_FOUND | 文件不存在 |

## 5. 数据范围说明

- **本人（OWN_DOCUMENTS）**：仅可见本人提交的单据
- **本部门（DEPARTMENT）**：可见本部门成员提交的单据
- **全部（ALL）**：可见系统内全部单据

## 6. 业务类型枚举

| 枚举值 | 说明 |
|--------|------|
| DAILY_PAYMENT | 日常付款 |
| BUSINESS_PAYMENT | 业务付款 |
| EMPLOYEE_REIMBURSEMENT | 员工报销 |
| SEAL_APPLICATION | 用印申请 |

## 7. 单据状态枚举与流转

| 枚举值 | 说明 |
|--------|------|
| PENDING | 待提交 |
| APPROVING | 审批中 |
| APPROVED | 已通过 |
| REJECTED | 已驳回 |
| SUPPLEMENT_REQUIRED | 待补充材料 |
| ARCHIVED | 已归档 |

**状态流转图**：

```
PENDING ──提交──> APPROVING ──通过──> APPROVED ──归档──> ARCHIVED
                     │  │
                     │  └──通过但补材料──> SUPPLEMENT_REQUIRED ──回传材料──> APPROVING
                     └──驳回──> REJECTED ──重新提交（生成新单据）──> APPROVING
```

流转规则：

- `ARCHIVED` 为终态，不可回退
- `REJECTED` 后不可修改原单，只能通过"再次提交"接口（POST /api/documents/{id}/repeat）复制生成新单据
- `SUPPLEMENT_REQUIRED` 由申请人回传材料后回到 `APPROVING` 继续审批

## 8. 审批操作字段说明

**驳回（reject）**：

- `rejectTarget`：驳回目标节点名，取值来源为该单据流程中的节点（可通过 GET /api/flow-configs/by-type 查询节点链，或通过流程历史接口查看已走过的节点）。传不在流程中的节点将返回 `REJECT_TARGET_INVALID` 错误。

**加签（sign）**：

- `signUserId`：加签目标用户 ID，当前允许加签任意在职用户；加签人需填写意见（sign-comment）后流程才继续。循环加签由流程引擎防重机制约束（同一单据同一节点不可重复加签同一人）。

## 9. 分页与幂等性

### 9.1 分页

列表接口（`GET /api/documents/page`、`GET /api/archive-ledger/page`）支持分页，分页参数由统一的 `PageParam` 基类约束（分页查询请求 DTO 继承它）：

| 参数 | 说明 | 默认 |
|------|------|------|
| page | 页码，从 1 开始，最小 1 | 1 |
| size | 每页条数，最小 1、最大 1000 | 10 |

分页参数不合法（如 `page=0`、`size=2000`）返回 400，`msg` 中带字段校验信息。

响应结构（`page` 回显请求的页码）：

```json
{
  "code": 0,
  "data": {
    "content": [ ... ],
    "page": 1,
    "size": 10,
    "totalElements": 100,
    "totalPages": 10
  },
  "msg": null
}
```

### 9.2 幂等性

提交单据、审批操作（通过/驳回/补材料/加签/材料回传）等写接口支持幂等，**前端零配合即可生效**：

**双模式幂等键：**

| 模式 | 触发条件 | 幂等范围 | 缓存时长 |
|-6-----|---------|---------|---------|
| 前端显式 key（推荐） | 请求头携带 `Idempotency-Key`（如 UUID） | 精确控制，相同 key 返回首次结果 | 10 分钟 |
| 自动 key（零配合） | 未携带请求头 | 同一用户 + 相同接口 + 相同参数 | 5 秒 |

- **自动 key 说明**：后端基于「用户 + 接口 + 参数摘要」自动生成幂等键，5 秒窗口仅覆盖网络重试场景（超时重发、双击提交），不会误伤相同参数的合法重复业务（如再次提交相同单据）
- **失败不缓存**：业务执行失败立即清理，客户端可重试
- **并发安全**：并发相同 key 的请求只有一个执行，其余等待并复用其结果
- **限制**：内存实现适用于单实例部署；多实例部署需将内存缓存替换为 Redis（当前为单实例内存实现）
