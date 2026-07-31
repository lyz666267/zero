package com.platform.controller;

import com.platform.dto.*;
import com.platform.entity.schema.SchemaColumn;
import com.platform.privacy.SensitiveFieldType;
import com.platform.privacy.executor.MaskExecutor;
import com.platform.privacy.mask.MaskRule;
import com.platform.privacy.mask.MaskRuleRegistry;
import com.platform.privacy.mask.MaskStrategy;
import com.platform.privacy.service.PrivacyAwareDataProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 隐私脱敏接口
 *
 * <p>将脱敏能力暴露为 REST API，供前端和下游服务调用。</p>
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>POST /api/privacy/process — 手动指定敏感字段后脱敏</li>
 *   <li>POST /api/privacy/process-auto — 三层自动检测 + 脱敏一站式处理</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private static final Logger log = LoggerFactory.getLogger(PrivacyController.class);

    private final PrivacyAwareDataProcessor processor;
    private final MaskRuleRegistry maskRuleRegistry;
    private final MaskExecutor maskExecutor;

    // ==================== 敏感类型 → 中文标签映射 ====================
    private static final Map<SensitiveFieldType, String> TYPE_LABELS = Map.of(
            SensitiveFieldType.PHONE, "手机号",
            SensitiveFieldType.EMAIL, "邮箱",
            SensitiveFieldType.ID_CARD, "身份证号",
            SensitiveFieldType.NAME, "姓名",
            SensitiveFieldType.ADDRESS, "地址",
            SensitiveFieldType.BANK_CARD, "银行卡号"
    );

    // ==================== 敏感类型 → 示例输入 ====================
    private static final Map<SensitiveFieldType, String> TYPE_EXAMPLES = Map.of(
            SensitiveFieldType.PHONE, "13812345678",
            SensitiveFieldType.EMAIL, "zhangsan@gmail.com",
            SensitiveFieldType.ID_CARD, "110101199001011234",
            SensitiveFieldType.NAME, "张三",
            SensitiveFieldType.ADDRESS, "北京市朝阳区建国路100号",
            SensitiveFieldType.BANK_CARD, "6222021234567890123"
    );

    /**
     * 对数据行执行脱敏处理（手动指定敏感字段）
     *
     * <p>接收数据行和已识别的敏感字段列表，返回脱敏后的数据。
     * 调用方通常先通过 SensitiveFieldDetector 识别敏感字段，再调用本接口执行脱敏。</p>
     */
    @PostMapping("/process")
    public PrivacyProcessResponse process(@RequestBody PrivacyProcessRequest request) {
        log.info("收到脱敏请求: {} 行数据, {} 个敏感字段",
                request.getData() != null ? request.getData().size() : 0,
                request.getSensitiveFields() != null ? request.getSensitiveFields().size() : 0);

        var maskedData = processor.process(request.getData(), request.getSensitiveFields());

        return new PrivacyProcessResponse(true, maskedData);
    }

    /**
     * 自动检测并脱敏（Phase 8.1 新增）
     *
     * <p>使用三层融合检测器（Regex → Keyword → LLM）自动识别敏感字段，
     * 无需调用方预先做敏感字段识别。内部流程：</p>
     * <ol>
     *   <li>columns + sampleData → DetectionContext</li>
     *   <li>CompositeSensitiveDetector.detect() → 三层融合检测</li>
     *   <li>PrivacyAwareDataProcessor.process() → 执行脱敏</li>
     * </ol>
     */
    @PostMapping("/process-auto")
    public PrivacyProcessResponse processAuto(@RequestBody PrivacyProcessAutoRequest request) {
        log.info("收到自动脱敏请求: {} 行数据, {} 列",
                request.getData() != null ? request.getData().size() : 0,
                request.getColumns() != null ? request.getColumns().size() : 0);

        // 转换为 SchemaColumn 列表（仅设置检测所需字段）
        List<SchemaColumn> columns = request.getColumns().stream()
                .map(info -> {
                    SchemaColumn col = new SchemaColumn();
                    col.setColumnName(info.getColumnName());
                    col.setColumnType(info.getColumnType());
                    col.setColumnComment(info.getColumnComment());
                    col.setDataType(info.getDataType());
                    return col;
                })
                .toList();

        var maskedData = processor.processAuto(request.getData(), columns);

        return new PrivacyProcessResponse(true, maskedData);
    }

    /**
     * 获取所有脱敏规则（Phase 8.2-2 新增）
     *
     * <p>返回所有已注册的脱敏规则，包含类型、策略、描述和前后对比示例，
     * 供前端 MaskConfig 页面展示和可视化使用。</p>
     */
    @GetMapping("/rules")
    public List<MaskRuleResponse> getRules() {
        List<MaskRule> rules = maskRuleRegistry.listAll();
        List<MaskRuleResponse> responses = new ArrayList<>();

        for (MaskRule rule : rules) {
            SensitiveFieldType type = rule.type();
            MaskStrategy strategy = rule.strategy();

            // 获取示例值并执行脱敏
            String exampleInput = TYPE_EXAMPLES.getOrDefault(type, "");
            String exampleOutput = "";
            if (!exampleInput.isEmpty()) {
                try {
                    exampleOutput = maskExecutor.mask(exampleInput, strategy);
                } catch (Exception e) {
                    log.warn("示例值脱敏失败: type={}, strategy={}", type, strategy, e);
                    exampleOutput = "[脱敏失败]";
                }
            }

            responses.add(new MaskRuleResponse(
                    type.name(),
                    TYPE_LABELS.getOrDefault(type, type.name()),
                    strategy.name(),
                    rule.description(),
                    exampleInput,
                    exampleOutput
            ));
        }

        log.info("返回 {} 条脱敏规则", responses.size());
        return responses;
    }

    /**
     * 测试脱敏效果（Phase 8.2-2 新增）
     *
     * <p>接收策略和原始值，返回脱敏后的值。用于前端实时预览脱敏效果。</p>
     */
    @PostMapping("/test")
    public MaskTestResponse test(@RequestBody MaskTestRequest request) {
        // 解析策略
        MaskStrategy strategy;
        try {
            strategy = MaskStrategy.valueOf(request.getStrategy());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的脱敏策略: " + request.getStrategy());
        }

        // 执行脱敏
        String maskedValue = maskExecutor.mask(request.getValue(), strategy);

        log.info("脱敏测试: strategy={}, '{}' → '{}'", request.getStrategy(), request.getValue(), maskedValue);

        return new MaskTestResponse(
                request.getStrategy(),
                request.getValue(),
                maskedValue
        );
    }
}
