package com.hxj.workflow;

import com.hxj.enums.ConditionOperatorEnum;
import com.hxj.enums.FlowCategoryEnum;
import com.hxj.enums.FlowNodeTypeEnum;
import com.hxj.exception.BusinessException;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysRole;
import com.hxj.repository.FlowConfigRepository;
import com.hxj.repository.SysRoleRepository;
import org.flowable.bpmn.model.BpmnModel;
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

/** 5.11/5.12 流程配置管理：CRUD、修改后重新部署、可视化链条数据。 */
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

    @BeforeEach
    void setUp() {
        flowConfigRepository.deleteAll();
        // 测试库未跑角色种子，这里补齐节点审批人引用的角色（角色需挂数据范围，列非空）
        sysRoleRepository.deleteAll();
        dataScopeRepository.deleteAll();
        SysDataScope scope = dataScopeRepository.save(new com.hxj.entity.SysDataScope("OWN", "仅本人单据"));
        for (String name : List.of("二级部门负责人", "执行总经理", "财务经理", "内控主管", "内控专员")) {
            SysRole role = new SysRole();
            role.setName(name);
            role.setDepartment("财务部");
            role.setPost("审批岗");
            role.setDataScope(scope);
            sysRoleRepository.save(role);
        }
    }

    @Test
    void shouldCreateConfigAndDeployProcessDefinition() {
        FlowConfigItems.Config detail = managementService.create(request());

        assertThat(detail.id()).isNotNull();
        assertThat(detail.nodes()).extracting(FlowConfigItems.NodeConfig::name)
                .containsExactly("发起人", "直属主管", "执行总经理审批", "抄送财务");
        assertThat(detail.conditionRules()).hasSize(1);

        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + detail.id())
                .latestVersion()
                .singleResult();
        assertThat(definition).isNotNull();
    }

    @Test
    void shouldRedeployNewVersionAfterUpdate() {
        FlowConfigItems.Config created = managementService.create(request());

        FlowConfigItems.SaveFlowConfigRequest updated = new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, "二级部门负责人"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "财务经理审批", FlowNodeTypeEnum.APPROVAL, "财务经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "执行总经理审批", FlowNodeTypeEnum.APPROVAL, "执行总经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("抄送财务", FlowNodeTypeEnum.CC, null)),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000", "执行总经理审批")));
        FlowConfigItems.Config result = managementService.update(created.id(), updated);

        assertThat(result.nodes()).extracting(FlowConfigItems.NodeConfig::name)
                .contains("财务经理审批", "执行总经理审批");
        // 修改后部署新版本，新提交单据按新流程流转
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + created.id())
                .latestVersion()
                .singleResult();
        assertThat(definition.getVersion()).isGreaterThanOrEqualTo(2);
        assertThat(definitionService.latestDefinitionId(created.id())).isEqualTo(definition.getId());
    }

    @Test
    void shouldReturnChainDataByType() {
        managementService.create(request());

        FlowConfigItems.Config detail = managementService.detailByType("采购申请");
        assertThat(detail.type()).isEqualTo("采购申请");
        assertThat(detail.nodes()).extracting(FlowConfigItems.NodeConfig::name)
                .containsExactly("发起人", "直属主管", "执行总经理审批", "抄送财务");
        assertThat(detail.nodes()).extracting(FlowConfigItems.NodeConfig::nodeType)
                .containsExactly(FlowNodeTypeEnum.START, FlowNodeTypeEnum.APPROVAL, FlowNodeTypeEnum.APPROVAL, FlowNodeTypeEnum.CC);
    }

    @Test
    void shouldRejectDuplicateType() {
        managementService.create(request());
        assertThatThrownBy(() -> managementService.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void shouldValidateApprovalNodeAssignee() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                        "审批节点", FlowNodeTypeEnum.APPROVAL, null)),
                List.of());
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批角色");
    }

    @Test
    void shouldValidateConditionRuleTarget() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, "二级部门负责人")),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperatorEnum.GREATER_THAN, "100", "不存在的节点")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标节点");
    }

    @Test
    void shouldRejectUnknownAssigneeRole() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, "不存在的角色")),
                List.of());
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldAcceptDynamicAssigneeNodesWithoutRoleExistence() {
        FlowConfigItems.SaveFlowConfigRequest valid = new FlowConfigItems.SaveFlowConfigRequest(
                "费用报销", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, "直属主管"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "会计（按部门）", FlowNodeTypeEnum.APPROVAL, "会计（按部门）")),
                List.of());
        assertThatCode(() -> managementService.create(valid)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateNodeNames() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "内控合规", FlowNodeTypeEnum.APPROVAL, "内控主管"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "内控合规", FlowNodeTypeEnum.APPROVAL, "内控专员")),
                List.of());
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void shouldBuildFlowElementForEveryNode() {
        FlowConfigItems.Config detail = managementService.create(request());
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("oa_flow_config_" + detail.id())
                .latestVersion()
                .singleResult();

        // 直接校验已部署的 BPMN：每个节点都应生成对应流程元素，防止重名节点被静默覆盖丢失
        BpmnModel model = repositoryService.getBpmnModel(definition.getId());
        long taskCount = model.getMainProcess().getFlowElements().stream()
                .filter(element -> element instanceof UserTask || element instanceof ServiceTask)
                .count();

        // request() 共 4 个节点，发起人(START) 由 buildModel 内置，其余 3 个必须各自生成流程元素
        assertThat(taskCount).isEqualTo(3);
    }

    private FlowConfigItems.SaveFlowConfigRequest request() {
        return new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategoryEnum.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeTypeEnum.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeTypeEnum.APPROVAL, "二级部门负责人"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "执行总经理审批", FlowNodeTypeEnum.APPROVAL, "执行总经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("抄送财务", FlowNodeTypeEnum.CC, null)),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperatorEnum.GREATER_THAN_OR_EQUAL, "20000", "执行总经理审批")));
    }
}