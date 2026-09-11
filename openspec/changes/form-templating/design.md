## Context

现有提交链路：固定 DTO → 硬编码 validate()（用印五件套/付款必填）→ 散列列存储。
流程条件依赖 4 个引擎变量（amount/involvesFunds/requiresAdminReview/businessMode）。
复审暗雷：抄送别称表、附件要求关键词、分类关键词、按名字查流程（详见方案文档第五·五节）。

## Goals / Non-Goals

- Goals: 字段定义元数据化；提交校验模板驱动；模板版本快照隔离在途；四暗雷退役；零发版新增单据
- Non-Goals: 拖拽画布设计器；明细子表以外的钉钉深水区（审批人绑定表单控件等）；扩展字段进列表搜索；字段→条件的通用提升（仅保留键提升）

## Decisions

1. 系统属性/提升字段边界：申请人/部门/状态/流程/单号/时间不进表单；amount/title 保留专列；
   保留键 involvesFunds/requiresAdminReview/businessMode 以 BOOLEAN/SELECT 控件存在于模板，
   提交时提升为流程变量（值同时留 JSON）。保留键不可删除、可排序。
2. 表单与流程绑定：form_template.flow_config_id 显式 1:1（管理端选择）；提交携带 templateId，
   后端经模板取流程，FlowConfig.type 加唯一约束（显示名语义）。
3. 版本快照：文档存 form_version + form_snapshot（提交时字段定义数组）+ field_values（值 JSON）。
   详情 = snapshot ∪ values 合并渲染，模板后续变更不影响历史单据。
4. 控件类型 9 种：TEXT/TEXTAREA/NUMBER/DATE/SELECT/BOOLEAN/IMAGE/ATTACHMENT/TABLE。
   TABLE 的 options 为列结构 [{key,label,type}]，值为行数组，校验递归单元格。
   IMAGE/ATTACHMENT 值为附件 ID 数组，oa_attachment 新增 field_key 列关联字段。
5. 暗雷退役：抄送目标结构化（FlowNodeConfig.cc_targets：角色/部门/人员 JSON）；
   附件要求=模板字段 required 附件类型+流程级前置材料清单（form_template.attachment_requirements）；
   DocumentClassifier 删除；BusinessTypeEnum 仅作过渡，文档 business_type 改存模板 type_key。
6. 单号前缀随模板：form_template.doc_prefix；DocumentCodeGenerator.generate(prefix)。
7. 校验转译对照（重写唯一风险点）：用印五件套必填→模板 required；付款必填 company/amount/reason
   → company 为模板 SELECT 字段、amount 提升字段、reason 模板 TEXTAREA；风险阈值逻辑保留（amount 列）。

## Risks / Trade-offs

- JSON 值不可 SQL 直查/排序——列表仅提升列可筛（自用可接受，已在方案文档记录）
- 模板编辑错误直接影响提交——管理端做保留键锁定 + field_key 唯一校验 + 预览
- 提交链路重写为最大风险点——测试先行（校验四场景/提升提取/快照冻结各成用例）
