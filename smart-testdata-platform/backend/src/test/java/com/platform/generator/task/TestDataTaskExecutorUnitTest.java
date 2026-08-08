package com.platform.generator.task;

import com.platform.dto.CachedSchemaResponse;
import com.platform.dto.CachedSchemaResponse.CachedColumnInfo;
import com.platform.dto.CachedSchemaResponse.CachedTableInfo;
import com.platform.generator.persistence.MultiTableWriteService;
import com.platform.mapper.TestDataTaskMapper;
import com.platform.privacy.service.PrivacyAwareDataProcessor;
import com.platform.schema.SchemaCacheService;
import com.platform.service.AgentLogService;
import com.platform.service.DataQualityEvaluator;
import com.platform.service.TestDataResultService;
import com.platform.service.TestDataTaskPlanService;
import com.platform.service.TestdataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * TestDataTaskExecutor 单元测试 — 验证发送给 AI 的 Schema 字段名正确
 */
@ExtendWith(MockitoExtension.class)
class TestDataTaskExecutorUnitTest {

    @Mock
    private TestDataTaskMapper taskMapper;

    @Mock
    private SchemaCacheService schemaCacheService;

    @Mock
    private TestdataService testdataService;

    @Mock
    private MultiTableDataGenerator multiTableDataGenerator;

    @Mock
    private MultiTableWriteService multiTableWriteService;

    @Mock
    private TestDataResultService testDataResultService;

    @Mock
    private TestDataTaskPlanService testDataTaskPlanService;

    @Mock
    private AgentLogService agentLogService;

    @Mock
    private DataQualityEvaluator qualityEvaluator;

    @Mock
    private PrivacyAwareDataProcessor privacyProcessor;

    @InjectMocks
    private TestDataTaskExecutor executor;

    @Test
    @SuppressWarnings("unchecked")
    void buildSchemaMapShouldUseTableNameKey() {
        CachedColumnInfo column = CachedColumnInfo.builder()
                .name("id")
                .type("bigint")
                .primaryKey(true)
                .nullable(false)
                .build();
        CachedTableInfo table = CachedTableInfo.builder()
                .tableName("users")
                .tableComment("用户表")
                .columns(List.of(column))
                .build();

        when(schemaCacheService.hasCache(1L)).thenReturn(true);
        when(schemaCacheService.getSchema(1L))
                .thenReturn(CachedSchemaResponse.builder().tables(List.of(table)).build());

        Map<String, Object> schema = ReflectionTestUtils.invokeMethod(
                executor, "buildSchemaMap", 1L);
        List<Map<String, Object>> tables =
                (List<Map<String, Object>>) schema.get("tables");

        assertEquals("users", tables.get(0).get("tableName"));
        assertNull(tables.get(0).get("name"));
    }
}
