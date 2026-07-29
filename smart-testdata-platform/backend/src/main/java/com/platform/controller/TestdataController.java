package com.platform.controller;

import com.platform.dto.ApiResponse;
import com.platform.dto.GeneratePlanRequest;
import com.platform.dto.GeneratePlanResponse;
import com.platform.service.TestdataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 测试数据生成接口 — 代理 AI 服务
 * <p>
 * 调用链：Vue → Spring Boot (/api/testdata/...) → FastAPI → LangChain Agent
 */
@RestController
@RequestMapping("/api/testdata")
@RequiredArgsConstructor
public class TestdataController {

    private final TestdataService testdataService;

    /**
     * 生成测试数据计划
     * <p>
     * POST /api/testdata/generate-plan
     * <p>
     * 请求体:
     * <pre>
     * {
     *   "schema": { ... Schema JSON ... },
     *   "requirement": "生成1000条用户数据"
     * }
     * </pre>
     * <p>
     * 返回: GenerationPlan JSON
     */
    @PostMapping("/generate-plan")
    public ApiResponse<GeneratePlanResponse> generatePlan(@RequestBody GeneratePlanRequest request) {
        GeneratePlanResponse result = testdataService.generatePlan(request);
        return ApiResponse.success(result);
    }
}
