package com.hxj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 文档契约测试：直接请求运行时 {@code /v3/api-docs}，断言关键 DTO 字段带有中文描述。
 *
 * <p>防止两类回退：
 * <ol>
 *   <li>record 的字段只写了 Javadoc 却漏掉 {@code @Schema(description=...)} —— Swagger 不读 Javadoc，
 *       只有 {@code @Schema} 会进入文档；</li>
 *   <li>有人重新提交手搓的、description 全空的 {@code openapi.json} 覆盖真实文档。</li>
 * </ol>
 * 这是「Apifox 看不到请求参数中文注释」的根因防线：请求/响应 DTO 必须带 {@code @Schema}。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keyRequestAndResponseSchemasCarryChineseDescriptions() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode schemas = mapper.readTree(json).path("components").path("schemas");

        System.out.println("EMPLOYEE_SCHEMA=" + schemas.path("EmployeeResponse").toPrettyString());

        assertDescription(schemas, "EmployeeResponse", "id", "用户ID");
        assertDescription(schemas, "RoleResponse", "permissions", "权限编码列表");
        assertDescription(schemas, "CreateEmployeeRequest", "account", "登录账号");
        assertDescription(schemas, "ArchiveLedgerItemResponse", "amount", "金额");
        assertDescription(schemas, "ApprovalHistoryItemResponse", "operator", "操作人");
        // 泛型 PageResponse<T> 的裸 schema 在 SpringDoc 下字段描述可能丢失，
        // 验证具体引用实例 PageResponseArchiveLedgerItem（字段描述来源相同）即可
        assertDescription(schemas, "PageResponseArchiveLedgerItemResponse", "totalElements", "总记录数");
        assertDescription(schemas, "Badge", "pendingApprovalCount", "待审批单据数");
        // record 分页请求：证明「保持 record」后文档注释依然完整
        assertDescription(schemas, "DocumentPageRequest", "size", "每页条数");
    }

    private void assertDescription(JsonNode schemas, String schemaName, String field, String expected) {
        JsonNode desc = schemas.path(schemaName).path("properties").path(field).path("description");
        assertThat(desc.isMissingNode())
                .as("schema %s 字段 %s 缺少 description", schemaName, field)
                .isFalse();
        assertThat(desc.asText())
                .as("schema %s 字段 %s 的 description", schemaName, field)
                .contains(expected);
    }
}
