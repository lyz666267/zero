package com.platform.service;

import com.platform.dto.DatasourceRequest;
import com.platform.dto.DatasourceResponse;
import com.platform.entity.Project;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.mapper.DatasourceMapper;
import com.platform.mapper.ProjectMapper;
import com.platform.mapper.UserMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源服务用户隔离测试 — 验证跨用户访问被拒绝
 */
@SpringBootTest
@DisplayName("数据源服务 — 用户隔离")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatasourceServiceTest {

    @Autowired
    private DatasourceService datasourceService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private DatasourceMapper datasourceMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static Long userAId;
    private static Long userBId;
    private static Long projectAId;
    private static Long datasourceAId;

    @BeforeAll
    static void setUp(@Autowired UserMapper userMapper,
                      @Autowired PasswordEncoder passwordEncoder,
                      @Autowired ProjectMapper projectMapper) {
        // 清理旧数据
        userMapper.delete(null);
        projectMapper.delete(null);

        // 创建用户 A
        User userA = new User();
        userA.setUsername("datasource_test_userA");
        userA.setPassword(passwordEncoder.encode("test123"));
        userA.setNickname("User A");
        userA.setEnabled(true);
        userMapper.insert(userA);
        userAId = userA.getId();

        // 创建用户 B
        User userB = new User();
        userB.setUsername("datasource_test_userB");
        userB.setPassword(passwordEncoder.encode("test123"));
        userB.setNickname("User B");
        userB.setEnabled(true);
        userMapper.insert(userB);
        userBId = userB.getId();

        // 用户 A 创建项目
        Project projectA = new Project();
        projectA.setUserId(userAId);
        projectA.setName("User A 的项目");
        projectA.setDescription("测试项目");
        projectMapper.insert(projectA);
        projectAId = projectA.getId();
    }

    @AfterAll
    static void tearDown(@Autowired DatasourceMapper datasourceMapper,
                         @Autowired ProjectMapper projectMapper,
                         @Autowired UserMapper userMapper) {
        // 清理测试数据
        datasourceMapper.delete(null);
        projectMapper.delete(null);
        userMapper.delete(null);
    }

    // ==================== getById 用户隔离测试 ====================

    @Nested
    @DisplayName("getById — 用户隔离")
    class GetByIdIsolation {

        @Test
        @Order(1)
        @DisplayName("用户 A 创建数据源 → 成功")
        void userACanCreateDatasource() {
            DatasourceRequest request = new DatasourceRequest();
            request.setProjectId(projectAId);
            request.setName("用户 A 的 MySQL");
            request.setDbType("MySQL");
            request.setHost("192.168.1.100");
            request.setPort(3306);
            request.setUsername("admin");
            request.setPassword("secret123");
            request.setDatabaseName("testdb");

            DatasourceResponse response = datasourceService.create(userAId, request);
            assertNotNull(response.getId());
            assertEquals("用户 A 的 MySQL", response.getName());
            datasourceAId = response.getId();
        }

        @Test
        @Order(2)
        @DisplayName("用户 A 查询自己的数据源 → 成功")
        void userACanGetOwnDatasource() {
            DatasourceResponse response = datasourceService.getById(datasourceAId, userAId);
            assertEquals(datasourceAId, response.getId());
            assertEquals("用户 A 的 MySQL", response.getName());
        }

        @Test
        @Order(2)
        @DisplayName("用户 B 查询用户 A 的数据源 → 被拒绝")
        void userBCannotGetOtherUsersDatasource() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.getById(datasourceAId, userBId));
            // 应返回 404（而非 403），避免信息泄露（不暴露资源是否存在）
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("项目不存在"));
        }

        @Test
        @Order(2)
        @DisplayName("不存在的 dataourceId → 被拒绝")
        void nonExistentDatasourceShouldFail() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.getById(99999L, userAId));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("数据源不存在"));
        }
    }

    // ==================== update 用户隔离测试 ====================

    @Nested
    @DisplayName("update — 用户隔离")
    class UpdateIsolation {

        @Test
        @Order(3)
        @DisplayName("用户 A 更新自己的数据源 → 成功")
        void userACanUpdateOwnDatasource() {
            DatasourceRequest request = new DatasourceRequest();
            request.setProjectId(projectAId);
            request.setName("用户 A 的 MySQL（已更新）");
            request.setDbType("MySQL");
            request.setHost("192.168.1.101");
            request.setPort(3307);
            request.setUsername("admin2");
            request.setPassword("newpass456");
            request.setDatabaseName("testdb2");

            DatasourceResponse response = datasourceService.update(datasourceAId, userAId, request);
            assertEquals("用户 A 的 MySQL（已更新）", response.getName());
            assertEquals("192.168.1.101", response.getHost());
        }

        @Test
        @Order(3)
        @DisplayName("用户 B 更新用户 A 的数据源 → 被拒绝")
        void userBCannotUpdateOtherUsersDatasource() {
            DatasourceRequest request = new DatasourceRequest();
            request.setProjectId(projectAId);
            request.setName("恶意修改");
            request.setDbType("MySQL");
            request.setHost("evil.com");
            request.setPort(3306);
            request.setUsername("hacker");
            request.setPassword("hack123");
            request.setDatabaseName("malicious");

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.update(datasourceAId, userBId, request));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("项目不存在"));
        }

        @Test
        @Order(3)
        @DisplayName("不存在的 datasourceId → 更新被拒绝")
        void updateNonExistentShouldFail() {
            DatasourceRequest request = new DatasourceRequest();
            request.setProjectId(projectAId);
            request.setName("test");
            request.setDbType("MySQL");
            request.setHost("127.0.0.1");
            request.setPort(3306);
            request.setUsername("root");
            request.setPassword("pass");
            request.setDatabaseName("db");

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.update(99999L, userAId, request));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("数据源不存在"));
        }
    }

    // ==================== delete 用户隔离测试 ====================

    @Nested
    @DisplayName("delete — 用户隔离")
    class DeleteIsolation {

        @Test
        @Order(4)
        @DisplayName("用户 B 删除用户 A 的数据源 → 被拒绝")
        void userBCannotDeleteOtherUsersDatasource() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.delete(datasourceAId, userBId));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("项目不存在"));

            // 验证数据源仍然存在（未被删除）
            DatasourceResponse response = datasourceService.getById(datasourceAId, userAId);
            assertNotNull(response);
        }

        @Test
        @Order(5)
        @DisplayName("用户 A 删除自己的数据源 → 成功")
        void userACanDeleteOwnDatasource() {
            assertDoesNotThrow(() -> datasourceService.delete(datasourceAId, userAId));

            // 验证已删除
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.getById(datasourceAId, userAId));
            assertEquals(404, ex.getCode());
        }
    }

    // ==================== listByProject 用户隔离测试 ====================

    @Nested
    @DisplayName("listByProject — 用户隔离")
    class ListByProject {

        @Test
        @Order(6)
        @DisplayName("用户 B 查看用户 A 的项目数据源 → 被拒绝")
        void userBCannotListOtherUsersProject() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    datasourceService.listByProject(projectAId, userBId));
            assertEquals(404, ex.getCode());
            assertTrue(ex.getMessage().contains("项目不存在"));
        }
    }
}
