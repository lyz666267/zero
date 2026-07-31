package com.platform.privacy.executor;

import com.platform.privacy.mask.MaskStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏执行器测试
 *
 * <p>验证 {@link DefaultMaskExecutor} 对各策略的脱敏效果。</p>
 */
@DisplayName("脱敏执行器测试")
class MaskExecutorTest {

    private MaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultMaskExecutor();
    }

    // ==================== 1. 手机号 ====================

    @Test
    @DisplayName("手机号脱敏 — 13812345678 → 138****5678")
    void testPhoneMask() {
        String result = executor.mask("13812345678", MaskStrategy.PHONE_MASK);
        assertEquals("138****5678", result);
    }

    @Test
    @DisplayName("手机号脱敏 — 短号码仍可处理")
    void testPhoneMaskShort() {
        String result = executor.mask("1234567", MaskStrategy.PHONE_MASK);
        assertEquals("1****4567", result);
    }

    // ==================== 2. 邮箱 ====================

    @Test
    @DisplayName("邮箱脱敏 — zhangsan@gmail.com → zha***@gmail.com")
    void testEmailMask() {
        String result = executor.mask("zhangsan@gmail.com", MaskStrategy.EMAIL_MASK);
        assertEquals("zha***@gmail.com", result);
    }

    @Test
    @DisplayName("邮箱脱敏 — 短本地部分")
    void testEmailMaskShortLocal() {
        String result = executor.mask("ab@test.com", MaskStrategy.EMAIL_MASK);
        assertEquals("a***@test.com", result);
    }

    @Test
    @DisplayName("邮箱脱敏 — 无 @ 符号回退")
    void testEmailMaskNoAt() {
        String result = executor.mask("notanemail", MaskStrategy.EMAIL_MASK);
        assertEquals("n***", result);
    }

    // ==================== 3. 身份证 ====================

    @Test
    @DisplayName("身份证脱敏 — 110101199001011234 → 110101********1234")
    void testIdCardMask() {
        String result = executor.mask("110101199001011234", MaskStrategy.ID_CARD_MASK);
        assertEquals("110101********1234", result);
    }

    @Test
    @DisplayName("身份证脱敏 — 短号码回退")
    void testIdCardMaskShort() {
        String result = executor.mask("1234567890", MaskStrategy.ID_CARD_MASK);
        assertEquals("123456****", result);
    }

    // ==================== 4. 姓名 ====================

    @Test
    @DisplayName("姓名脱敏 — 张三 → 张*")
    void testNameMaskTwoChars() {
        String result = executor.mask("张三", MaskStrategy.NAME_MASK);
        assertEquals("张*", result);
    }

    @Test
    @DisplayName("姓名脱敏 — 张伟强 → 张**")
    void testNameMaskThreeChars() {
        String result = executor.mask("张伟强", MaskStrategy.NAME_MASK);
        assertEquals("张**", result);
    }

    @Test
    @DisplayName("姓名脱敏 — 单字不变")
    void testNameMaskSingleChar() {
        String result = executor.mask("张", MaskStrategy.NAME_MASK);
        assertEquals("张", result);
    }

    // ==================== 5. 地址 ====================

    @Test
    @DisplayName("地址脱敏 — 保留前6字符")
    void testAddressMask() {
        // 北京市朝阳区建国路100号 = 13 chars, keep first 6
        String result = executor.mask("北京市朝阳区建国路100号", MaskStrategy.ADDRESS_MASK);
        assertEquals("北京市朝阳区" + "*".repeat(7), result);
        assertTrue(result.startsWith("北京市朝阳区"), "应保留前6字符");
        assertEquals(13, result.length(), "脱敏后长度应不变");
    }

    @Test
    @DisplayName("地址脱敏 — 不足6字符全保留")
    void testAddressMaskShort() {
        String result = executor.mask("北京市", MaskStrategy.ADDRESS_MASK);
        assertEquals("北京市", result, "不足6字符应全保留");
    }

    // ==================== 6. 银行卡 ====================

    @Test
    @DisplayName("银行卡脱敏 — 保留后4位")
    void testBankCardMask() {
        String result = executor.mask("6222021234567890123", MaskStrategy.BANK_CARD_MASK);
        assertEquals("****0123", result);
    }

    @Test
    @DisplayName("银行卡脱敏 — 不足4位全遮蔽")
    void testBankCardMaskShort() {
        String result = executor.mask("123", MaskStrategy.BANK_CARD_MASK);
        assertEquals("****", result);
    }

    // ==================== 7. null 输入 ====================

    @Test
    @DisplayName("null 输入应返回 null")
    void testNullInput() {
        assertNull(executor.mask(null, MaskStrategy.PHONE_MASK));
        assertNull(executor.mask(null, MaskStrategy.EMAIL_MASK));
        assertNull(executor.mask(null, MaskStrategy.NAME_MASK));
    }

    // ==================== 8. 空字符串 ====================

    @Test
    @DisplayName("空字符串应返回空字符串")
    void testEmptyString() {
        String result = executor.mask("", MaskStrategy.PHONE_MASK);
        assertEquals("", result);

        String result2 = executor.mask("", MaskStrategy.NAME_MASK);
        assertEquals("", result2);
    }

    // ==================== 补充：所有策略覆盖 ====================

    @Test
    @DisplayName("所有 6 种策略均能正常执行不抛异常")
    void testAllStrategiesExecuteWithoutException() {
        String input = "test_value_12345";

        for (MaskStrategy strategy : MaskStrategy.values()) {
            String result = executor.mask(input, strategy);
            assertNotNull(result, strategy + " 不应返回 null");
            assertFalse(result.isEmpty(), strategy + " 不应返回空字符串");
        }
    }
}
