package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.entity.TestDataTaskPlan;
import com.platform.mapper.TestDataTaskPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 测试数据生成计划服务
 *
 * <h3>职责</h3>
 * <p>保存和查询 LLM Agent 生成的 GenerationPlan（JSON 格式），
 * 用于前端展示 AI 生成过程的中间结果。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>保存失败不影响主流程 — 异常被捕获并记录日志，不向上抛出</li>
 *   <li>plan_json 以完整 JSON 格式存储，保留原始结构</li>
 *   <li>查询返回最近一条计划记录</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataTaskPlanService {

    private final TestDataTaskPlanMapper planMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存生成计划
     *
     * <p>将 plan 对象序列化为 JSON 后存入 testdata_task_plan 表。
     * 序列化或写入失败仅记录日志，不抛出异常，确保不影响主生成流程。</p>
     *
     * @param taskId 关联任务 ID
     * @param plan   生成计划对象（通常为 {@code GeneratePlanResponse}）
     */
    public void savePlan(Long taskId, Object plan) {
        if (plan == null) {
            log.debug("跳过空 plan 保存: taskId={}", taskId);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(plan);

            TestDataTaskPlan entity = new TestDataTaskPlan();
            entity.setTaskId(taskId);
            entity.setPlanJson(json);

            planMapper.insert(entity);
            log.info("生成计划已保存: taskId={}, jsonLength={}", taskId, json.length());
        } catch (JsonProcessingException e) {
            log.error("计划 JSON 序列化失败: taskId={}, error={}", taskId, e.getMessage());
        } catch (Exception e) {
            log.error("计划保存失败（不影响任务状态）: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    /**
     * 查询生成计划
     *
     * @param taskId 任务 ID
     * @return 最近一条计划记录，不存在则返回 {@code null}
     */
    public TestDataTaskPlan getPlan(Long taskId) {
        return planMapper.selectList(
                new LambdaQueryWrapper<TestDataTaskPlan>()
                        .eq(TestDataTaskPlan::getTaskId, taskId)
                        .orderByDesc(TestDataTaskPlan::getCreateTime)
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);
    }

    /**
     * 根据任务 ID 查询并解析生成计划
     *
     * <p>查询 testdata_task_plan 表，将 plan_json 解析为 Object/Map 结构返回。
     * 解析失败仅记录日志，返回 null。</p>
     *
     * @param taskId 任务 ID
     * @return 解析后的计划对象，不存在或解析失败则返回 {@code null}
     */
    public Object getPlanByTaskId(Long taskId) {
        TestDataTaskPlan plan = getPlan(taskId);
        if (plan == null || plan.getPlanJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(plan.getPlanJson(), Object.class);
        } catch (JsonProcessingException e) {
            log.error("计划 JSON 解析失败: taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }
}
