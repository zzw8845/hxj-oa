# 海峡金 OA 审批系统开发规范

> 本文件为开发者和 AI Coding Agent 提供项目特定的开发指导和规范。
> 在进行任何开发工作前，请仔细阅读并遵守本规范。

---

## 1. 项目概述

**海峡金 OA 审批系统** 是一个基于 Spring Boot 的企业级审批工作流系统，支持单据提交、多级审批、归档台账、统计看板等功能。

### 技术栈
- **Java 17**
- **Spring Boot 3.2.5**（Web / Data JPA / Security / AOP / Validation）
- **Spring Security + JWT**（无状态认证）
- **Flowable 7.0**（工作流引擎）
- **MySQL 8**（生产数据库）
- **Flyway**（数据库版本管理）
- **Redis**（幂等性缓存、未来 Token 管理）
- **SpringDoc OpenAPI**（API 文档）
- **EasyExcel**（台账导出）
- **H2**（测试数据库）

---

## 2. 项目结构

```
src/main/java/com/hxj/
├── OaApplication.java              # 启动类
├── approval/                       # 审批操作模块
│   ├── ApprovalActionController.java
│   ├── ApprovalActionService.java
│   ├── ApprovalRequest.java        # 审批请求 DTO
│   ├── ApprovalResult.java         # 审批结果 DTO
│   ├── SignRequest.java            # 加签请求 DTO
│   └── ApprovalHistoryItem.java    # 审批历史项 DTO
├── archive/                        # 归档台账模块
│   ├── ArchiveLedgerController.java
│   ├── ArchiveLedgerService.java
│   ├── ArchiveLedgerQuery.java     # 归档查询条件 DTO
│   └── ArchiveLedgerItem.java      # 归档条目 DTO
├── auth/                           # 认证模块
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── LoginRequest.java           # 登录请求 DTO
│   ├── LoginResponse.java          # 登录响应 DTO
│   └── RefreshTokenRequest.java    # 刷新 Token 请求 DTO
├── common/                         # 通用组件
│   ├── ApiResponse.java            # 统一响应封装
│   ├── PageParam.java              # 分页请求参数基类
│   ├── PageResponse.java           # 分页响应封装
│   ├── RequestLoggingAspect.java   # 请求日志切面
│   ├── Idempotent.java             # 幂等性注解
│   ├── IdempotencyAspect.java      # 幂等性切面
│   ├── IdempotencyStore.java       # 幂等性存储接口
│   ├── RedisIdempotencyStore.java  # Redis 幂等性存储
│   └── InMemoryIdempotencyStore.java # 内存幂等性存储（降级）
├── config/                         # 配置类
│   ├── SecurityConfig.java         # Spring Security 配置
│   ├── SecurityBeansConfig.java    # Security 相关 Bean
│   ├── MethodSecurityConfig.java   # 方法级安全配置
│   ├── CorsConfig.java             # 跨域配置
│   └── TimeConfig.java             # 时间序列化配置
├── dashboard/                      # 统计看板模块
│   ├── DashboardController.java
│   ├── DashboardService.java
│   └── DashboardViews.java         # 看板视图 DTO
├── document/                       # 单据管理模块
│   ├── DocumentController.java
│   ├── DocumentApplicationService.java
│   ├── DocumentClassifier.java     # 单据分类器
│   ├── DocumentDetail.java         # 单据详情 DTO
│   ├── DocumentSummary.java        # 单据摘要 DTO
│   ├── DocumentSearchCriteria.java # 单据查询条件 DTO
│   ├── SubmitDocumentRequest.java  # 提交单据请求 DTO
│   ├── AttachmentRequirementService.java # 必传附件服务
│   ├── LocalAttachmentStorage.java # 本地附件存储
│   └── QuickDocumentItem.java      # 快捷单据项 DTO
├── entity/                         # 数据库实体（JPA Entity）
│   ├── OaDocument.java             # 单据实体
│   ├── OaAttachment.java           # 附件实体
│   ├── ApprovalRecord.java         # 审批记录实体
│   ├── ApprovalAction.java         # 审批操作枚举
│   ├── CcRecord.java               # 抄送记录实体
│   ├── CcSource.java               # 抄送来源枚举
│   ├── Company.java                # 公司枚举
│   ├── DocumentStatus.java         # 单据状态枚举
│   ├── DocumentType.java           # 单据类型枚举
│   ├── FlowConfig.java             # 流程配置实体
│   ├── FlowNodeConfig.java         # 流程节点配置实体
│   ├── FlowNodeType.java           # 流程节点类型枚举
│   ├── FlowConditionRule.java      # 流程条件规则实体
│   ├── FlowCategory.java           # 流程分类枚举
│   ├── ConditionOperator.java      # 条件操作符枚举
│   ├── BusinessType.java           # 业务类型枚举
│   ├── SealType.java               # 印章类型枚举
│   ├── SupplementMode.java         # 补材料模式枚举
│   ├── SysUser.java                # 系统用户实体
│   ├── SysRole.java                # 系统角色实体
│   ├── SysPermission.java          # 系统权限实体
│   ├── SysDataScope.java           # 数据范围实体
│   ├── UserStatus.java             # 用户状态枚举
│   └── QuickDocument.java          # 快捷单据实体
├── exception/                      # 异常处理
│   ├── BusinessException.java      # 业务异常
│   └── GlobalExceptionHandler.java # 全局异常处理器
├── permission/                     # 员工与角色管理模块
│   ├── PermissionManagementController.java
│   ├── EmployeeManagementService.java
│   ├── RoleManagementService.java
│   ├── CreateEmployeeRequest.java  # 创建员工请求 DTO
│   ├── UpdateEmployeeRequest.java  # 更新员工请求 DTO
│   ├── EmployeeResponse.java       # 员工响应 DTO
│   ├── SaveRoleRequest.java        # 保存角色请求 DTO
│   ├── RoleResponse.java           # 角色响应 DTO
│   ├── RoleTreeNode.java           # 角色树节点 DTO
│   └── DepartmentRoleNode.java     # 部门角色节点 DTO
├── repository/                     # 数据访问层（JPA Repository）
│   ├── OaDocumentRepository.java
│   ├── OaAttachmentRepository.java
│   ├── ApprovalRecordRepository.java
│   ├── CcRecordRepository.java
│   ├── FlowConfigRepository.java
│   ├── SysUserRepository.java
│   ├── SysRoleRepository.java
│   ├── SysPermissionRepository.java
│   ├── SysDataScopeRepository.java
│   ├── ArchiveLedgerRepository.java
│   ├── QuickDocumentRepository.java
│   ├── JdbcDocumentSequenceAllocator.java # 单据编号序列分配器
│   └── DocumentSequenceAllocator.java    # 序列分配器接口
├── security/                       # 安全相关
│   ├── AuthenticatedUser.java      # 已认证用户信息
│   ├── JwtService.java             # JWT 生成与验证
│   ├── JwtAuthenticationFilter.java # JWT 认证过滤器
│   ├── DocumentAccessPolicy.java   # 单据访问策略
│   ├── JsonAccessDeniedHandler.java # 403 处理
│   └── JsonAuthenticationEntryPoint.java # 401 处理
├── service/                        # 通用服务
│   ├── DocumentCodeGenerator.java  # 单据编号生成器
│   └── DocumentSequenceAllocator.java # 序列分配器接口
└── workflow/                       # 工作流模块
    ├── OaWorkflowService.java      # 审批工作流服务
    ├── ConfigDrivenProcessDefinitionService.java # 配置驱动流程定义
    ├── FlowConfigManagementService.java # 流程配置管理服务
    ├── FlowConfigController.java   # 流程配置控制器
    ├── FlowConfigItems.java        # 流程配置项 DTO
    ├── OaCcNodeDelegate.java       # 抄送节点委托
    ├── WorkflowHistoryItem.java    # 流程历史项 DTO
    ├── WorkflowNodeStat.java       # 流程节点统计 DTO
    └── WorkflowPort.java           # 流程引擎端口接口
```

---

## 3. 分层架构规范

### 3.1 Controller 层
- **职责**：接收 HTTP 请求、参数校验、调用 Service 层、返回统一响应
- **规范**：
  - 不允许在 Controller 中写业务逻辑
  - 请求参数使用 DTO，且必须使用 `@Valid` 校验
  - 返回类型统一为**显式** `ApiResponse<T>`（签名即文档，保证 Swagger 生成结构与实际响应一致；已移除自动包装 Advice，漏写会直接暴露为非统一结构，Code Review 时注意检查）
  - 类名后缀：`Controller`
  - 路径前缀：`/api/`

### 3.2 Service 层
- **职责**：封装业务逻辑、事务管理、调用 Repository 层
- **规范**：
  - Service 类直接以 `Service` 后缀命名（不强制接口+实现分离）
  - 使用 `@Service` 注解标记
  - 事务方法添加 `@Transactional(rollbackFor = Exception.class)`
  - 类名后缀：`Service`

### 3.3 Repository 层
- **职责**：数据库访问
- **规范**：
  - 使用 Spring Data JPA Repository 接口
  - 复杂查询使用 `@Query` 或 JPA Specifications
  - 类名后缀：`Repository`

### 3.4 Entity 层
- **职责**：数据库表映射
- **规范**：
  - 使用 JPA 注解（`@Entity`、`@Table`、`@Id`、`@Column` 等）
  - 枚举字段使用 `@Enumerated(EnumType.STRING)`
  - 审计字段（`createdAt`、`updatedAt`）使用 `@CreationTimestamp`、`@UpdateTimestamp`
  - 类名与数据库表名对应，表名使用下划线命名（如 `oa_document`）

### 3.5 DTO 层
- **职责**：请求参数封装与响应数据封装
- **规范**：
  - 请求 DTO：放在对应模块包内，以 `Request` 结尾（如 `SubmitDocumentRequest`）
  - 分页查询请求 DTO 继承 `PageParam`（page 从 1 开始、size 默认 10、最大 1000），Controller 用 `@Valid` 校验，分页对象通过 `toPageable(Sort)` 构建（如 `DocumentPageRequest`）
  - 响应 DTO：放在对应模块包内，以 `Response`、`Item`、`Summary`、`Detail` 等结尾
  - 所有字段必须添加中文注释（`/** 字段说明 */`）
  - 使用 Jackson 注解控制序列化（`@JsonProperty`、`@JsonIgnore` 等）

---

## 4. 命名规范

| 类型 | 规则 | 示例 |
|------|------|------|
| Controller | `{业务}Controller` | `DocumentController` |
| Service | `{业务}Service` | `DocumentApplicationService` |
| Repository | `{业务}Repository` | `OaDocumentRepository` |
| Entity | `{业务}（大驼峰，无后缀）` | `OaDocument` |
| 请求 DTO | `{业务}{操作}Request` | `SubmitDocumentRequest` |
| 响应 DTO | `{业务}{视图}` | `DocumentSummary`、`DocumentDetail` |
| 枚举 | `{业务}{属性}枚举语义` | `DocumentStatus`、`ApprovalAction` |
| 配置类 | `{功能}Config` | `SecurityConfig` |
| 切面 | `{功能}Aspect` | `IdempotencyAspect` |
| 异常 | `{业务}Exception` | `BusinessException` |

---

## 5. 代码风格规范

### 5.1 依赖注入
- 使用构造器注入（Lombok `@RequiredArgsConstructor` 或手写构造器）
- **禁止**使用 `@Autowired` 字段注入

### 5.2 异常处理
- 业务异常使用 `BusinessException`，传入错误码和消息
- 全局异常处理器 `GlobalExceptionHandler` 统一处理，返回 `ApiResponse.error()`
- **禁止**在 Controller/Service 中 try-catch 吞掉异常

### 5.3 日志
- 使用 `@Slf4j` 注解（Lombok）
- 关键业务操作记录 `info` 级别
- 异常记录 `error` 级别，包含上下文信息
- 幂等性降级等异常情况记录 `warn` 级别

### 5.4 注释规范
- **类注释**：每个类必须包含中文类说明、作者（可选）、功能描述
- **方法注释**：公共方法必须包含功能说明、参数说明、返回值说明、异常说明
- **字段注释**：DTO 和 Entity 的每个字段必须包含中文注释
- **关键逻辑注释**：复杂业务逻辑必须添加行内注释说明意图

### 5.5 时间处理
- 全系统统一使用 `java.time.LocalDateTime`
- 时区固定为 `Asia/Shanghai`
- API 输入输出使用 ISO8601 格式（`yyyy-MM-dd'T'HH:mm:ss`）
- 日期参数使用 `@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")`

---

## 6. 幂等性规范

### 6.1 使用方式
在写操作接口的 Controller 方法上添加 `@Idempotent` 注解：

```java
@PostMapping
@Idempotent
public ApiResponse<DocumentSummary> submit(@Valid @RequestBody SubmitDocumentRequest request) {
    // ...
}
```

### 6.2 双模式幂等键
| 模式 | 请求头 | 幂等范围 | 缓存时长 |
|------|--------|---------|---------|
| 前端显式 key（推荐） | `Idempotency-Key: {UUID}` | 精确控制 | 10 分钟 |
| 自动 key（零配合） | 无 | 用户+接口+参数 | 5 秒 |

### 6.3 存储策略
- 优先使用 Redis（`RedisIdempotencyStore`），跨实例共享
- Redis 不可用时自动降级为内存实现（`InMemoryIdempotencyStore`）
- 业务执行失败立即释放占位，允许客户端重试

---

## 7. 安全规范

### 7.1 JWT 认证
- Access Token 有效期 4 小时，Refresh Token 有效期 7 天
- 登录接口返回 `accessToken` + `refreshToken`
- 请求头携带：`Authorization: Bearer {accessToken}`

### 7.2 密码安全
- 使用 BCrypt 加密存储
- **禁止**明文存储或传输密码

### 7.3 权限控制
- 使用 Spring Security 进行认证和授权
- 数据范围：`OWN_DOCUMENTS`（本人）、`DEPARTMENT`（本部门）、`ALL`（全部）
- 方法级权限使用 `@PreAuthorize` 或自定义 `DocumentAccessPolicy`

---

## 8. 数据库规范

### 8.1 版本管理
- 使用 Flyway 管理数据库版本
- 迁移脚本放在 `src/main/resources/db/migration/`
- 命名规则：`V{版本号}__{描述}.sql`（如 `V1__init_schema.sql`）

### 8.2 实体映射
- 表名使用下划线命名：`oa_document`、`sys_user`
- 字段名使用下划线命名：`document_status`、`created_at`
- 枚举字段存储字符串值（`@Enumerated(EnumType.STRING)`）

---

## 9. 测试规范

### 9.1 单元测试
- Service 层必须编写单元测试
- 使用 JUnit 5 + Mockito + AssertJ
- 测试覆盖率要求：核心业务逻辑 > 80%

### 9.2 集成测试
- Controller 层编写集成测试
- 使用 `@SpringBootTest` 或 `@DataJpaTest`
- 测试完整的请求流程

### 9.3 测试数据库
- 测试使用 H2 内存数据库
- 测试配置在 `src/test/resources/application.yml`（如需要）

---

## 10. Git 规范

### 10.1 分支管理
- `main`：生产分支
- `develop`：开发分支
- `feature/*`：功能分支
- `hotfix/*`：紧急修复分支

### 10.2 提交规范
- 使用 Conventional Commits 规范
- 格式：`type(scope): description`
- 示例：`feat(document): add document submission API`

---

## 11. 常见问题

### 11.1 幂等性 Redis 不可用
- 系统自动降级为内存实现，不影响业务
- 日志中会出现 `[idempotency] Redis 不可用，降级为内存实现` 的 warn 日志

### 11.2 单据编号生成
- 使用 `DocumentCodeGenerator` 生成唯一编号
- 格式：`OA-{业务类型}-{日期}-{序列号}`
- 序列号通过 `JdbcDocumentSequenceAllocator` 从数据库获取，保证唯一

### 11.3 审批流程配置
- 流程配置通过 `FlowConfig` 实体管理
- 节点类型：`START`、`APPROVE`、`CC`（抄送）、`CONDITION`（条件分支）、`END`
- 条件规则通过 `FlowConditionRule` 配置，支持金额、业务类型等条件

---

**最后更新**: 2026年9月
**版本**: 1.0.0