package com.hxj.document;

import com.hxj.common.ApiResponse;
import com.hxj.config.MethodSecurityConfig;
import com.hxj.config.SecurityBeansConfig;
import com.hxj.config.SecurityConfig;
import com.hxj.config.TimeConfig;
import com.hxj.entity.SysDataScope;
import com.hxj.entity.SysPermission;
import com.hxj.entity.SysRole;
import com.hxj.entity.SysUser;
import com.hxj.exception.GlobalExceptionHandler;
import com.hxj.security.JwtAuthenticationFilter;
import com.hxj.security.JwtService;
import com.hxj.security.JsonAccessDeniedHandler;
import com.hxj.security.JsonAuthenticationEntryPoint;
import com.hxj.security.SecurityResponseWriter;
import com.hxj.security.TokenBlacklistService;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分页查询请求绑定契约测试。
 *
 * <p>验证 {@link DocumentPageRequest} 由可变 class 改为 record 后：
 * <ol>
 *   <li>Spring MVC 仍能按扁平参数名（{@code page=1&size=10&keyword=x}）完成构造器绑定；</li>
 *   <li>record 组件上的 Bean Validation 约束仍然生效；</li>
 *   <li>未传的分页参数在紧凑构造器中补默认值，不会把 null 传进 {@code PageRequest}；</li>
 *   <li>非法值被拒绝而不是被静默纠正。</li>
 * </ol>
 * 这四条是「分页请求 DTO 能否保持 record」的前提，改动分页请求时必须保证本测试通过。
 *
 * <p>用探针 Controller 而非真实 Controller：绑定与校验是 Web 层职责，
 * 探针可精确验证这层行为，无需 mock 业务 Service（本机 JDK 25 下 Mockito 无法 mock 具体类）。
 */
@WebMvcTest(controllers = DocumentPageRequestBindingTest.PagingProbeController.class, properties = {
        "app.jwt.secret=test-page-binding-jwt-secret-key-with-at-least-thirty-two-bytes",
        "app.jwt.expiration-ms=28800000"
})
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class,
        JwtAuthenticationFilter.class,
        JsonAuthenticationEntryPoint.class,
        SecurityResponseWriter.class,
        JsonAccessDeniedHandler.class,
        JwtService.class,
        TokenBlacklistService.class,
        SecurityBeansConfig.class,
        TimeConfig.class,
        GlobalExceptionHandler.class,
        DocumentPageRequestBindingTest.PagingProbeController.class
})
class DocumentPageRequestBindingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldBindFlatQueryParamsToRecordComponents() throws Exception {
        probe("page", "2", "size", "5", "keyword", "合同")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.keyword").value("合同"));
    }

    @Test
    void shouldApplyDefaultsWhenPagingParamsAbsent() throws Exception {
        probe()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    /** size = 0 既不是「不分页」也不是合法页码大小，必须被拒绝而不是静默纠正成默认 10。 */
    @Test
    void shouldRejectZeroSize() throws Exception {
        probe("size", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("每页条数必须为 -1（不分页）或 1~1000")));
    }

    @Test
    void shouldRejectSizeExceedingMax() throws Exception {
        probe("size", "1001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectPageLessThanOne() throws Exception {
        probe("page", "0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldAcceptSizeMinusOneAsUnpagedFlag() throws Exception {
        probe("size", "-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(-1));
    }

    @Test
    void shouldReturnUnpagedWhenSizeIsMinusOne() {
        DocumentPageRequest request = new DocumentPageRequest(null, null, null, null, null, 1, -1);

        assertThat(request.toPageable(Sort.by(Sort.Direction.DESC, "updatedAt")))
                .isSameAs(Pageable.unpaged());
    }

    @Test
    void shouldConvertToSpringDataPageable() {
        DocumentPageRequest request = new DocumentPageRequest(null, null, null, null, null, 3, 20);

        Pageable pageable = request.toPageable(Sort.by(Sort.Direction.DESC, "updatedAt"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    private org.springframework.test.web.servlet.ResultActions probe(String... params) throws Exception {
        var request = get("/api/test/paging-probe").header("Authorization", "Bearer " + token());
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String token() {
        SysDataScope scope = new SysDataScope("ALL", "全部单据");
        SysPermission permission = new SysPermission("VIEW_ALL_FORMS", "查看全部单据");

        SysRole role = new SysRole();
        role.setName("测试角色");
        role.setDataScope(scope);
        role.addPermission(permission);

        SysUser user = new SysUser();
        user.setAccount("paging-tester");
        user.setName("分页测试用户");
        user.setDepartment("测试部门");
        user.setPost("测试岗位");
        user.addRole(role);
        return jwtService.issueAccessToken(user);
    }

    /** 回显绑定结果，用于断言 record 的构造器绑定。 */
    record PageEcho(Integer page, Integer size, String keyword) {
    }

    @RestController
    static class PagingProbeController {

        @GetMapping("/api/test/paging-probe")
        ApiResponse<PageEcho> probe(@Valid DocumentPageRequest request) {
            return ApiResponse.success(new PageEcho(request.page(), request.size(), request.keyword()));
        }
    }
}
