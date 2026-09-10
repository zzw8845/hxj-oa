package com.hxj.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
import com.hxj.repository.OaDocumentRepository;
import com.hxj.repository.SysDataScopeRepository;
import com.hxj.repository.SysPermissionRepository;
import com.hxj.repository.SysRoleRepository;
import com.hxj.repository.SysUserRepository;
import com.hxj.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HTTP 契约闭环测试：GET 回来的表示，必须能原样 PUT 回去。
 *
 * <p>这是「读写标识不一致」类缺陷的防线。该类缺陷的特点是：Service 层单测全绿
 * （测试直接用 repository 拿到的 id 构造请求），但前端拿着 GET 响应根本拼不出
 * 写接口要求的字段，一调用就 VALIDATION_FAILED。
 *
 * <p>本测试强制约束：<strong>写接口的字段名必须与读接口一致</strong>。
 * 任何「读出来是 code、写进去要 id」的设计都会在此处失败。
 *
 * <p>注意：本项目 HTTP 状态码固定 200，失败靠 {@code success}/{@code code} 表达，
 * 因此断言必须看 {@code success}，不能只看 HTTP 状态。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contract-oa;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.upload-dir=target/test-uploads",
        "flowable.database-schema-update=create-drop",
        "flowable.async-executor-activate=false",
        "flowable.async-history-executor-activate=false",
        "flowable.app.enabled=false",
        "flowable.cmmn.enabled=false",
        "flowable.dmn.enabled=false",
        "flowable.idm.enabled=false",
        "flowable.eventregistry.enabled=false"
})
@AutoConfigureMockMvc
class ApiRoundTripContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysPermissionRepository permissionRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private OaDocumentRepository documentRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void seedAdminAndReferences() {
        // doc_seq 不是 JPA 实体，靠 Flyway 建表；测试环境 flyway 关闭，这里手动补建，
        // 否则提交单据时 JdbcDocumentSequenceAllocator 会因表不存在而报 bad SQL grammar。
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS doc_seq ("
                + "seq_date VARCHAR(8) PRIMARY KEY, seq_value BIGINT NOT NULL DEFAULT 0)");

        // 清理顺序必须遵循外键方向：先删引用方（单据引用用户/流程配置），再删被引用方。
        documentRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        dataScopeRepository.deleteAll();

        SysDataScope scope = dataScopeRepository.save(new SysDataScope("OWN", "仅本人单据"));
        SysPermission view = permissionRepository.save(new SysPermission("VIEW_OWN_FORMS", "查看本人表单"));
        SysPermission submit = permissionRepository.save(new SysPermission("SUBMIT_ALL_FORMS", "提交全部表单"));
        SysPermission configure =
                permissionRepository.save(new SysPermission("CONFIGURE_FLOW_PERMISSION", "配置流程与权限"));

        SysRole role = new SysRole();
        role.setName("超级管理员");
        role.setDepartmentId(com.hxj.support.DictionaryTestSupport
                .ensureDepartment(departmentRepository, "总经办").getId());
        role.setDepartment("总经办");
        role.setPost("系统管理员");
        role.setDataScope(scope);
        role.addPermission(view);
        role.addPermission(submit);
        role.addPermission(configure);
        SysRole savedRole = roleRepository.save(role);

        // 流程节点审批人引用的角色（createFlowConfig 的 payload 用到）
        SysRole approverRole = new SysRole();
        approverRole.setName("二级部门负责人");
        approverRole.setDepartmentId(com.hxj.support.DictionaryTestSupport
                .ensureDepartment(departmentRepository, "各二级部门").getId());
        approverRole.setDepartment("各二级部门");
        approverRole.setPost("部门负责人");
        approverRole.setDataScope(scope);
        roleRepository.save(approverRole);

        SysUser admin = new SysUser();
        admin.setName("林安然");
        admin.setJobNo("HXJ001");
        admin.setAccount("linanran");
        admin.setPassword("not-used");
        com.hxj.support.DictionaryTestSupport.applyDictionary(admin,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, "总经办"),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "系统管理员"));
        admin.setStatus(UserStatusEnum.ACTIVE);
        admin.addRole(savedRole);
        token = jwtService.issueAccessToken(userRepository.save(admin));
    }

    @Test
    void roleResponseShouldBeWritableBackVerbatim() throws Exception {
        JsonNode role = firstElementOf("/api/admin/roles");

        ObjectNode body = mapper.createObjectNode();
        body.set("name", role.get("name"));
        body.set("departmentId", role.get("departmentId"));
        body.set("post", role.get("post"));
        body.set("dataScope", role.get("dataScope"));
        body.set("scopeDepartmentIds", role.get("scopeDepartmentIds"));
        body.set("permissions", role.get("permissions"));

        String response = putJson("/api/admin/roles/" + role.get("id").asLong(), body);

        assertSuccess("角色读回写", response);
    }

    @Test
    void employeeResponseShouldBeWritableBackVerbatim() throws Exception {
        JsonNode employee = firstElementOf("/api/admin/employees");

        ObjectNode body = mapper.createObjectNode();
        body.set("name", employee.get("name"));
        body.set("jobNo", employee.get("jobNo"));
        body.set("departmentId", employee.get("departmentId"));
        body.set("postId", employee.get("postId"));
        body.set("status", employee.get("status"));
        body.set("roles", employee.get("roles"));

        String response = putJson("/api/admin/employees/" + employee.get("id").asLong(), body);

        assertSuccess("员工读回写", response);
    }

    /**
     * 流程配置详情必须能原样回写。
     *
     * <p>列表接口（Brief）只给 {@code nodeNames}，而写接口要完整节点链，二者天然不同构；
     * 因此闭环以<strong>详情接口</strong>为准——这正是前端「编辑流程」时真正读取的接口。
     */
    @Test
    void flowConfigDetailShouldBeWritableBackVerbatim() throws Exception {
        long configId = createFlowConfig("采购申请-流程闭环");
        JsonNode config = dataOf(getJson("/api/flow-configs/" + configId));

        ObjectNode body = mapper.createObjectNode();
        body.set("type", config.get("type"));
        body.set("category", config.get("category"));
        body.set("nodes", config.get("nodes"));
        body.set("conditionRules", config.get("conditionRules"));

        String response = putJson("/api/admin/flow-configs/" + configId, body);

        assertSuccess("流程配置读回写", response);
    }

    /**
     * 提交的单据必须能原样读回：写入字段与详情接口返回的字段一一对应。
     *
     * <p>与角色/员工那种「GET→PUT」不同，单据是一次性提交、不可修改的资源，
     * 因此闭环方向是「POST→GET」：先写后读，验证写入的值没有被吞掉或改名。
     */
    @Test
    void submittedDocumentShouldBeReadBackWithSameFields() throws Exception {
        createFlowConfig("采购申请-单据闭环");

        ObjectNode submit = mapper.createObjectNode();
        submit.put("businessType", "DAILY_PAYMENT");
        submit.put("projectName", "采购申请-单据闭环");
        submit.put("company", "海峡金");
        submit.put("amount", 1200);
        submit.put("reason", "契约测试：验证提交后可原样读回");
        String created = postJson("/api/documents", submit);
        assertSuccess("提交单据", created);

        long documentId = mapper.readTree(created).path("data").path("id").asLong();
        JsonNode detail = dataOf(getJson("/api/documents/" + documentId));

        assertThat(detail.path("projectName").asText()).isEqualTo("采购申请-单据闭环");
        assertThat(detail.path("businessType").asText()).isEqualTo("DAILY_PAYMENT");
        assertThat(new java.math.BigDecimal(detail.path("amount").asText()))
                .as("金额应原样读回，实际详情=%s", detail)
                .isEqualByComparingTo("1200");
    }

    private long createFlowConfig(String type) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("type", type);
        body.put("category", "DAILY");
        ArrayNode nodes = body.putArray("nodes");
        ObjectNode start = nodes.addObject();
        start.put("name", "发起人");
        start.put("nodeType", "START");
        start.put("assigneeRole", "");
        ObjectNode approval = nodes.addObject();
        approval.put("name", "直属主管");
        approval.put("nodeType", "APPROVAL");
        approval.put("assigneeRole", "二级部门负责人");
        body.putArray("conditionRules");

        String response = postJson("/api/admin/flow-configs", body);
        assertSuccess("创建流程配置 " + type, response);
        return mapper.readTree(response).path("data").path("id").asLong();
    }

    private JsonNode firstElementOf(String path) throws Exception {
        String json = getJson(path);
        JsonNode data = mapper.readTree(json).path("data");
        assertThat(data.isArray())
                .as("%s 应返回数组，实际响应=%s", path, json)
                .isTrue();
        assertThat(data.isEmpty())
                .as("%s 应至少有一条数据，实际响应=%s", path, json)
                .isFalse();
        return data.get(0);
    }

    private String putJson(String path, ObjectNode body) throws Exception {
        return mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String postJson(String path, ObjectNode body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String getJson(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode dataOf(String json) throws Exception {
        return mapper.readTree(json).path("data");
    }

    private void assertSuccess(String action, String response) throws Exception {
        assertThat(mapper.readTree(response).path("success").asBoolean())
                .as("%s 应成功，实际响应=%s", action, response)
                .isTrue();
    }
}
