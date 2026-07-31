package com.platform.privacy.mask;

import com.platform.privacy.SensitiveFieldType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 脱敏规则注册表
 *
 * <h3>职责</h3>
 * <p>维护 {@link SensitiveFieldType} → {@link MaskRule} 的默认映射关系，
 * 供下游组件根据识别出的敏感字段类型查询对应脱敏策略。</p>
 *
 * <h3>默认映射</h3>
 * <table>
 *   <tr><th>SensitiveFieldType</th><th>MaskStrategy</th></tr>
 *   <tr><td>PHONE</td><td>PHONE_MASK</td></tr>
 *   <tr><td>EMAIL</td><td>EMAIL_MASK</td></tr>
 *   <tr><td>ID_CARD</td><td>ID_CARD_MASK</td></tr>
 *   <tr><td>NAME</td><td>NAME_MASK</td></tr>
 *   <tr><td>ADDRESS</td><td>ADDRESS_MASK</td></tr>
 *   <tr><td>BANK_CARD</td><td>BANK_CARD_MASK</td></tr>
 * </table>
 *
 * <p>UNKNOWN 类型无对应规则，{@link #lookup(SensitiveFieldType)} 返回 {@code Optional.empty()}。</p>
 *
 * <h3>设计原则</h3>
 * <p>只负责策略映射，不执行真正的字符串替换。
 * 识别（{@link com.platform.privacy.SensitiveFieldDetector}）与执行分离。</p>
 */
@Slf4j
@Component
public class MaskRuleRegistry {

    private final Map<SensitiveFieldType, MaskRule> rules = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        register(SensitiveFieldType.PHONE, MaskStrategy.PHONE_MASK,
                "手机号脱敏：保留前3后4，中间用 **** 替换");
        register(SensitiveFieldType.EMAIL, MaskStrategy.EMAIL_MASK,
                "邮箱脱敏：保留首字符和 @ 后域名，中间用 *** 替换");
        register(SensitiveFieldType.ID_CARD, MaskStrategy.ID_CARD_MASK,
                "身份证号脱敏：保留前6后4，中间用 **** 替换");
        register(SensitiveFieldType.NAME, MaskStrategy.NAME_MASK,
                "姓名脱敏：保留首字，其余用 * 替换");
        register(SensitiveFieldType.ADDRESS, MaskStrategy.ADDRESS_MASK,
                "地址脱敏：保留省市，详细地址用 ** 替换");
        register(SensitiveFieldType.BANK_CARD, MaskStrategy.BANK_CARD_MASK,
                "银行卡号脱敏：保留后4位，其余用 **** 替换");

        log.info("脱敏规则注册完成: 共 {} 条规则", rules.size());
    }

    /**
     * 根据敏感字段类型查询脱敏规则
     *
     * @param type 敏感字段类型
     * @return 对应的脱敏规则，UNKNOWN 返回 {@code Optional.empty()}
     */
    public Optional<MaskRule> lookup(SensitiveFieldType type) {
        return Optional.ofNullable(rules.get(type));
    }

    /**
     * 根据敏感字段类型直接获取脱敏策略
     *
     * @param type 敏感字段类型
     * @return 对应的脱敏策略，无匹配返回 {@code Optional.empty()}
     */
    public Optional<MaskStrategy> getStrategy(SensitiveFieldType type) {
        MaskRule rule = rules.get(type);
        return rule != null ? Optional.of(rule.strategy()) : Optional.empty();
    }

    /**
     * 获取所有已注册的脱敏规则（只读视图）
     */
    public List<MaskRule> listAll() {
        return List.copyOf(rules.values());
    }

    /**
     * 检查某个敏感类型是否有对应的脱敏规则
     */
    public boolean hasRule(SensitiveFieldType type) {
        return rules.containsKey(type);
    }

    /**
     * 注册一条脱敏规则
     */
    private void register(SensitiveFieldType type, MaskStrategy strategy, String description) {
        rules.put(type, new MaskRule(type, strategy, description));
    }
}
