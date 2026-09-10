package com.hxj.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.enums.UserStatusEnum;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 全端点冒烟测试：保证每个已发布的接口都「可达、可鉴权、可被统一响应封装」。
 *
 * <p>端点清单不硬编码，而是从运行时 {@code /v3/api-docs} 动态读取，因此新增 Controller
 * 后无需改测试即可自动纳入覆盖——避免「新接口忘了补测试」。
 *
 * <p>验证三件事（都是历史上真实踩过的坑）：
 * <ol>
 *   <li><strong>路径可达</strong>：404/405 会体现为 HTTP 状态码非 200（本项目约定 HTTP 恒定 200）；</li>
 *   <li><strong>未被统一封装遗漏</strong>：响应体必须能解析出 {@code success}/{@code code}；</li>
 *   <li><strong>没有服务端异常</strong>：{@code INTERNAL_ERROR} 说明序列化/绑定/空指针等真实缺陷。
 *       业务错误码（如 VALIDATION_FAILED、DOCUMENT_NOT_FOUND）是<em>预期</em>结果，不算失败。</li>
 * </ol>
 *
 * <p>写接口统一传空对象 {@code {}}，目的是探测端点存在性与绑定层是否工作，
 * 不追求业务成功——业务正确性由各 Service 单测与契约闭环测试负责。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke-oa;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
class ApiEndpointSmokeTest {

    /**
     * 不适合空 JSON 探测的端点。
     *
     * <p>{@code /api/auth/logout} 会把当前 token 写入 Redis 黑名单，
     * 一旦探测它，后续所有端点的鉴权都会 401，因此必须排除。
     * 其余为 multipart/文件下载端点，需要真实文件输入，另行覆盖。
     */
    private static final List<String> SKIPPED = List.of(
            "POST /api/auth/logout",
            "POST /api/documents/{id}/attachments",
            "POST /api/documents/{documentId}/actions/supplement-materials",
            "POST /api/documents/{documentId}/actions/stamped-file",
            "GET /api/documents/attachments/{attachmentId}"
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private SysUserRepository userRepository;
    @Autowired private SysRoleRepository roleRepository;
    @Autowired private SysPermissionRepository permissionRepository;
    @Autowired private SysDataScopeRepository dataScopeRepository;
    @Autowired private com.hxj.repository.SysDepartmentRepository departmentRepository;
    @Autowired private com.hxj.repository.SysPostRepository postRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private String token;

    @BeforeEach
    void seedAdminWithFullPermissions() {
        userRepository.deleteAll();
        roleRepository.deleteAll();
        permissionRepository.deleteAll();
        dataScopeRepository.deleteAll();

        SysDataScope scope = dataScopeRepository.save(new SysDataScope("ALL", "全部"));

        SysRole role = new SysRole();
        role.setName("冒烟测试管理员");
        role.setDepartment("总经办");
        role.setPost("系统管理员");
        role.setDataScope(scope);
        for (String code : List.of("VIEW_ALL_FORMS", "VIEW_OWN_FORMS", "SUBMIT_ALL_FORMS",
                "APPROVE_ALL_NODES", "UPLOAD_APPROVAL_EVIDENCE", "CONFIGURE_FLOW_PERMISSION")) {
            role.addPermission(permissionRepository.save(new SysPermission(code, code)));
        }
        SysRole savedRole = roleRepository.save(role);

        SysUser admin = new SysUser();
        admin.setName("冒烟管理员");
        admin.setJobNo("SMOKE001");
        admin.setAccount("smoke-admin");
        admin.setPassword("not-used");
        com.hxj.support.DictionaryTestSupport.applyDictionary(admin,
                com.hxj.support.DictionaryTestSupport.ensureDepartment(departmentRepository, "总经办"),
                com.hxj.support.DictionaryTestSupport.ensurePost(postRepository, "系统管理员"));
        admin.setStatus(UserStatusEnum.ACTIVE);
        admin.addRole(savedRole);
        token = jwtService.issueAccessToken(userRepository.save(admin));
    }

    @Test
    void everyPublishedEndpointShouldBeReachableAndWrapped() throws Exception {
        List<String> endpoints = publishedEndpoints();
        assertThat(endpoints).as("至少应发现一个 /api 端点").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (String endpoint : endpoints) {
            if (SKIPPED.contains(endpoint)) {
                continue;
            }
            String problem = probe(endpoint);
            if (problem != null) {
                failures.add(endpoint + " → " + problem);
            }
        }

        assertThat(failures)
                .as("以下端点冒烟失败（共探测 %d 个）：\n%s", endpoints.size(), String.join("\n", failures))
                .isEmpty();
    }

    /** 探测单个端点，返回问题描述；通过则返回 null。 */
    private String probe(String endpoint) throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String method = parts[0];
        String path = parts[1].replaceAll("\\{[^}]+}", "1");

        MvcResult result;
        switch (method) {
            case "GET" -> result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
            case "POST" -> result = mockMvc.perform(post(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andReturn();
            case "PUT" -> result = mockMvc.perform(put(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andReturn();
            default -> throw new IllegalStateException("未处理的 HTTP 方法: " + method);
        }

        int status = result.getResponse().getStatus();
        if (status != 200) {
            return "HTTP " + status + "（本项目约定 HTTP 恒定 200，非 200 说明路径不存在或方法不匹配）";
        }

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String contentType = result.getResponse().getContentType();
        if (contentType == null || !contentType.contains("json") || body.isBlank()) {
            return null; // 文件下载等非 JSON 响应，只要 200 即可
        }

        JsonNode root = mapper.readTree(body);
        if (!root.has("success") || !root.has("code")) {
            return "响应未经 ApiResponse 统一封装: " + body;
        }
        String code = root.path("code").asText();
        if ("INTERNAL_ERROR".equals(code)) {
            return "服务端异常: " + root.path("message").asText();
        }
        return null;
    }

    private List<String> publishedEndpoints() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = mapper.readTree(json).path("paths");

        List<String> endpoints = new ArrayList<>();
        paths.fieldNames().forEachRemaining(path -> {
            if (!path.startsWith("/api/")) {
                return;
            }
            JsonNode methods = paths.get(path);
            methods.fieldNames().forEachRemaining(method -> {
                if (List.of("get", "post", "put").contains(method)) {
                    endpoints.add(method.toUpperCase() + " " + path);
                }
            });
        });
        return endpoints;
    }
}
