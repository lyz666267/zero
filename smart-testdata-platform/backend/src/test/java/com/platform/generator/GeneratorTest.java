package com.platform.generator;

import com.platform.dto.ForeignKeyInfo;
import com.platform.dto.GeneratePlanResponse.FieldPlan;
import com.platform.dto.GeneratePlanResponse.Range;
import com.platform.dto.GeneratePlanResponse.TablePlan;
import com.platform.generator.context.GenerationContext;
import com.platform.dto.MultiTableGenerateResponse;
import com.platform.dto.MultiTableGenerateResponse.TableResult;
import com.platform.generator.impl.*;
import com.platform.generator.relation.ForeignKeyGenerator;
import com.platform.generator.table.TableDataGenerator;
import com.platform.generator.task.MultiTableDataGenerator;
import com.platform.generator.persistence.InsertStatementBuilder;
import com.platform.schema.relation.TableOrderService;
import com.platform.sql.InsertSqlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生成器单元测试 — 不连接数据库，纯内存测试
 */
@DisplayName("生成器单元测试")
class GeneratorTest {

    private GeneratorEngine engine;
    private GeneratorRegistry registry;
    private TableDataGenerator tableDataGenerator;
    private MultiTableDataGenerator multiTableDataGenerator;
    private InsertSqlBuilder insertSqlBuilder;
    private InsertStatementBuilder insertStatementBuilder;

    @BeforeEach
    void setUp() {
        // 手动组装 registry + engine，不依赖 Spring 容器
        List<Generator> generators = List.of(
                new NameGenerator(),
                new EmailGenerator(),
                new IntegerGenerator(),
                new WordGenerator(),
                new BooleanGenerator(),
                new EnumGenerator(),
                new DecimalGenerator(),
                new DateTimeGenerator(),
                new UUIDGenerator(),
                new PhoneGenerator()
        );
        registry = new GeneratorRegistry(generators);
        registry.init();
        ForeignKeyGenerator fkGenerator = new ForeignKeyGenerator();
        engine = new GeneratorEngine(registry, fkGenerator);
        tableDataGenerator = new TableDataGenerator(engine);

        // 多表调度器
        TableOrderService tableOrderService = new TableOrderService();
        multiTableDataGenerator = new MultiTableDataGenerator(tableOrderService, tableDataGenerator);
        insertSqlBuilder = new InsertSqlBuilder();
        insertStatementBuilder = new InsertStatementBuilder();
    }

    // ==================== faker.email ====================

    @Test
    @DisplayName("faker.email — 应返回合法邮箱地址")
    void testFakerEmail() {
        FieldPlan plan = FieldPlan.builder()
                .name("email")
                .generator("faker.email")
                .build();

        Object value = engine.execute(plan);
        assertNotNull(value);
        assertInstanceOf(String.class, value);

        String email = (String) value;
        assertTrue(email.contains("@"), "邮箱应包含 @: " + email);
    }

    // ==================== faker.name ====================

    @Test
    @DisplayName("faker.name — 应返回非空中文姓名")
    void testFakerName() {
        FieldPlan plan = FieldPlan.builder()
                .name("name")
                .generator("faker.name")
                .build();

        Object value = engine.execute(plan);
        assertNotNull(value);
        assertInstanceOf(String.class, value);

        String name = (String) value;
        assertFalse(name.isBlank(), "姓名不应为空");
        // 中文姓名至少 2 个字符
        assertTrue(name.length() >= 2, "姓名长度应 ≥ 2: " + name);
    }

    // ==================== random.integer ====================

    @Test
    @DisplayName("random.integer 默认范围 — 应在 1~100 内")
    void testRandomIntegerDefault() {
        FieldPlan plan = FieldPlan.builder()
                .name("age")
                .generator("random.integer")
                .build();

        // 多次生成确保均在默认范围内
        for (int i = 0; i < 50; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(Integer.class, value);
            int num = (Integer) value;
            assertTrue(num >= 1 && num <= 100,
                    "默认范围应为 [1, 100]，实际: " + num);
        }
    }

    @Test
    @DisplayName("random.integer 指定范围 — 应在 min~max 内")
    void testRandomIntegerWithRange() {
        Range range = Range.builder().min(10).max(20).build();
        FieldPlan plan = FieldPlan.builder()
                .name("score")
                .generator("random.integer")
                .range(range)
                .build();

        // 多次生成确保均在指定范围内
        for (int i = 0; i < 50; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(Integer.class, value);
            int num = (Integer) value;
            assertTrue(num >= 10 && num <= 20,
                    "应在 [10, 20] 内，实际: " + num);
        }
    }

    @Test
    @DisplayName("random.integer 边界值 — min=max 应始终返回该值")
    void testRandomIntegerBoundary() {
        Range range = Range.builder().min(42).max(42).build();
        FieldPlan plan = FieldPlan.builder()
                .name("fixed")
                .generator("random.integer")
                .range(range)
                .build();

        for (int i = 0; i < 10; i++) {
            assertEquals(42, engine.execute(plan));
        }
    }

    // ==================== random.boolean ====================

    @Test
    @DisplayName("random.boolean — 应返回 true 或 false")
    void testRandomBoolean() {
        FieldPlan plan = FieldPlan.builder()
                .name("active")
                .generator("random.boolean")
                .build();

        boolean foundTrue = false;
        boolean foundFalse = false;

        // 多次生成确保能覆盖 true 和 false
        for (int i = 0; i < 50; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(Boolean.class, value);
            if ((Boolean) value) {
                foundTrue = true;
            } else {
                foundFalse = true;
            }
        }
        assertTrue(foundTrue, "应至少有一次 true");
        assertTrue(foundFalse, "应至少有一次 false");
    }

    // ==================== 异常场景 ====================

    @Test
    @DisplayName("未注册的生成器 — 应抛出异常")
    void testUnknownGenerator() {
        FieldPlan plan = FieldPlan.builder()
                .name("x")
                .generator("faker.unknown")
                .build();

        assertThrows(com.platform.exception.BusinessException.class,
                () -> engine.execute(plan));
    }

    @Test
    @DisplayName("generator 为空 — 应抛出异常")
    void testBlankGenerator() {
        FieldPlan plan = FieldPlan.builder()
                .name("x")
                .generator("")
                .build();

        assertThrows(com.platform.exception.BusinessException.class,
                () -> engine.execute(plan));
    }

    // ==================== 注册中心 ====================

    @Test
    @DisplayName("注册中心 — 应包含全部 10 个生成器")
    void testRegistryContainsAll() {
        assertTrue(registry.contains("faker.name"));
        assertTrue(registry.contains("faker.email"));
        assertTrue(registry.contains("random.integer"));
        assertTrue(registry.contains("faker.word"));
        assertTrue(registry.contains("random.boolean"));
        assertTrue(registry.contains("enum.values"));
        assertTrue(registry.contains("random.decimal"));
        assertTrue(registry.contains("time.past_datetime"));
        assertTrue(registry.contains("uuid"));
        assertTrue(registry.contains("faker.phone"));
        assertEquals(10, registry.registeredNames().size());
    }

    // ==================== enum.values ====================

    @Test
    @DisplayName("enum.values — 返回值应在枚举列表中")
    void testEnumValues() {
        FieldPlan plan = FieldPlan.builder()
                .name("status")
                .generator("enum.values")
                .params(Map.of("values", List.of("ACTIVE", "INACTIVE", "PENDING")))
                .build();

        for (int i = 0; i < 30; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(String.class, value);
            String str = (String) value;
            assertTrue(List.of("ACTIVE", "INACTIVE", "PENDING").contains(str),
                    "枚举值应在列表中，实际: " + str);
        }
    }

    // ==================== random.decimal ====================

    @Test
    @DisplayName("random.decimal — 应在 0~10000 范围内，保留 2 位小数")
    void testRandomDecimalDefault() {
        FieldPlan plan = FieldPlan.builder()
                .name("price")
                .generator("random.decimal")
                .build();

        for (int i = 0; i < 50; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(BigDecimal.class, value);
            BigDecimal decimal = (BigDecimal) value;
            assertTrue(decimal.compareTo(BigDecimal.ZERO) >= 0,
                    "应 >= 0，实际: " + decimal);
            assertTrue(decimal.compareTo(new BigDecimal("10000.00")) <= 0,
                    "应 <= 10000，实际: " + decimal);
            assertEquals(2, decimal.scale(), "应保留 2 位小数");
        }
    }

    @Test
    @DisplayName("random.decimal 指定范围 — 应在 min~max 内")
    void testRandomDecimalWithRange() {
        Range range = Range.builder().min(0).max(100).build();
        FieldPlan plan = FieldPlan.builder()
                .name("amount")
                .generator("random.decimal")
                .range(range)
                .build();

        for (int i = 0; i < 30; i++) {
            Object value = engine.execute(plan);
            BigDecimal decimal = (BigDecimal) value;
            assertTrue(decimal.compareTo(new BigDecimal("0.00")) >= 0,
                    "应 >= 0，实际: " + decimal);
            assertTrue(decimal.compareTo(new BigDecimal("100.00")) <= 0,
                    "应 <= 100，实际: " + decimal);
        }
    }

    // ==================== time.past_datetime ====================

    @Test
    @DisplayName("time.past_datetime — 应返回过去时间，格式正确")
    void testPastDateTime() {
        FieldPlan plan = FieldPlan.builder()
                .name("created_at")
                .generator("time.past_datetime")
                .build();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < 20; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(String.class, value);

            String datetime = (String) value;
            // 验证格式可解析
            LocalDateTime parsed = LocalDateTime.parse(datetime, fmt);
            // 验证是过去时间
            assertTrue(parsed.isBefore(LocalDateTime.now()),
                    "应为过去时间，实际: " + datetime);
        }
    }

    // ==================== uuid ====================

    @Test
    @DisplayName("uuid — 应返回 36 字符 UUID 格式")
    void testUUID() {
        FieldPlan plan = FieldPlan.builder()
                .name("id")
                .generator("uuid")
                .build();

        for (int i = 0; i < 10; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(String.class, value);

            String uuid = (String) value;
            assertEquals(36, uuid.length(), "UUID 应为 36 字符: " + uuid);
            // 验证连字符位置
            assertEquals('-', uuid.charAt(8));
            assertEquals('-', uuid.charAt(13));
            assertEquals('-', uuid.charAt(18));
            assertEquals('-', uuid.charAt(23));
            // 验证是合法的 UUID（可解析）
            assertDoesNotThrow(() -> java.util.UUID.fromString(uuid),
                    "应能解析为 UUID: " + uuid);
        }
    }

    // ==================== faker.phone ====================

    @Test
    @DisplayName("faker.phone — 应返回 11 位手机号")
    void testPhone() {
        FieldPlan plan = FieldPlan.builder()
                .name("phone")
                .generator("faker.phone")
                .build();

        for (int i = 0; i < 20; i++) {
            Object value = engine.execute(plan);
            assertInstanceOf(String.class, value);

            String phone = (String) value;
            assertFalse(phone.isBlank(), "手机号不应为空");
            assertTrue(phone.matches("\\d{11}"), "手机号应为 11 位数字: " + phone);
        }
    }

    // ==================== 表级生成 ====================

    @Test
    @DisplayName("单字段表生成 — 应返回指定行数")
    void testTableSingleField() {
        TablePlan tablePlan = TablePlan.builder()
                .table("test_user")
                .count(5)
                .fields(List.of(
                        FieldPlan.builder().name("username").generator("faker.name").build()
                ))
                .build();

        List<Map<String, Object>> rows = tableDataGenerator.generate(tablePlan);

        assertEquals(5, rows.size(), "应生成 5 行数据");
        for (Map<String, Object> row : rows) {
            assertEquals(1, row.size(), "每行应有 1 个字段");
            assertTrue(row.containsKey("username"), "应包含 username 字段");
            assertNotNull(row.get("username"), "username 不应为 null");
            String name = (String) row.get("username");
            assertFalse(name.isBlank(), "username 不应为空");
            assertTrue(name.length() >= 2, "姓名长度应 ≥ 2: " + name);
        }
    }

    @Test
    @DisplayName("多字段表生成 — 每行应包含所有字段")
    void testTableMultiField() {
        TablePlan tablePlan = TablePlan.builder()
                .table("user")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder().name("username").generator("faker.name").build(),
                        FieldPlan.builder().name("email").generator("faker.email").build(),
                        FieldPlan.builder().name("age").generator("random.integer")
                                .range(Range.builder().min(18).max(65).build()).build()
                ))
                .build();

        List<Map<String, Object>> rows = tableDataGenerator.generate(tablePlan);

        assertEquals(3, rows.size(), "应生成 3 行数据");
        for (Map<String, Object> row : rows) {
            assertEquals(3, row.size(), "每行应有 3 个字段");
            assertTrue(row.containsKey("username"), "应包含 username");
            assertTrue(row.containsKey("email"), "应包含 email");
            assertTrue(row.containsKey("age"), "应包含 age");

            String email = (String) row.get("email");
            assertTrue(email.contains("@"), "邮箱应包含 @: " + email);

            Integer age = (Integer) row.get("age");
            assertTrue(age >= 18 && age <= 65, "age 应在 18~65: " + age);
        }
    }

    @Test
    @DisplayName("count=100 — 应返回 100 条数据")
    void testTableLargeCount() {
        TablePlan tablePlan = TablePlan.builder()
                .table("large_table")
                .count(100)
                .fields(List.of(
                        FieldPlan.builder().name("id").generator("uuid").build(),
                        FieldPlan.builder().name("active").generator("random.boolean").build()
                ))
                .build();

        List<Map<String, Object>> rows = tableDataGenerator.generate(tablePlan);

        assertEquals(100, rows.size(), "应生成 100 行数据");
        for (Map<String, Object> row : rows) {
            assertEquals(2, row.size(), "每行应有 2 个字段");
            // UUID 应为 36 字符
            String id = (String) row.get("id");
            assertEquals(36, id.length(), "UUID 应为 36 字符");
            // boolean 应非空
            assertNotNull(row.get("active"), "active 不应为 null");
        }
    }

    @Test
    @DisplayName("未知 generator 的表生成 — 应抛出异常")
    void testTableUnknownGenerator() {
        TablePlan tablePlan = TablePlan.builder()
                .table("bad_table")
                .count(5)
                .fields(List.of(
                        FieldPlan.builder().name("x").generator("faker.name").build(),
                        FieldPlan.builder().name("y").generator("faker.unknown").build()
                ))
                .build();

        assertThrows(com.platform.exception.BusinessException.class,
                () -> tableDataGenerator.generate(tablePlan));
    }

    // ==================== 多表生成上下文 ====================

    @Test
    @DisplayName("GenerationContext — 生成表数据时自动记录主键")
    void testContextAutoRecordPrimaryKey() {
        GenerationContext ctx = new GenerationContext();

        // 模拟 department 表：id 为自增主键，name 是部门名
        TablePlan deptPlan = TablePlan.builder()
                .table("department")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build()
                ))
                .build();

        List<Map<String, Object>> rows = tableDataGenerator.generate(deptPlan, ctx);

        // 验证数据行
        assertEquals(3, rows.size(), "应生成 3 行数据");

        // 验证 context 自动记录了主键
        assertTrue(ctx.hasTable("department"), "context 应包含 department");
        assertEquals(3, ctx.idCount("department"), "应记录 3 个主键");

        List<Object> ids = ctx.getIds("department");
        assertEquals(3, ids.size(), "getIds 应返回 3 个值");

        // 验证 id 值与生成数据一致
        for (int i = 0; i < 3; i++) {
            assertEquals(rows.get(i).get("id"), ids.get(i),
                    "context 中的 id 应与生成数据一致");
        }
    }

    @Test
    @DisplayName("GenerationContext — 无主键标记时不记录")
    void testContextNoPrimaryKey() {
        GenerationContext ctx = new GenerationContext();

        TablePlan plan = TablePlan.builder()
                .table("log")
                .count(5)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("message")
                                .generator("faker.word")
                                .build(),
                        FieldPlan.builder()
                                .name("level")
                                .generator("enum.values")
                                .params(Map.of("values", List.of("INFO", "WARN", "ERROR")))
                                .build()
                ))
                .build();

        List<Map<String, Object>> rows = tableDataGenerator.generate(plan, ctx);
        assertEquals(5, rows.size());

        // 没有 primaryKey=true 的字段，context 不应记录
        assertFalse(ctx.hasTable("log"), "无主键标记的表不应记录");
    }

    @Test
    @DisplayName("GenerationContext — 线程安全：getIds 返回快照副本")
    void testContextGetIdsSnapshot() {
        GenerationContext ctx = new GenerationContext();

        // 手动添加 id
        ctx.addGeneratedId("test", 1);
        ctx.addGeneratedId("test", 2);
        ctx.addGeneratedId("test", 3);

        List<Object> ids = ctx.getIds("test");
        assertEquals(List.of(1, 2, 3), ids);

        // 返回的列表是不可变的
        assertThrows(UnsupportedOperationException.class, () -> ids.add(4));
    }

    @Test
    @DisplayName("GenerationContext — getIds 未生成过的表返回空列表")
    void testContextGetIdsUnknownTable() {
        GenerationContext ctx = new GenerationContext();

        List<Object> ids = ctx.getIds("non_existent");
        assertNotNull(ids, "未生成过的表应返回非 null 列表");
        assertTrue(ids.isEmpty(), "未生成过的表应返回空列表");
        assertFalse(ctx.hasTable("non_existent"), "未生成过的表 hasTable 应返回 false");
    }

    // ==================== 外键字段生成 ====================

    @Test
    @DisplayName("外键生成 — department_id 应随机选取已生成的 department.id")
    void testForeignKeyFromContext() {
        GenerationContext ctx = new GenerationContext();

        // 1. 先生成 department 表，写入 context
        TablePlan deptPlan = TablePlan.builder()
                .table("department")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("dept_name")
                                .generator("faker.name")
                                .build()
                ))
                .build();
        tableDataGenerator.generate(deptPlan, ctx);

        // 验证 department 已生成
        assertTrue(ctx.hasTable("department"));
        List<Object> deptIds = ctx.getIds("department");
        assertEquals(3, deptIds.size());

        // 2. 生成 employee 表，department_id 外键引用 department.id
        TablePlan empPlan = TablePlan.builder()
                .table("employee")
                .count(10)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build(),
                        FieldPlan.builder()
                                .name("department_id")
                                .generator(null) // 不设置 generator，走 FK
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("department")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        List<Map<String, Object>> empRows = tableDataGenerator.generate(empPlan, ctx);
        assertEquals(10, empRows.size());

        // 3. 验证：每行的 department_id 都是 department.id 中的一个
        for (Map<String, Object> row : empRows) {
            Object deptId = row.get("department_id");
            assertNotNull(deptId, "department_id 不应为 null");
            assertTrue(deptIds.contains(deptId),
                    "department_id " + deptId + " 应在 " + deptIds + " 中");
        }
    }

    @Test
    @DisplayName("外键生成 — 关联表无数据时应抛异常")
    void testForeignKeyMissingTable() {
        GenerationContext ctx = new GenerationContext();
        // 不生成任何表数据，直接引外键

        TablePlan empPlan = TablePlan.builder()
                .table("employee")
                .count(5)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build(),
                        FieldPlan.builder()
                                .name("department_id")
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("department")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        assertThrows(com.platform.exception.BusinessException.class,
                () -> tableDataGenerator.generate(empPlan, ctx),
                "关联表 department 无数据时应抛 BusinessException");
    }

    // ==================== 多表生成调度 ====================

    @Test
    @DisplayName("多表生成 — 2 层依赖：department → employee 自动排序")
    void testMultiTableTwoLevelOrder() {
        // department 表（被依赖方）
        TablePlan deptPlan = TablePlan.builder()
                .table("department")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("dept_name")
                                .generator("faker.name")
                                .build()
                ))
                .build();

        // employee 表（依赖方，有 FK 指向 department）
        TablePlan empPlan = TablePlan.builder()
                .table("employee")
                .count(5)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build(),
                        FieldPlan.builder()
                                .name("department_id")
                                .generator(null)
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("department")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        // 故意打乱输入顺序：employee 在前，department 在后
        List<TablePlan> plans = new ArrayList<>();
        plans.add(empPlan);
        plans.add(deptPlan);

        MultiTableGenerateResponse response = multiTableDataGenerator.generate(plans);

        // 验证成功
        assertTrue(response.isSuccess(), "多表生成应成功");
        assertEquals(2, response.getTables().size(), "应返回 2 张表的结果");

        // 验证顺序：department 应先于 employee
        TableResult first = response.getTables().get(0);
        TableResult second = response.getTables().get(1);
        assertEquals("department", first.getTable(), "第一张表应为 department（被依赖表在前）");
        assertEquals("employee", second.getTable(), "第二张表应为 employee（依赖表在后）");

        // 验证数据量
        assertEquals(3, first.getCount(), "department 应生成 3 行");
        assertEquals(5, second.getCount(), "employee 应生成 5 行");

        // 验证 employee 的 department_id 都来自 department 的 id
        List<Map<String, Object>> deptData = first.getData();
        List<Object> deptIds = new ArrayList<>();
        for (Map<String, Object> row : deptData) {
            deptIds.add(row.get("id"));
        }

        List<Map<String, Object>> empData = second.getData();
        for (Map<String, Object> row : empData) {
            Object deptId = row.get("department_id");
            assertNotNull(deptId, "department_id 不应为 null");
            assertTrue(deptIds.contains(deptId),
                    "employee.department_id " + deptId + " 应来自 department.id " + deptIds);
        }
    }

    @Test
    @DisplayName("多表生成 — 3 层依赖：department → employee → task")
    void testMultiTableThreeLevelOrder() {
        // department 表
        TablePlan deptPlan = TablePlan.builder()
                .table("department")
                .count(2)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build()
                ))
                .build();

        // employee 表（FK → department）
        TablePlan empPlan = TablePlan.builder()
                .table("employee")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(1000).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("name")
                                .generator("faker.name")
                                .build(),
                        FieldPlan.builder()
                                .name("department_id")
                                .generator(null)
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("department")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        // task 表（FK → employee）
        TablePlan taskPlan = TablePlan.builder()
                .table("task")
                .count(4)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("title")
                                .generator("faker.word")
                                .build(),
                        FieldPlan.builder()
                                .name("assignee_id")
                                .generator(null)
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("employee")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        // 故意打乱顺序
        List<TablePlan> plans = List.of(taskPlan, deptPlan, empPlan);

        MultiTableGenerateResponse response = multiTableDataGenerator.generate(plans);

        // 验证成功
        assertTrue(response.isSuccess());
        assertEquals(3, response.getTables().size());

        // 验证排序：department → employee → task
        assertEquals("department", response.getTables().get(0).getTable(),
                "第 1 张应为 department（最底层被依赖表）");
        assertEquals("employee", response.getTables().get(1).getTable(),
                "第 2 张应为 employee");
        assertEquals("task", response.getTables().get(2).getTable(),
                "第 3 张应为 task（最上层依赖表）");

        // 验证数据量
        assertEquals(2, response.getTables().get(0).getCount());
        assertEquals(3, response.getTables().get(1).getCount());
        assertEquals(4, response.getTables().get(2).getCount());

        // 验证 task.assignee_id 来自 employee.id
        List<Object> empIds = new ArrayList<>();
        for (Map<String, Object> row : response.getTables().get(1).getData()) {
            empIds.add(row.get("id"));
        }
        for (Map<String, Object> row : response.getTables().get(2).getData()) {
            assertTrue(empIds.contains(row.get("assignee_id")),
                    "task.assignee_id 应来自 employee.id");
        }
    }

    @Test
    @DisplayName("多表生成 — 循环依赖 A→B, B→A 应抛异常")
    void testMultiTableCircularDependency() {
        // 表 A: FK → B
        TablePlan planA = TablePlan.builder()
                .table("A")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("b_id")
                                .generator(null)
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("B")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        // 表 B: FK → A
        TablePlan planB = TablePlan.builder()
                .table("B")
                .count(3)
                .fields(List.of(
                        FieldPlan.builder()
                                .name("id")
                                .generator("random.integer")
                                .range(Range.builder().min(1).max(100).build())
                                .params(Map.of("primaryKey", true))
                                .build(),
                        FieldPlan.builder()
                                .name("a_id")
                                .generator(null)
                                .foreignKey(ForeignKeyInfo.builder()
                                        .table("A")
                                        .column("id")
                                        .build())
                                .build()
                ))
                .build();

        List<TablePlan> plans = List.of(planA, planB);

        assertThrows(com.platform.exception.BusinessException.class,
                () -> multiTableDataGenerator.generate(plans),
                "循环依赖应抛出 BusinessException");
    }

    // ==================== SQL INSERT 生成 ====================

    @Test
    @DisplayName("SQL 生成 — 单条数据应生成正确的 INSERT 语句")
    void testInsertSqlSingleRow() {
        List<Map<String, Object>> data = List.of(
                Map.of("username", "张三", "age", 20)
        );

        String sql = insertSqlBuilder.build("user", data);

        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO `user`"), "SQL 应包含表名");
        assertTrue(sql.contains("username"), "SQL 应包含列名 username");
        assertTrue(sql.contains("age"), "SQL 应包含列名 age");
        assertTrue(sql.contains("'张三'"), "SQL 应包含转义后的字符串值");
        assertTrue(sql.contains("20"), "SQL 应包含整数值");
        assertTrue(sql.endsWith(";"), "SQL 应以分号结尾");
        assertTrue(sql.contains("VALUES"), "SQL 应包含 VALUES 关键字");
    }

    @Test
    @DisplayName("SQL 生成 — 100 条数据应批量生成在一个 INSERT 中")
    void testInsertSqlBatch100Rows() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.add(Map.of("id", i + 1, "name", "user" + i));
        }

        String sql = insertSqlBuilder.build("users", data);

        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO `users`"), "SQL 应包含表名");
        assertTrue(sql.endsWith(";"), "SQL 应以分号结尾");

        // 批量 INSERT: 应有 100 个值行（VALUES 之后）
        String afterValues = sql.substring(sql.indexOf("VALUES\n") + "VALUES\n".length());
        long valueRowCount = afterValues.lines()
                .filter(line -> line.trim().startsWith("("))
                .count();
        assertEquals(100, valueRowCount, "批量 INSERT 应包含 100 行值");

        // 不应有多个 INSERT 语句
        long insertCount = sql.split("INSERT INTO").length - 1;
        assertEquals(1, insertCount, "应只有一条 INSERT 语句");
    }

    @Test
    @DisplayName("SQL 生成 — 字符串特殊字符应正确转义")
    void testInsertSqlStringEscape() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "张三'测试")
        );

        String sql = insertSqlBuilder.build("user", data);

        assertNotNull(sql);
        // 单引号应被转义为两个单引号
        assertTrue(sql.contains("''测试'"),
                "单引号应转义为 ''，实际 SQL: " + sql);
        // 确认不是 \" 转义（MySQL 使用 '' 而非 \'）
        assertFalse(sql.contains("\\'"), "不应使用反斜杠转义");
    }

    @Test
    @DisplayName("SQL 生成 — null 值应输出为 NULL")
    void testInsertSqlNullValue() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("name", "test");
        row.put("age", null);
        List<Map<String, Object>> data = List.of(row);

        String sql = insertSqlBuilder.build("user", data);

        assertNotNull(sql);
        assertTrue(sql.contains("NULL"), "null 值应输出为 NULL，实际 SQL: " + sql);
        assertTrue(sql.contains("'test'"), "SQL 应包含字符串值");
    }

    // ==================== InsertStatementBuilder ====================

    @Test
    @DisplayName("参数化 SQL — 应生成带 ? 占位符的 INSERT 语句")
    void testInsertStatementBuilderBasic() {
        String sql = insertStatementBuilder.buildSql("user", List.of("name", "age", "email"));

        assertNotNull(sql);
        assertEquals("INSERT INTO `user` (`name`, `age`, `email`) VALUES (?, ?, ?)", sql,
                "应生成参数化 INSERT SQL");
        assertTrue(sql.contains("?"), "应包含占位符");
        assertFalse(sql.contains("'"), "参数化 SQL 不应包含引号");
    }

    @Test
    @DisplayName("参数化 SQL — 单列表也应正确生成")
    void testInsertStatementBuilderSingleColumn() {
        String sql = insertStatementBuilder.buildSql("log", List.of("message"));

        assertNotNull(sql);
        assertEquals("INSERT INTO `log` (`message`) VALUES (?)", sql);
    }
}
