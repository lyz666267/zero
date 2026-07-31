package com.platform.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.entity.Datasource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatasourceResponse DTO 安全测试 — 确保加密密码不会泄露到 JSON 响应
 */
@DisplayName("DatasourceResponse — 密码字段隔离测试")
class DatasourceResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ==================== JSON 序列化测试 ====================

    @Nested
    @DisplayName("JSON 序列化 — passwordEncrypted 不得出现")
    class JsonSerialization {

        @Test
        @DisplayName("序列化不应包含 passwordEncrypted")
        void shouldNotContainPasswordEncrypted() throws Exception {
            DatasourceResponse dto = DatasourceResponse.builder()
                    .id(1L)
                    .projectId(100L)
                    .name("测试数据源")
                    .dbType("MySQL")
                    .host("192.168.1.1")
                    .port(3306)
                    .username("admin")
                    .dbName("test_db")
                    .status("CONNECTED")
                    .build();

            String json = objectMapper.writeValueAsString(dto);

            assertFalse(json.contains("passwordEncrypted"),
                    "JSON 不应包含 passwordEncrypted 字段");
            assertFalse(json.contains("password"),
                    "JSON 不应包含任何 password 相关字段");
        }

        @Test
        @DisplayName("序列化应包含所有公开字段")
        void shouldContainPublicFields() throws Exception {
            DatasourceResponse dto = DatasourceResponse.builder()
                    .id(1L)
                    .projectId(100L)
                    .name("测试数据源")
                    .dbType("MySQL")
                    .host("192.168.1.1")
                    .port(3306)
                    .username("admin")
                    .dbName("test_db")
                    .status("CONNECTED")
                    .build();

            String json = objectMapper.writeValueAsString(dto);

            assertTrue(json.contains("\"id\":1"), "应包含 id");
            assertTrue(json.contains("\"name\":\"测试数据源\""), "应包含 name");
            assertTrue(json.contains("\"host\":\"192.168.1.1\""), "应包含 host");
            assertTrue(json.contains("\"username\":\"admin\""), "应包含 username");
            assertTrue(json.contains("\"dbName\":\"test_db\""), "应包含 dbName");
            assertTrue(json.contains("\"status\":\"CONNECTED\""), "应包含 status");
        }
    }

    // ==================== fromEntity 转换测试 ====================

    @Nested
    @DisplayName("fromEntity — 实体转 DTO")
    class FromEntity {

        @Test
        @DisplayName("应正确映射所有公开字段")
        void shouldMapAllPublicFields() {
            Datasource entity = new Datasource();
            entity.setId(1L);
            entity.setProjectId(100L);
            entity.setName("生产数据库");
            entity.setDbType("MySQL");
            entity.setHost("10.0.0.1");
            entity.setPort(3306);
            entity.setUsername("root");
            entity.setPasswordEncrypted("AES_ENCRYPTED_SECRET_KEY_12345");
            entity.setDbName("production_db");
            entity.setStatus("CONNECTED");

            DatasourceResponse dto = DatasourceResponse.fromEntity(entity);

            assertEquals(1L, dto.getId());
            assertEquals(100L, dto.getProjectId());
            assertEquals("生产数据库", dto.getName());
            assertEquals("MySQL", dto.getDbType());
            assertEquals("10.0.0.1", dto.getHost());
            assertEquals(3306, dto.getPort());
            assertEquals("root", dto.getUsername());
            assertEquals("production_db", dto.getDbName());
            assertEquals("CONNECTED", dto.getStatus());
        }

        @Test
        @DisplayName("DTO 不应包含 passwordEncrypted 字段（编译时安全）")
        void dtoClassShouldNotHavePasswordField() {
            // 反射检查 DatasourceResponse 类不应有 passwordEncrypted getter
            var methods = DatasourceResponse.class.getMethods();
            for (var method : methods) {
                assertFalse(method.getName().toLowerCase().contains("password"),
                        "DatasourceResponse 不应有 password 相关方法: " + method.getName());
            }
        }
    }
}
