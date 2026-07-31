package com.platform.privacy.executor;

import com.platform.privacy.mask.MaskStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认脱敏执行器
 *
 * <h3>脱敏规则</h3>
 * <table>
 *   <tr><th>策略</th><th>规则</th><th>示例</th></tr>
 *   <tr><td>PHONE_MASK</td><td>保留前3后4，中间替换为 ****</td><td>138****5678</td></tr>
 *   <tr><td>EMAIL_MASK</td><td>保留前3个字符和 @ 后域名，中间替换为 ***</td><td>zha***@gmail.com</td></tr>
 *   <tr><td>ID_CARD_MASK</td><td>保留前6后4，中间替换为 ****</td><td>110101********1234</td></tr>
 *   <tr><td>NAME_MASK</td><td>保留首字，其余替换为 *</td><td>张* / 张**</td></tr>
 *   <tr><td>ADDRESS_MASK</td><td>保留前6个字符，其余替换为 *</td><td>北京市朝阳区****</td></tr>
 *   <tr><td>BANK_CARD_MASK</td><td>保留后4位，其余替换为 ****</td><td>****1234</td></tr>
 * </table>
 *
 * <h3>边界处理</h3>
 * <ul>
 *   <li>{@code null} 输入 → 返回 {@code null}</li>
 *   <li>空字符串 → 返回空字符串</li>
 *   <li>值长度不足时尽量保留可见部分，不抛异常</li>
 * </ul>
 */
@Slf4j
@Component
public class DefaultMaskExecutor implements MaskExecutor {

    @Override
    public String mask(String value, MaskStrategy strategy) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        return switch (strategy) {
            case PHONE_MASK     -> maskPhone(value);
            case EMAIL_MASK     -> maskEmail(value);
            case ID_CARD_MASK   -> maskIdCard(value);
            case NAME_MASK      -> maskName(value);
            case ADDRESS_MASK   -> maskAddress(value);
            case BANK_CARD_MASK -> maskBankCard(value);
        };
    }

    // ==================== 各策略实现 ====================

    /**
     * 手机号脱敏：保留前3后4，中间替换为 ****
     */
    private String maskPhone(String value) {
        if (value.length() <= 7) {
            return value.charAt(0) + "****" + value.substring(Math.max(1, value.length() - 4));
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /**
     * 邮箱脱敏：保留前3个字符和 @ 后域名，中间替换为 ***
     */
    private String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            // 非标准邮箱，回退到首字符 + *** 的简单脱敏
            return value.charAt(0) + "***";
        }
        String localPart = value.substring(0, atIndex);
        String domain = value.substring(atIndex);

        if (localPart.length() <= 3) {
            return localPart.charAt(0) + "***" + domain;
        }
        return localPart.substring(0, 3) + "***" + domain;
    }

    /**
     * 身份证号脱敏：保留前6后4，中间替换为 ********
     */
    private String maskIdCard(String value) {
        if (value.length() <= 10) {
            return value.substring(0, Math.min(6, value.length())) + "****";
        }
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }

    /**
     * 姓名脱敏：保留首字，其余每个字替换为 *
     */
    private String maskName(String value) {
        if (value.length() <= 1) {
            return value;
        }
        return value.charAt(0) + "*".repeat(value.length() - 1);
    }

    /**
     * 地址脱敏：保留前6个字符，其余替换为 *
     */
    private String maskAddress(String value) {
        int keep = Math.min(6, value.length());
        if (keep >= value.length()) {
            return value;
        }
        return value.substring(0, keep) + "*".repeat(value.length() - keep);
    }

    /**
     * 银行卡号脱敏：保留后4位，前面全部替换为 ****
     */
    private String maskBankCard(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
