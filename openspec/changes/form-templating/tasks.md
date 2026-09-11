## 1. 数据模型与迁移（M1）
- [x] 1.1 V27 迁移：form_template/form_field 两表；oa_document 加 form_template_id/form_version/form_snapshot/field_values，删除散列列；quick_document DROP；FlowConfig.type 唯一约束
- [x] 1.2 预置"通用模板"（现有全部字段的等价定义，含保留键）与 doc_prefix
- [x] 1.3 FormTemplate/FormField 实体与仓储，验证 JPA 测试通过

## 2. 模板管理后端（M1）
- [x] 2.1 FormTemplateManagementService：模板 CRUD/字段编辑/版本递增/保留键锁定/field_key 唯一与保留字校验/SELECT options 校验
- [x] 2.2 端点：/api/admin/form-templates CRUD（配置权限）+ /api/form-templates/enabled 与 by-type（登录即可）
- [x] 2.3 模板与流程绑定：flow_config_id 显式关联，管理端选择
- [x] 2.4 测试：CRUD/保留键/版本/唯一性

## 3. 提交链路重写（M2）
- [ ] 3.1 SubmitDocumentRequest v2（projectName/company/needPostMaterial/ccUserIds/templateId/fieldValues）
- [ ] 3.2 模板驱动校验（必填/类型强转/SELECT 选项/未知 key 拒绝）+ 附件要求按模板
- [ ] 3.3 提升键提取（amount 列+风险标记；involvesFunds/requiresAdminReview/businessMode → 流程变量）+ form_snapshot 冻结
- [ ] 3.4 DocumentCodeGenerator 前缀参数化
- [ ] 3.5 暗雷退役：DocumentClassifier 删除；AttachmentRequirementService 改模板驱动；抄送目标结构化（cc_targets）
- [ ] 3.6 详情响应：snapshot ∪ values 合并数组；测试：校验四场景/提升提取/快照冻结/明细表递归校验

## 4. 前端动态化（M3）
- [ ] 4.1 提交表单按模板渲染 9 种控件 + fieldValues 提交
- [ ] 4.2 详情按合并数组渲染；驳回重提回填
- [ ] 4.3 快捷菜单改由 enabled 模板派生（quick_document 界面退役）

## 5. 模板编辑器（M4）
- [ ] 5.1 模板列表/字段编辑器（增删改排序/启停/保留字段锁定）
- [ ] 5.2 流程绑定选择器

## 6. 收尾（M5）
- [ ] 6.1 全量回归 146+ 用例；契约/架构测试更新
- [ ] 6.2 文档同步（权限模型设计说明/表单模板化方案勾欠账）
