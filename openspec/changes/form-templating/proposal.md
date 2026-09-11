## Why

单据表单目前硬编码在 SubmitDocumentRequest（固定字段）与 validate() 中，新增/修改表单字段必须发版；
复审另发现四处字符串解析暗雷（抄送别称表、附件要求关键词、业务分类关键词、流程按名字查找）。
业务预期表单与流程会持续变更/新增，需要元数据驱动的表单模板体系。
当前零存量单据数据（单据/附件/审批记录/抄送/流程配置全部为 0），是终态直上的唯一零成本窗口。

## What Changes

- 新增 form_template / form_field：按业务类型定义字段清单，9 种控件（TEXT/TEXTAREA/NUMBER/DATE/SELECT/BOOLEAN/IMAGE/ATTACHMENT/TABLE 明细）
- OaDocument 重构：新增 form_template_id/form_version/form_snapshot/field_values；删除散列列（发票摘要/合同号/用印五件套等）
- 提交链路按模板驱动校验；提升字段 amount/title 保留专列（风险标记/列表/条件变量），involvesFunds/requiresAdminReview/businessMode 作为保留键进流程变量
- 四颗暗雷退役：①抄送目标结构化（FlowNodeConfig 新增抄送目标，ROLE_ALIASES 删除）②附件要求进模板 ③DocumentClassifier 退役（提交显式选模板携带 templateId）④FlowConfig.type 唯一约束、提交按 templateId 关联流程
- quick_document 退役：display_name/sort_order 并入 form_template，快捷菜单由 enabled 模板派生
- DocumentCodeGenerator 前缀参数化（单号前缀随模板走）；单号序列机制不变
- 普通员工可读字段定义端点（渲染提交表单用）

## Impact

- 代码：后端提交链路重写 + 模板管理服务/端点；前端对接版提交表单动态化 + 模板编辑器
- 数据：V27 迁移（2 新表 + oa_document 重构 + quick_document 退役）；零存量数据无兼容层
- 契约保持：统一响应、权限守卫、流程条件变量名不变；Flowable 零改动
