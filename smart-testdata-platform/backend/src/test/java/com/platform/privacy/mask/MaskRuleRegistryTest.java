package com.platform.privacy.mask;

import com.platform.privacy.SensitiveFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏规则注册表测试
 *
 * <p>验证 {@link MaskRuleRegistry} 的默认映射关系和边界条件。</p>
 */
@DisplayName("脱敏规则注册表测试")
class MaskRuleRegistryTest {

    private MaskRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MaskRuleRegistry();
        registry.init();
    }

    // ==================== 1. PHONE → PHONE_MASK ====================

    @Test
    @DisplayName("PHONE 类型应返回 PHONE_MASK 策略")
    void testPhoneReturnsPhoneMask() {
        Optional<MaskStrategy> strategy = registry.getStrategy(SensitiveFieldType.PHONE);

        assertTrue(strategy.isPresent(), "PHONE 应有对应策略");
        assertEquals(MaskStrategy.PHONE_MASK, strategy.get());

        Optional<MaskRule> rule = registry.lookup(SensitiveFieldType.PHONE);
        assertTrue(rule.isPresent());
        assertEquals(MaskStrategy.PHONE_MASK, rule.get().strategy());
        assertEquals(SensitiveFieldType.PHONE, rule.get().type());
        assertFalse(rule.get().description().isBlank(), "规则描述不应为空");
    }

    // ==================== 2. EMAIL → EMAIL_MASK ====================

    @Test
    @DisplayName("EMAIL 类型应返回 EMAIL_MASK 策略")
    void testEmailReturnsEmailMask() {
        Optional<MaskStrategy> strategy = registry.getStrategy(SensitiveFieldType.EMAIL);

        assertTrue(strategy.isPresent(), "EMAIL 应有对应策略");
        assertEquals(MaskStrategy.EMAIL_MASK, strategy.get());

        Optional<MaskRule> rule = registry.lookup(SensitiveFieldType.EMAIL);
        assertTrue(rule.isPresent());
        assertEquals(MaskStrategy.EMAIL_MASK, rule.get().strategy());
    }

    // ==================== 3. ID_CARD → ID_CARD_MASK ====================

    @Test
    @DisplayName("ID_CARD 类型应返回 ID_CARD_MASK 策略")
    void testIdCardReturnsIdCardMask() {
        Optional<MaskStrategy> strategy = registry.getStrategy(SensitiveFieldType.ID_CARD);

        assertTrue(strategy.isPresent(), "ID_CARD 应有对应策略");
        assertEquals(MaskStrategy.ID_CARD_MASK, strategy.get());

        Optional<MaskRule> rule = registry.lookup(SensitiveFieldType.ID_CARD);
        assertTrue(rule.isPresent());
        assertEquals(MaskStrategy.ID_CARD_MASK, rule.get().strategy());
    }

    // ==================== 4. UNKNOWN 没有规则 ====================

    @Test
    @DisplayName("UNKNOWN 类型不应有对应规则")
    void testUnknownHasNoRule() {
        Optional<MaskStrategy> strategy = registry.getStrategy(SensitiveFieldType.UNKNOWN);
        assertTrue(strategy.isEmpty(), "UNKNOWN 不应有对应策略");

        Optional<MaskRule> rule = registry.lookup(SensitiveFieldType.UNKNOWN);
        assertTrue(rule.isEmpty(), "UNKNOWN 不应有对应规则");

        assertFalse(registry.hasRule(SensitiveFieldType.UNKNOWN),
                "hasRule(UNKNOWN) 应返回 false");
    }

    // ==================== 5. 所有敏感类型映射完整 ====================

    @Test
    @DisplayName("6 种敏感类型均应有对应的脱敏规则")
    void testAllSensitiveTypesHaveRules() {
        SensitiveFieldType[] expectedTypes = {
                SensitiveFieldType.PHONE,
                SensitiveFieldType.EMAIL,
                SensitiveFieldType.ID_CARD,
                SensitiveFieldType.NAME,
                SensitiveFieldType.ADDRESS,
                SensitiveFieldType.BANK_CARD
        };

        for (SensitiveFieldType type : expectedTypes) {
            Optional<MaskRule> rule = registry.lookup(type);
            assertTrue(rule.isPresent(),
                    type + " 应有对应脱敏规则");
            assertNotNull(rule.get().strategy(),
                    type + " 的策略不应为 null");
            assertFalse(rule.get().description().isBlank(),
                    type + " 的描述不应为空");
        }

        // UNKNOWN 不应包含在内
        assertFalse(registry.hasRule(SensitiveFieldType.UNKNOWN));
    }

    // ==================== 补充：规则总数 ====================

    @Test
    @DisplayName("注册表应包含恰好 6 条规则")
    void testRuleCount() {
        List<MaskRule> all = registry.listAll();
        assertEquals(6, all.size(), "注册表应包含恰好 6 条规则");
    }

    @Test
    @DisplayName("listAll 返回的列表不应包含 UNKNOWN")
    void testListAllExcludesUnknown() {
        List<MaskRule> all = registry.listAll();
        boolean hasUnknown = all.stream()
                .anyMatch(r -> r.type() == SensitiveFieldType.UNKNOWN);
        assertFalse(hasUnknown, "listAll 不应包含 UNKNOWN 规则");
    }

    // ==================== 补充：策略唯一性 ====================

    @Test
    @DisplayName("每种敏感类型应有唯一的策略")
    void testStrategyUniqueness() {
        SensitiveFieldType[] types = {
                SensitiveFieldType.PHONE,
                SensitiveFieldType.EMAIL,
                SensitiveFieldType.ID_CARD,
                SensitiveFieldType.NAME,
                SensitiveFieldType.ADDRESS,
                SensitiveFieldType.BANK_CARD
        };

        for (SensitiveFieldType type : types) {
            MaskStrategy s1 = registry.getStrategy(type).orElseThrow();
            MaskStrategy s2 = registry.lookup(type).orElseThrow().strategy();
            assertEquals(s1, s2,
                    "lookup 和 getStrategy 应返回一致的结果: " + type);
        }
    }
}
