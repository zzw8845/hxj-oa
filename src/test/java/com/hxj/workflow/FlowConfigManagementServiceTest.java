package com.hxj.workflow;

import com.hxj.enums.AssigneeTypeEnum;
import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.enums.FlowStatusEnum;
import com.hxj.exception.BusinessException;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysRole;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.SysRoleRepository;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 5.11/5.12 流程配置管理（图模型 + 发布两态）：
 * 节点 + 转移边、保存关口校验（协议/拓扑/可达性）、发布部署与版本推进。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flowconfig-oa;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "flowable.database-schema-update=create-drop",
        "flowable.async-executor-activate=false",
        "flowable.async-history-executor-activate=false",
        "flowable.app.enabled=false",
        "flowable.cmmn.enabled=false",
        "flowable.dmn.enabled=false",
        "flowable.idm.enabled=false",
        "flowable.eventregistry.enabled=false"
})
class FlowConfigManagementServiceTest {

    @Autowired private FlowConfigManagementService managementService;
    @Autowired private ConfigDrivenProcessDefinitionService definitionService;
    @Autowired private FlowConfigRepository flowConfigRepository;
    @Autowired private SysRoleRepository sysRoleRepository;
    @Autowired private com.hxj.repository.SysDataScopeRepository dataScopeRepository;
    @Autowired private RepositoryService repositoryService;

    private Long managerRoleId;
    private Long ceoRoleId;
    private Long financeRoleId;

    @BeforeEach
    void setUp() {
        flowConfigRepository.deleteAll();
        // 测试库未跑角色种子，这里补齐节点审批人引用的角色（角色需挂数据范围，列非空）
        sysRoleRepository.deleteAll();
        dataScopeRepository.deleteAll();
        SysDataScope scope = dataScopeRepository.save(new com.hxj.entity.SysDataScope("OWN", "仅本人单据"));
        managerRoleId = ceoRoleId = financeRoleId = null;
        for (String name : List.of("二级部门负责人", "执行总经理", "财务经理", "内控主管", "内控专员")) {
            SysRole role = new SysRole();
            role.setName(name);
            role.setDepartment("财务部");
            role.setPost("审批岗");
            role.setDataScope(scope);
            SysRole saved = sysRoleRepository.save(role);
            switch (name) {
                case "二级部门负责人" -> managerRoleId = saved.getId();
                case "执行总经理" -> ceoRoleId = saved.getId();
                case "财务经理" -> financeRoleId = saved.getId();
            }
        }
    }

    // —— 辅助：新契约（结构化协议 + 转移边）——

    private FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload node(String name, FlowNodeTypeEnum type) {
        return new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(name, type, null, null, null);
    }

    private FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload roleNode(String name, Long roleId) {
        return new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                name, FlowNodeTypeEnum.APPROVAL, AssigneeTypeEnum.ROLE, String.valueOf(roleId), null);
    }

    private FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload typedNode(
            String name, AssigneeTypeEnum assigneeType) {
        return new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                name, FlowNodeTypeEnum.APPROVAL, assigneeType, null, null);
    }

    private FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload edge(String from, String to) {
        return new FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload(from, to, null, null, null);
    }

    private FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload condEdge(
            String from, String to, String var, ConditionOperatorEnum op, String val) {
        return new FlowConfigItems.SaveFlowConfigRequest.FlowTransitionPayload(from, to, var, op, val);
    }

    /** 标准测试流程：发起人 → 主管 → 金额≥2万走执行总经理（否则直走抄送）→ 抄送。 */
    private FlowConfigItems.SaveFlowConfigRequest request() {
        return new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("直属主管", managerRoleId),
                        roleNode("执行总经理审批", ceoRoleId),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "抄送财务", FlowNodeTypeEnum.CC, null, null,
                                "[{\"type\":\"ROLE\",\"value\":\"财务经理\"}]")),
                List.of(
                        edge("发起人", "直属主管"),
                        condEdge("直属主管", "执行总经理审批",
                                "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000"),
                        edge("直属主管", "抄送财务"),
                        edge("执行总经理审批", "抄送财务"),
                        edge("抄送财务", null)));
    }

    // —— 基础 CRUD 与部署 ——

    @Test
    void shouldCreateDraftWithoutDeployThenPublishToDeploy() {
        FlowConfigItems.Config created = managementService.create(request());

        // 保存 = 草稿，不部署（改错配置不污染运行时）
        assertThat(created.status()).isEqualTo(FlowStatusEnum.DRAFT);
        assertThat(created.version()).isZero();
        assertThat(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + created.id()).latestVersion().singleResult())
                .isNull();

        // 发布 = 部署 + 版本推进
        FlowConfigItems.Config published = managementService.publish(created.id());
        assertThat(published.status()).isEqualTo(FlowStatusEnum.PUBLISHED);
        assertThat(published.version()).isEqualTo(1);
        assertThat(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + created.id()).latestVersion().singleResult())
                .isNotNull();
    }

    @Test
    void shouldRevertToDraftOnUpdateAndRequireRepublish() {
        FlowConfigItems.Config created = managementService.publish(managementService.create(request()).id());

        FlowConfigItems.SaveFlowConfigRequest updated = new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("直属主管", managerRoleId),
                        roleNode("财务经理审批", financeRoleId),
                        roleNode("执行总经理审批", ceoRoleId),
                        node("抄送财务", FlowNodeTypeEnum.CC)),
                List.of(
                        edge("发起人", "直属主管"),
                        edge("直属主管", "财务经理审批"),
                        edge("财务经理审批", "执行总经理审批"),
                        edge("执行总经理审批", "抄送财务"),
                        edge("抄送财务", null)));
        FlowConfigItems.Config result = managementService.update(created.id(), updated);

        // 修改后回退草稿（重新发布才能生效）
        assertThat(result.status()).isEqualTo(FlowStatusEnum.DRAFT);
        ProcessDefinition before = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + created.id()).latestVersion().singleResult();
        managementService.publish(created.id());
        ProcessDefinition after = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + created.id()).latestVersion().singleResult();
        assertThat(after.getVersion()).isGreaterThan(before.getVersion());
        assertThat(managementService.detail(created.id()).version()).isEqualTo(2);
    }

    @Test
    void shouldCompileConditionalBranchToExclusiveGateway() {
        FlowConfigItems.Config created = managementService.publish(managementService.create(request()).id());
        BpmnModel model = repositoryService.getBpmnModel(
                repositoryService.createProcessDefinitionQuery()
                        .processDefinitionKey("oa_flow_config_" + created.id()).latestVersion().singleResult().getId());

        // 多出边节点应生成排他网关：直属主管（条件边→执行总经理 + 默认边→抄送）
        long gateways = model.getMainProcess().getFlowElements().stream()
                .filter(ExclusiveGateway.class::isInstance).count();
        assertThat(gateways).isEqualTo(1);
        long taskCount = model.getMainProcess().getFlowElements().stream()
                .filter(element -> element instanceof UserTask || element instanceof ServiceTask)
                .count();
        assertThat(taskCount).isEqualTo(3);
    }

    @Test
    void shouldReturnGraphDataByType() {
        managementService.publish(managementService.create(request()).id());

        FlowConfigItems.Config detail = managementService.detailByType("采购申请");
        assertThat(detail.type()).isEqualTo("采购申请");
        assertThat(detail.nodes()).extracting(FlowConfigItems.NodeConfig::nodeType)
                .containsExactly(FlowNodeTypeEnum.START, FlowNodeTypeEnum.APPROVAL,
                        FlowNodeTypeEnum.APPROVAL, FlowNodeTypeEnum.CC);
        // 候选角色名由角色ID解析展示
        assertThat(detail.nodes().get(1).assigneeRoleNames()).isEqualTo("二级部门负责人");
        assertThat(detail.transitions()).hasSize(5);
        assertThat(detail.transitions().stream().anyMatch(t -> t.variableName() == null))
                .as("默认边（无条件）应存在").isTrue();
    }

    @Test
    void shouldRejectDuplicateType() {
        managementService.create(request());
        assertThatThrownBy(() -> managementService.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    // —— 保存关口：节点协议校验 ——

    @Test
    void shouldValidateApprovalNodeAssigneeType() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "审批节点", FlowNodeTypeEnum.APPROVAL, null, null, null)),
                List.of(edge("发起人", "审批节点")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批人解析方式");
    }

    @Test
    void shouldRejectUnknownAssigneeRoleId() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("直属主管", 99999L)),
                List.of(edge("发起人", "直属主管")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldRejectDynamicAssigneeWithRoleValue() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "费用报销", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, AssigneeTypeEnum.MANAGER, "123", null)),
                List.of(edge("发起人", "直属主管")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不接受角色参数");
    }

    @Test
    void shouldAcceptDynamicAssigneeNodesWithoutRoleValue() {
        FlowConfigItems.SaveFlowConfigRequest valid = new FlowConfigItems.SaveFlowConfigRequest(
                "费用报销", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        typedNode("直属主管", AssigneeTypeEnum.MANAGER),
                        typedNode("会计（按部门）", AssigneeTypeEnum.DEPT_ACCOUNTANT)),
                List.of(
                        edge("发起人", "直属主管"),
                        edge("直属主管", "会计（按部门）"),
                        edge("会计（按部门）", null)));
        assertThatCode(() -> managementService.create(valid)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateNodeNames() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("内控合规", managerRoleId),
                        roleNode("内控合规", ceoRoleId)),
                List.of(
                        edge("发起人", "内控合规"),
                        edge("内控合规", "内控合规")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    // —— 保存关口：转移边拓扑校验 ——

    @Test
    void shouldRejectTransitionWithUnknownNode() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(node("发起人", FlowNodeTypeEnum.START), roleNode("审批", managerRoleId)),
                List.of(edge("发起人", "不存在的节点")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标节点不在节点列表");
    }

    @Test
    void shouldRejectNodeWithoutOutgoingEdge() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(node("发起人", FlowNodeTypeEnum.START), roleNode("审批", managerRoleId)),
                List.of(edge("发起人", "审批"))); // 审批节点无出边 → 悬挂
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少出边");
    }

    @Test
    void shouldRejectConditionalEdgesWithoutDefaultFallback() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("领导审批", ceoRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        // 审批节点只有条件出边、无默认兜底：不满足条件时流程悬挂
                        condEdge("审批", "领导审批",
                                "amount", ConditionOperatorEnum.GREATER_THAN, "100")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无条件兜底出边");
    }

    @Test
    void shouldRejectUnreachableNode() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("孤岛审批", financeRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        edge("审批", null),
                        edge("孤岛审批", "孤岛审批"))); // 孤岛审批自环：无入边、不可达
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可达");
    }

    @Test
    void shouldRejectDeadBranch() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("死胡同", financeRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        edge("审批", "死胡同"),
                        edge("死胡同", "审批"))); // 审批↔死胡同互指成环：每节点都有出边但到不了结束
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法到达流程结束");
    }

    // —— 保存关口：条件变量白名单 / 比较值类型匹配 / 抄送目标 ——

    @Test
    void shouldRejectConditionVariableOutsideWhitelist() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("领导审批", ceoRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        // 变量名打错一个字母：网关求值时流程永久卡死——保存即拒
                        condEdge("审批", "领导审批",
                                "amout", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000"),
                        edge("审批", "领导审批"),
                        edge("领导审批", null)));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条件变量不可用");
    }

    @Test
    void shouldRejectAmountExpectedValueNotNumeric() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("领导审批", ceoRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        condEdge("审批", "领导审批",
                                "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "两万"),
                        edge("审批", "领导审批"),
                        edge("领导审批", null)));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是数字");
    }

    @Test
    void shouldRejectBooleanExpectedValueNotBoolean() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "非标合同审批", FlowCategoryEnum.SEAL,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        roleNode("财务复核", financeRoleId)),
                List.of(
                        edge("发起人", "审批"),
                        condEdge("审批", "财务复核",
                                "involvesFunds", ConditionOperatorEnum.EQUAL, "yes"),
                        edge("审批", "财务复核"),
                        edge("财务复核", null)));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("true / false");
    }

    @Test
    void shouldRejectMalformedCcTargets() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "抄送", FlowNodeTypeEnum.CC, null, null, "[\"财务经理\"]")),
                List.of(
                        edge("发起人", "审批"),
                        edge("审批", "抄送")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("抄送目标格式错误");
    }

    @Test
    void shouldRejectCcTargetRoleNotExists() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "抄送", FlowNodeTypeEnum.CC, null, null,
                                "[{\"type\":\"ROLE\",\"value\":\"不存在的角色\"}]")),
                List.of(
                        edge("发起人", "审批"),
                        edge("审批", "抄送")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("抄送角色不存在");
    }

    @Test
    void shouldAcceptValidCcTargetsToEnd() {
        FlowConfigItems.SaveFlowConfigRequest valid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        node("发起人", FlowNodeTypeEnum.START),
                        roleNode("审批", managerRoleId),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "抄送", FlowNodeTypeEnum.CC, null, null,
                                "[{\"type\":\"ROLE\",\"value\":\"财务经理\"}]")),
                List.of(
                        edge("发起人", "审批"),
                        edge("审批", "抄送"),
                        edge("抄送", null))); // null 目标 = 流程结束
        assertThatCode(() -> managementService.create(valid)).doesNotThrowAnyException();
    }
}
