package dev.reboot.exception;

import dev.reboot.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 关键分支回归（TD-038）。
 *
 * <p>Day 102 压测实测：畸形 JSON 走 {@link Exception} 兜底返回 500 并污染 error.log；
 * Day 103 遗留清单将其列为 TD-038。本测试用 standalone MockMvc + 探针控制器锁定语义：
 * 畸形 JSON / 缺失请求体 → 400（客户端错误），不再进 500 兜底。</p>
 */
class GlobalExceptionHandlerTest {

    /** 探针控制器：仅用于触发 @RequestBody 反序列化路径。 */
    @RestController
    static class ProbeController {
        @PostMapping("/probe")
        ApiResponse<String> echo(@RequestBody Map<String, String> body) {
            return ApiResponse.ok(body.get("name"));
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void malformedJsonReturns400Not500() throws Exception {
        mockMvc.perform(post("/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"broken..."))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求体格式错误，请检查 JSON 语法与必填字段"));
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void validBodyStillReturns200() throws Exception {
        mockMvc.perform(post("/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ok"));
    }
}
