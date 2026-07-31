package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dto.ResultResponse;
import com.platform.entity.TestDataResult;
import com.platform.entity.TestDataTask;
import com.platform.exception.BusinessException;
import com.platform.mapper.TestDataResultMapper;
import com.platform.mapper.TestDataTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 测试数据生成结果服务
 *
 * <h3>职责</h3>
 * <p>保存每次任务产生的测试数据（JSON 格式），供前端查看和回溯。
 * 一个任务支持多张表的结果，每张表一条记录。</p>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>保存失败不影响主流程 — 异常被捕获并记录日志</li>
 *   <li>data_json 存储原始 {@code List<Map>} → JSON 数组字符串</li>
 *   <li>查询返回按创建时间排序的结果列表</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataResultService {

    private final TestDataResultMapper resultMapper;
    private final TestDataTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存单表生成结果
     *
     * @param taskId    关联任务 ID
     * @param tableName 来源表名
     * @param data      生成的数据行列表
     */
    public void saveResult(Long taskId, String tableName, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            log.debug("跳过空数据保存: taskId={}, tableName={}", taskId, tableName);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(data);

            TestDataResult result = new TestDataResult();
            result.setTaskId(taskId);
            result.setTableName(tableName);
            result.setDataJson(json);

            resultMapper.insert(result);
            log.info("生成结果已保存: taskId={}, tableName={}, rows={}",
                    taskId, tableName, data.size());
        } catch (JsonProcessingException e) {
            log.error("数据 JSON 序列化失败: taskId={}, tableName={}, error={}",
                    taskId, tableName, e.getMessage());
        }
    }

    /**
     * 按任务 ID 查询生成结果列表
     *
     * @param taskId 任务 ID
     * @return 该任务的所有表结果，按创建时间升序
     */
    public List<TestDataResult> findByTaskId(Long taskId) {
        return resultMapper.selectList(
                new LambdaQueryWrapper<TestDataResult>()
                        .eq(TestDataResult::getTaskId, taskId)
                        .orderByAsc(TestDataResult::getCreateTime)
        );
    }

    /**
     * 按任务 ID 查询并将 dataJson 反序列化为 List
     *
     * @param taskId 任务 ID
     * @return 任务下所有表名 → 数据行的映射
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedHashMap<String, List<Map<String, Object>>> findDataByTaskId(Long taskId) {
        List<TestDataResult> results = findByTaskId(taskId);
        java.util.LinkedHashMap<String, List<Map<String, Object>>> map = new java.util.LinkedHashMap<>();
        for (TestDataResult result : results) {
            try {
                List<Map<String, Object>> data = objectMapper.readValue(
                        result.getDataJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
                map.put(result.getTableName(), data);
            } catch (JsonProcessingException e) {
                log.error("数据 JSON 反序列化失败: taskId={}, tableName={}, error={}",
                        taskId, result.getTableName(), e.getMessage());
                map.put(result.getTableName(), Collections.emptyList());
            }
        }
        return map;
    }

    /**
     * 按任务 ID 查询生成结果（聚合返回）
     *
     * <p>检查任务是否存在，若不存在抛出 {@link BusinessException}(404)。
     * 若任务存在但无生成结果，返回 success=true + 空 tables 列表。</p>
     *
     * @param taskId 任务 ID
     * @return 按 tableName 聚合的结果响应
     * @throws BusinessException 如果任务不存在
     */
    public ResultResponse getResultByTaskId(Long taskId) {
        // 1. 校验任务是否存在
        TestDataTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 2. 查询并反序列化所有结果
        java.util.LinkedHashMap<String, List<Map<String, Object>>> dataMap = findDataByTaskId(taskId);

        // 3. 按 tableName 聚合为 ResultTable 列表
        List<ResultResponse.ResultTable> tables = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : dataMap.entrySet()) {
            tables.add(ResultResponse.ResultTable.builder()
                    .tableName(entry.getKey())
                    .rows(entry.getValue())
                    .build());
        }

        return ResultResponse.builder()
                .success(true)
                .tables(tables)
                .build();
    }
}
