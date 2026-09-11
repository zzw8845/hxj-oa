package com.hxj.common;

/**
 * 全局业务错误码枚举：前端契约的单一出处（阿里 Java 开发手册风格）。
 *
 * <p>每个枚举项绑定：<b>字符串业务码</b>（{@link #getCode()}，枚举名）+
 * <b>默认提示</b>（{@link #getDefaultMessage()}）。
 *
 * <p>HTTP 响应状态码统一为 200，前端靠 {@code success}/{@code code} 字段分流。
 *
 * <p>命名规范：大写下划线，{@code 资源_原因}（如 DOCUMENT_NOT_FOUND）。
 */
public enum ErrorCodeEnum {

    // —— 通用 ——
    /** 未指定具体码的业务异常兜底 */
    BUSINESS_ERROR("业务异常"),
    /** 幂等拦截：重复请求 */
    DUPLICATE_REQUEST("重复请求"),
    /** 参数校验失败（@Valid/@RequestParam 约束） */
    VALIDATION_FAILED("参数校验失败"),
    /** 权限不足（方法级 @PreAuthorize 校验失败） */
    ACCESS_DENIED("无权执行此操作"),
    /** 请求体 JSON 格式错误 */
    REQUEST_BODY_INVALID("请求体格式不正确"),
    /** 路径/查询参数类型不匹配 */
    ARGUMENT_TYPE_INVALID("参数类型不正确"),
    /** 缺少必要请求头 */
    MISSING_REQUEST_HEADER("缺少必要请求头"),
    /** 请求路径不存在 */
    RESOURCE_NOT_FOUND("请求的资源不存在"),
    /** HTTP 方法不支持 */
    METHOD_NOT_ALLOWED("不支持的请求方法"),
    /** 未捕获异常兜底 */
    INTERNAL_ERROR("系统繁忙，请稍后重试"),

    // —— 认证（AuthException 使用）——
    /** 账号或密码错误 / Refresh Token 无效 */
    AUTH_FAILED("请先登录或重新登录"),
    /** 账号已停用或离职 */
    ACCOUNT_DISABLED("账号已停用，请联系管理员"),
    /** Access Token 过期 */
    TOKEN_EXPIRED("登录已过期，请重新登录"),

    // —— 单据 ——
    /** 单据不存在或无权查看 */
    DOCUMENT_NOT_FOUND("单据不存在或无权查看"),
    /** 前置单据不存在 */
    LINKED_DOCUMENT_NOT_FOUND("前置单据不存在"),
    /** 仅可关联已审批通过单据 */
    LINKED_DOCUMENT_NOT_APPROVED("仅可关联已审批通过的单据"),
    /** 附件不存在 */
    ATTACHMENT_NOT_FOUND("附件不存在"),
    /** 请完整填写用印申请信息 */
    SEAL_INFO_INCOMPLETE("请完整填写用印申请信息"),
    /** 请完整填写付款申请信息 */
    PAYMENT_INFO_INCOMPLETE("请完整填写付款申请信息"),
    /** 快捷单据条目不存在 */
    QUICK_DOCUMENT_NOT_FOUND("快捷单据不存在"),
    /** 同业务类型下快捷单据名称已存在 */
    QUICK_DOCUMENT_EXISTS("该业务类型下单据名称已存在"),

    // —— 审批操作 ——
    /** 请上传当前节点凭证 */
    EVIDENCE_REQUIRED("请上传当前节点凭证"),
    /** 审批凭证不存在 */
    EVIDENCE_NOT_FOUND("审批凭证不存在"),
    /** 请选择驳回层级 */
    REJECT_TARGET_REQUIRED("请选择驳回层级"),
    /** 驳回原因不能为空 */
    REJECT_REASON_REQUIRED("驳回原因不能为空"),
    /** 驳回层级不在该单据的流程节点中 */
    REJECT_TARGET_INVALID("驳回层级不在该单据的流程节点中"),
    /** 请完整填写补充材料信息 */
    SUPPLEMENT_INFO_REQUIRED("请完整填写补充材料信息"),
    /** 请上传补充材料文件 */
    MATERIAL_FILE_REQUIRED("请上传补充材料文件"),
    /** 加签人员和原因不能为空 */
    SIGN_INFO_REQUIRED("加签人员和原因不能为空"),
    /** 加签人员不存在 */
    SIGN_USER_NOT_FOUND("加签人员不存在"),
    /** 请填写加签意见 */
    SIGN_COMMENT_REQUIRED("请填写加签意见"),
    /** 当前用户没有该单据的加签任务 */
    SIGN_TASK_NOT_FOUND("当前用户没有该单据的加签任务"),
    /** 请上传盖章文件 */
    STAMPED_FILE_REQUIRED("请上传盖章文件"),
    /** 仅用印申请可回传盖章文件 */
    NOT_SEAL_APPLICATION("仅用印申请可回传盖章文件"),
    /** 当前用户不是该单据的审批人 */
    APPROVAL_NOT_ALLOWED("当前用户不是该单据的审批人"),

    // —— 人员与角色 ——
    /** 抄送人员不存在 */
    CC_USER_NOT_FOUND("抄送人员不存在"),
    /** 当前用户/员工不存在 */
    USER_NOT_FOUND("用户不存在"),
    /** 登录账号已存在 */
    ACCOUNT_EXISTS("登录账号已存在"),
    /** 员工工号已存在 */
    JOB_NO_EXISTS("员工工号已存在"),
    /** 请完整填写员工、账号和密码信息 */
    EMPLOYEE_ACCOUNT_INCOMPLETE("请完整填写员工、账号和密码信息"),
    /** 角色/分配角色不存在 */
    ROLE_NOT_FOUND("角色不存在"),
    /** 角色名称已存在 */
    ROLE_EXISTS("角色名称已存在"),
    /** 角色下存在员工，无法删除 */
    ROLE_HAS_MEMBERS("角色下存在员工，无法删除"),
    /** 角色被流程节点引用，无法删除 */
    ROLE_IN_FLOW_USE("角色被流程节点引用，无法删除"),
    /** 角色被抄送记录引用，无法删除 */
    ROLE_IN_CC_USE("角色被抄送记录引用，无法删除"),
    /** 仅申请人本人可撤回单据 */
    DOCUMENT_WITHDRAW_FORBIDDEN("仅申请人本人可撤回单据"),
    /** 审批已开始，无法撤回 */
    DOCUMENT_WITHDRAW_STARTED("审批已开始，无法撤回"),
    /** 单据已办结或已作废，不可再作废 */
    DOCUMENT_VOID_STATE_INVALID("单据已办结或已作废，不可再作废"),
    /** 请填写作废原因 */
    VOID_REASON_REQUIRED("请填写作废原因"),
    /** 数据范围不存在 */
    DATA_SCOPE_NOT_FOUND("数据范围不存在"),
    /** 权限点不存在 */
    PERMISSION_NOT_FOUND("权限点不存在"),
    /** 部门不存在 */
    DEPARTMENT_NOT_FOUND("部门不存在"),
    /** 部门名称已存在 */
    DEPARTMENT_NAME_EXISTS("部门名称已存在"),
    /** 存在下级部门，无法删除 */
    DEPARTMENT_HAS_CHILDREN("存在下级部门，无法删除"),
    /** 部门下存在员工，无法删除 */
    DEPARTMENT_HAS_EMPLOYEES("部门下存在员工，无法删除"),
    /** 部门被角色引用，无法删除 */
    DEPARTMENT_HAS_ROLES("部门被角色引用，无法删除"),
    /** 部门被自定义数据范围引用，无法删除 */
    DEPARTMENT_IN_SCOPE_USE("部门被自定义数据范围引用，无法删除"),
    /** 部门有兼职员工，无法删除 */
    DEPARTMENT_HAS_SECONDARY("部门有兼职员工，无法删除"),
    /** 不能将部门移动到自身或其下级部门下 */
    DEPARTMENT_MOVE_CYCLE("不能将部门移动到自身或其下级部门下"),
    /** 岗位不存在 */
    POST_NOT_FOUND("岗位不存在"),
    /** 岗位名称已存在 */
    POST_NAME_EXISTS("岗位名称已存在"),
    /** 岗位下存在员工，无法删除 */
    POST_HAS_EMPLOYEES("岗位下存在员工，无法删除"),
    /** 岗位不属于该员工所在部门 */
    POST_DEPARTMENT_MISMATCH("岗位不属于该员工所在部门"),
    /** 部门下存在岗位，无法删除 */
    DEPARTMENT_HAS_POSTS("部门下存在岗位，无法删除"),
    /** 直属主管不存在 */
    MANAGER_NOT_FOUND("直属主管不存在"),
    /** 直属主管不能是自己 */
    MANAGER_SELF_REFERENCE("直属主管不能是自己"),
    /** 员工有在途待办，须先完成离职交接 */
    EMPLOYEE_PENDING_TASKS("员工有在途待办，请先完成转交或退回"),

    // —— 流程配置 ——
    /** 流程配置不存在 / 未配置对应审批流程 */
    FLOW_CONFIG_NOT_FOUND("未配置对应审批流程"),
    FORM_TEMPLATE_NOT_FOUND("表单模板不存在"),
    FORM_FIELD_INVALID("表单字段校验失败"),
    FLOW_CONFIG_IN_USE("流程配置已被单据引用，无法删除"),
    /** 同名流程配置已存在 */
    FLOW_CONFIG_EXISTS("同名流程配置已存在"),
    /** 流程类型与分类不能为空 */
    FLOW_CONFIG_INFO_REQUIRED("流程类型与分类不能为空"),
    /** 流程节点链不能为空 */
    FLOW_NODES_REQUIRED("流程节点链不能为空"),
    /** 流程至少需要一个审批节点 */
    FLOW_APPROVAL_NODE_REQUIRED("流程至少需要一个审批节点"),
    /** 节点名称与类型不能为空 */
    FLOW_NODE_INVALID("节点名称与类型不能为空"),
    /** 流程节点名称重复（重名会导致节点在 BPMN 编译期被静默覆盖丢失） */
    FLOW_NODE_DUPLICATE("流程节点名称不能重复"),
    /** 审批节点需指定审批角色 */
    FLOW_ASSIGNEE_REQUIRED("审批节点需指定审批角色"),
    /** 条件分支规则不完整 */
    FLOW_RULE_INVALID("条件分支规则不完整"),
    /** 条件规则目标节点不在节点链中 */
    FLOW_RULE_TARGET_INVALID("条件规则目标节点不在节点链中"),

    // —— 文件 ——
    /** 上传文件不能为空 */
    EMPTY_FILE("上传文件不能为空"),
    /** 文件名不合法 */
    INVALID_FILE_NAME("文件名不合法"),
    /** 文件路径不合法 */
    INVALID_FILE_PATH("文件路径不合法"),
    /** 文件保存失败 */
    FILE_STORE_FAILED("文件保存失败"),
    /** 文件不存在 */
    FILE_NOT_FOUND("文件不存在"),
    /** 文件读取失败 */
    FILE_READ_FAILED("文件读取失败");

    /** 默认错误提示（面向用户） */
    private final String defaultMessage;

    ErrorCodeEnum(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    /** 字符串业务码：与枚举名一致，用于响应体 code 字段 */
    public String getCode() {
        return name();
    }

    /** 默认错误提示 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}