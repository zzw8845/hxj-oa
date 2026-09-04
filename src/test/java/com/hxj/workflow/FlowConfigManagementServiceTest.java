package com.hxj.workflow;

import com.hxj.entity.ConditionOperator;
import com.hxj.entity.FlowCategory;
import com.hxj.entity.FlowNodeType;
import com.hxj.exception.BusinessException;
import com.hxj.repository.FlowConfigRepository;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired private RepositoryService repositoryService;

    @BeforeEach
    void setUp() {
        flowConfigRepository.deleteAll();
    }

    @Test
    void shouldCreateConfigAndDeployProcessDefinition() {
        FlowConfigItems.FlowConfigDetail detail = managementService.create(request());

        assertThat(detail.id()).isNotNull();
        assertThat(detail.nodes()).extracting(FlowConfigItems.FlowNodeItem::name)
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
        FlowConfigItems.FlowConfigDetail created = managementService.create(request());

        FlowConfigItems.SaveFlowConfigRequest updated = new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategory.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeType.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeType.APPROVAL, "二级部门负责人"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "财务经理审批", FlowNodeType.APPROVAL, "财务经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "执行总经理审批", FlowNodeType.APPROVAL, "执行总经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("抄送财务", FlowNodeType.CC, null)),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperator.GREATER_THAN_OR_EQUAL, "20000", "执行总经理审批")));
        FlowConfigItems.FlowConfigDetail result = managementService.update(created.id(), updated);

        assertThat(result.nodes()).extracting(FlowConfigItems.FlowNodeItem::name)
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

        FlowConfigItems.FlowConfigDetail detail = managementService.detailByType("采购申请");
        assertThat(detail.type()).isEqualTo("采购申请");
        assertThat(detail.nodes()).extracting(FlowConfigItems.FlowNodeItem::name)
                .containsExactly("发起人", "直属主管", "执行总经理审批", "抄送财务");
        assertThat(detail.nodes()).extracting(FlowConfigItems.FlowNodeItem::nodeType)
                .containsExactly(FlowNodeType.START, FlowNodeType.APPROVAL, FlowNodeType.APPROVAL, FlowNodeType.CC);
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
                "借款申请", FlowCategory.DAILY,
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                        "审批节点", FlowNodeType.APPROVAL, null)),
                List.of());
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批角色");
    }

    @Test
    void shouldValidateConditionRuleTarget() {
        FlowConfigItems.SaveFlowConfigRequest invalid = new FlowConfigItems.SaveFlowConfigRequest(
                "借款申请", FlowCategory.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeType.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeType.APPROVAL, "二级部门负责人")),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperator.GREATER_THAN, "100", "不存在的节点")));
        assertThatThrownBy(() -> managementService.create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标节点");
    }

    private FlowConfigItems.SaveFlowConfigRequest request() {
        return new FlowConfigItems.SaveFlowConfigRequest(
                "采购申请", FlowCategory.DAILY,
                List.of(
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("发起人", FlowNodeType.START, null),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "直属主管", FlowNodeType.APPROVAL, "二级部门负责人"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload(
                                "执行总经理审批", FlowNodeType.APPROVAL, "执行总经理"),
                        new FlowConfigItems.SaveFlowConfigRequest.FlowNodePayload("抄送财务", FlowNodeType.CC, null)),
                List.of(new FlowConfigItems.SaveFlowConfigRequest.FlowConditionRulePayload(
                        "amount", ConditionOperator.GREATER_THAN_OR_EQUAL, "20000", "执行总经理审批")));
    }
}