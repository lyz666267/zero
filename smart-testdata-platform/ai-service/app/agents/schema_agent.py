"""
Schema 理解 Agent

职责：
    接收数据库 Schema JSON → 输出字段语义标签 + 敏感字段标识 + 外键推断 + 生成器推荐

流程：
    1. 解析 Schema 请求（database + tables + columns）
    2. 调用 DeepSeek LLM 进行深度语义分析
    3. 若无 API Key，降级为规则引擎 Mock（字段名模式匹配）
"""
import asyncio
import json
from loguru import logger

from app.schemas.schema_analysis import (
    SchemaAnalyzeRequest, SchemaAnalyzeResponse,
    SchemaAnalysisResult, AnalysisSummary,
    AnalyzedTable, AnalyzedColumn,
    SensitiveDetection, InferredForeignKey, GeneratorSuggestion,
)
from app.llm import llm_router, RouterExhaustedError, LLMProviderError
from app.prompts.schema_prompt import SCHEMA_ANALYSIS_SYSTEM_PROMPT


# ============================================================
# 规则引擎 — 语义标签映射（Mock 模式使用）
# ============================================================

# 字段名 → 语义标签
SEMANTIC_LABEL_MAP = {
    # 姓名
    "name": "PERSON_NAME", "username": "PERSON_NAME", "full_name": "PERSON_NAME",
    "first_name": "PERSON_NAME", "last_name": "PERSON_NAME", "nickname": "PERSON_NAME",

    # 邮箱
    "email": "EMAIL", "mail": "EMAIL", "e_mail": "EMAIL",

    # 电话
    "phone": "PHONE", "mobile": "PHONE", "tel": "PHONE", "telephone": "PHONE",
    "phone_number": "PHONE", "cellphone": "PHONE",

    # 身份证/证件
    "id_card": "ID_CARD", "idcard": "ID_CARD", "id_number": "ID_CARD",
    "ssn": "ID_CARD", "passport": "ID_CARD", "card_no": "ID_CARD",

    # 地址
    "address": "ADDRESS", "addr": "ADDRESS", "location": "ADDRESS",
    "city": "ADDRESS", "province": "ADDRESS", "country": "ADDRESS",
    "zip": "ADDRESS", "zip_code": "ADDRESS", "postcode": "ADDRESS",

    # 银行卡
    "bank_card": "BANK_CARD", "bankcard": "BANK_CARD", "bank_account": "BANK_CARD",
    "credit_card": "BANK_CARD",

    # 金额
    "price": "AMOUNT", "amount": "AMOUNT", "money": "AMOUNT",
    "salary": "AMOUNT", "balance": "AMOUNT", "fee": "AMOUNT",
    "total": "AMOUNT", "discount": "AMOUNT", "cost": "AMOUNT",

    # 日期时间
    "created_at": "DATE_TIME", "updated_at": "DATE_TIME",
    "create_time": "DATE_TIME", "update_time": "DATE_TIME",
    "datetime": "DATE_TIME", "timestamp": "DATE_TIME",
    "date": "DATE_TIME", "time": "DATE_TIME",
    "birthday": "DATE_TIME", "birth_date": "DATE_TIME",

    # 布尔标记
    "status": "ENUM_VALUE", "state": "ENUM_VALUE",
    "type": "ENUM_VALUE", "category": "ENUM_VALUE",
    "gender": "ENUM_VALUE", "sex": "ENUM_VALUE",
    "level": "ENUM_VALUE", "role": "ENUM_VALUE",

    # URL
    "url": "URL_PATH", "website": "URL_PATH", "link": "URL_PATH",
    "domain": "URL_PATH", "ip": "URL_PATH", "ip_address": "URL_PATH",
    "path": "URL_PATH",

    # 文本
    "description": "TEXT_CONTENT", "content": "TEXT_CONTENT",
    "remark": "TEXT_CONTENT", "note": "TEXT_CONTENT",
    "comment": "TEXT_CONTENT", "bio": "TEXT_CONTENT",
    "summary": "TEXT_CONTENT", "detail": "TEXT_CONTENT",

    # 标识符
    "uuid": "IDENTIFIER", "code": "IDENTIFIER", "no": "IDENTIFIER",
    "key": "IDENTIFIER", "token": "IDENTIFIER", "serial": "IDENTIFIER",
}

# 语义标签 → 敏感类型
SEMANTIC_TO_SENSITIVE = {
    "PERSON_NAME": ("NAME", 0.95),
    "EMAIL": ("EMAIL", 0.90),
    "PHONE": ("PHONE", 0.95),
    "ID_CARD": ("ID_CARD", 0.95),
    "ADDRESS": ("ADDRESS", 0.85),
    "BANK_CARD": ("BANK_CARD", 0.90),
    "AMOUNT": ("NONE", 0.0),       # 金额不一定是个人敏感
    "DATE_TIME": ("NONE", 0.0),
    "BOOLEAN_FLAG": ("NONE", 0.0),
    "ENUM_VALUE": ("NONE", 0.0),
    "IDENTIFIER": ("NONE", 0.0),
    "TEXT_CONTENT": ("NONE", 0.0),
    "URL_PATH": ("NONE", 0.0),
    "UNKNOWN": ("NONE", 0.0),
}

# 语义标签 → 生成器
SEMANTIC_TO_GENERATOR = {
    "PERSON_NAME": ("faker.name", "字段名匹配姓名语义"),
    "EMAIL": ("faker.email", "字段名匹配邮箱语义"),
    "PHONE": ("faker.phone_number", "字段名匹配手机号语义"),
    "ID_CARD": ("faker.ssn", "字段名匹配证件号语义"),
    "ADDRESS": ("faker.address", "字段名匹配地址语义"),
    "BANK_CARD": ("faker.ssn", "字段名匹配银行卡号语义"),
    "AMOUNT": ("random.decimal", "字段名匹配金额语义"),
    "DATE_TIME": ("time.past_datetime", "字段名匹配日期时间语义"),
    "BOOLEAN_FLAG": ("random.boolean", "is_/has_ 前缀匹配布尔语义"),
    "ENUM_VALUE": ("enum.values", "status/type 等字段匹配枚举语义"),
    "IDENTIFIER": ("uuid", "字段名匹配标识符语义"),
    "TEXT_CONTENT": ("faker.text", "字段名匹配文本内容语义"),
    "URL_PATH": ("faker.url", "字段名匹配 URL 语义"),
    "UNKNOWN": ("faker.word", "默认按 VARCHAR 类型生成"),
}

# MySQL 类型 → 默认生成器（无语义标签时使用）
TYPE_TO_GENERATOR = {
    "varchar": "faker.word", "char": "faker.word",
    "text": "faker.text", "longtext": "faker.text",
    "mediumtext": "faker.text", "tinytext": "faker.word",
    "int": "random.integer", "bigint": "random.integer",
    "smallint": "random.integer", "tinyint": "random.integer",
    "mediumint": "random.integer",
    "decimal": "random.decimal", "float": "random.decimal",
    "double": "random.decimal",
    "datetime": "time.past_datetime", "timestamp": "time.past_datetime",
    "date": "time.past_date", "time": "time.time",
    "boolean": "random.boolean", "bool": "random.boolean",
    "json": "faker.json",
}


class SchemaAgent:
    """
    Schema 理解 Agent

    两种模式：
    1. LLM 模式（有 API Key）— 调用 DeepSeek 进行深度语义分析
    2. Mock 模式（无 API Key）— 基于规则引擎进行字段名模式匹配
    """

    def __init__(self):
        self.has_llm = llm_router.has_llm
        logger.info(f"SchemaAgent 初始化: mode={'LLM' if self.has_llm else 'MOCK'}")

    async def analyze(self, request: SchemaAnalyzeRequest) -> SchemaAnalyzeResponse:
        """
        Schema 分析入口

        Args:
            request: 包含 database + tables + columns 的完整 Schema

        Returns:
            SchemaAnalyzeResponse 包含分析结果或错误信息
        """
        tables = request.tables

        if not tables:
            return SchemaAnalyzeResponse(
                success=False,
                error="Schema 中没有表信息，请先提供数据库表结构",
                mock=False,
            )

        if self.has_llm:
            try:
                return await self._analyze_with_llm(request)
            except (LLMProviderError, RouterExhaustedError, json.JSONDecodeError, asyncio.TimeoutError) as e:
                logger.warning(f"LLM 分析失败，降级为 Mock 模式: {e}")
                return self._analyze_mock(request)

        return self._analyze_mock(request)

    async def _analyze_with_llm(self,
                                 request: SchemaAnalyzeRequest) -> SchemaAnalyzeResponse:
        """调用 DeepSeek LLM 进行深度 Schema 语义分析"""
        schema_desc = self._build_schema_description(request)

        user_prompt = f"""## 数据库 Schema

{schema_desc}

请对该 Schema 进行完整的语义分析。"""

        try:
            messages = [
                {"role": "system", "content": SCHEMA_ANALYSIS_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ]

            # 通过 LLMRouter 调用（自动处理 DeepSeek → Qwen 故障切换）
            response_text = await llm_router.chat(
                messages, temperature=0.1, max_tokens=8192
            )

            # 提取 JSON 并解析
            json_text = llm_router.extract_json(response_text)
            result_dict = json.loads(json_text)

            result = self._parse_llm_result(result_dict, request)
            logger.info(
                f"LLM Schema 分析完成: database={result.database}, "
                f"tables={result.summary.totalTables}, "
                f"columns={result.summary.totalColumns}, "
                f"sensitive={result.summary.sensitiveColumns}"
            )

            return SchemaAnalyzeResponse(
                success=True,
                result=result,
                mock=False,
            )

        except RouterExhaustedError as e:
            # 所有 LLM 模型均失败 → 降级为 Mock
            logger.warning(f"所有 LLM 模型均已失败，降级为 Mock 模式: {e}")
            return self._analyze_mock(request)

        except json.JSONDecodeError as e:
            logger.error(f"LLM Schema 分析 JSON 解析失败: {e}")
            logger.warning("降级为 Mock 模式")
            return self._analyze_mock(request)

        except LLMProviderError as e:
            logger.error(f"LLM Schema 分析调用失败，降级为 Mock 模式: {e}")
            return self._analyze_mock(request)

        except asyncio.TimeoutError:
            logger.error("LLM Schema 分析超时，降级为 Mock 模式")
            return self._analyze_mock(request)

    def _analyze_mock(self, request: SchemaAnalyzeRequest) -> SchemaAnalyzeResponse:
        """
        规则引擎 Mock 模式

        基于字段名关键词匹配进行语义标签、敏感检测、生成器推荐。
        不调用 LLM，保证在没有 API Key 时也能返回有意义的结果。
        """
        analyzed_tables = []
        total_columns = 0
        total_sensitive = 0
        total_fk = 0
        recommendations = []

        # 收集所有表名用于外键推断
        all_table_names = {t.tableName.lower() for t in request.tables}

        for table in request.tables:
            analyzed_columns = []
            pk_columns = []

            for col in table.columns:
                # 收集主键
                if col.primaryKey:
                    pk_columns.append(col.name)

                # 语义标签匹配
                label, match_type = self._match_semantic_label(col.name, col.comment)

                # 敏感检测
                sensitive_type, confidence = SEMANTIC_TO_SENSITIVE.get(
                    label, ("NONE", 0.0)
                )
                is_sensitive = sensitive_type != "NONE"
                if is_sensitive:
                    total_sensitive += 1

                # 外键推断
                fk = self._infer_foreign_key(col.name, col.type, all_table_names)

                # 生成器推荐
                generator_suggestion = self._suggest_generator(
                    col.name, col.type, label, col.primaryKey
                )

                analyzed_columns.append(AnalyzedColumn(
                    name=col.name,
                    type=col.type,
                    nullable=col.nullable,
                    defaultValue=col.defaultValue,
                    comment=col.comment,
                    semanticLabel=label,
                    sensitiveDetection=SensitiveDetection(
                        sensitive=is_sensitive,
                        sensitiveType=sensitive_type,
                        confidence=confidence,
                    ),
                    inferredForeignKey=fk,
                    generatorSuggestion=generator_suggestion,
                ))

                if fk:
                    total_fk += 1

                total_columns += 1

            # 表级建议
            table_names_lower = [t.tableName.lower() for t in request.tables]
            for col in table.columns:
                if col.name.lower().endswith("_id"):
                    ref_table = col.name.lower()[:-3]  # 去掉 _id
                    if ref_table in table_names_lower:
                        recommendations.append(
                            f"表 {table.tableName} 的 {col.name} 可能引用表 {ref_table}，建议按依赖顺序生成"
                        )

            # 去重
            recommendations = list(dict.fromkeys(recommendations))

            analyzed_tables.append(AnalyzedTable(
                tableName=table.tableName,
                tableComment=table.comment,
                primaryKey=pk_columns,
                rowEstimate=100,
                columns=analyzed_columns,
            ))

        summary = AnalysisSummary(
            totalTables=len(analyzed_tables),
            totalColumns=total_columns,
            sensitiveColumns=total_sensitive,
            foreignKeyRelations=total_fk,
            recommendations=recommendations[:10],  # 最多 10 条建议
        )

        result = SchemaAnalysisResult(
            database=request.database,
            dbType=request.dbType,
            tables=analyzed_tables,
            summary=summary,
        )

        logger.info(
            f"Mock Schema 分析完成: database={request.database}, "
            f"tables={summary.totalTables}, columns={summary.totalColumns}, "
            f"sensitive={summary.sensitiveColumns}, fk={summary.foreignKeyRelations}"
        )

        return SchemaAnalyzeResponse(
            success=True,
            result=result,
            mock=True,
        )

    # ==================== 辅助方法 ====================

    def _build_schema_description(self, request: SchemaAnalyzeRequest) -> str:
        """将 Schema 请求转换为 LLM 友好的文本描述"""
        lines = [
            f"数据库: {request.database}",
            f"类型: {request.dbType}",
            f"表数量: {len(request.tables)}",
            "",
        ]

        for table in request.tables:
            header = f"表: {table.tableName}"
            if table.comment:
                header += f" ({table.comment})"
            lines.append(header)

            for col in table.columns:
                flags = []
                if col.primaryKey:
                    flags.append("PK")
                if not col.nullable:
                    flags.append("NOT NULL")
                if col.defaultValue is not None:
                    flags.append(f"DEFAULT {col.defaultValue}")

                flag_str = " ".join(flags)
                line = f"  - {col.name}: {col.type}"
                if flag_str:
                    line += f" [{flag_str}]"
                if col.comment:
                    line += f" // {col.comment}"
                lines.append(line)
            lines.append("")

        return "\n".join(lines)

    def _match_semantic_label(self, col_name: str,
                               col_comment: str | None) -> tuple[str, str]:
        """
        根据字段名匹配语义标签

        返回 (semanticLabel, matchType)
        matchType: "exact" | "prefix" | "contains" | "comment" | "unknown"
        """
        name_lower = col_name.lower().strip()

        # 1. 精确匹配
        if name_lower in SEMANTIC_LABEL_MAP:
            return SEMANTIC_LABEL_MAP[name_lower], "exact"

        # 2. 前缀匹配（is_xxx, has_xxx）
        for prefix in ("is_", "has_"):
            if name_lower.startswith(prefix):
                return "BOOLEAN_FLAG", "prefix"

        # 3. 后缀匹配（xxx_id, xxx_date, xxx_time）
        if name_lower.endswith("_id") and name_lower != "id":
            return "IDENTIFIER", "suffix"
        if name_lower.endswith("_date") or name_lower.endswith("_time"):
            return "DATE_TIME", "suffix"

        # 4. 包含匹配
        for keyword, label in SEMANTIC_LABEL_MAP.items():
            if keyword in name_lower and len(keyword) > 3:
                return label, "contains"

        # 5. 注释推断
        if col_comment:
            comment_lower = col_comment.lower()
            for keyword, label in SEMANTIC_LABEL_MAP.items():
                if keyword in comment_lower:
                    return label, "comment"

        return "UNKNOWN", "unknown"

    def _infer_foreign_key(self, col_name: str, col_type: str,
                           table_names: set[str]) -> InferredForeignKey | None:
        """
        推断外键关系

        规则：
        - `xxx_id` 字段 + 存在 `xxx` 表 → 推断外键
        - `xxx_Id` 字段 + 存在 `xxx` 表 → 推断外键（snake_case）
        """
        name_lower = col_name.lower().strip()

        # 跳过主键 id
        if name_lower in ("id", "uuid", "guid"):
            return None

        # 匹配 xxx_id 模式
        if name_lower.endswith("_id"):
            ref_table = name_lower[:-3]  # 去掉 _id
            if ref_table in table_names:
                return InferredForeignKey(
                    referencedTable=ref_table,
                    referencedColumn="id",
                    confidence=0.85,
                )
            # 尝试单数/复数转换
            if ref_table.endswith("s") and ref_table[:-1] in table_names:
                return InferredForeignKey(
                    referencedTable=ref_table[:-1],
                    referencedColumn="id",
                    confidence=0.70,
                )
            if ref_table + "s" in table_names:
                return InferredForeignKey(
                    referencedTable=ref_table + "s",
                    referencedColumn="id",
                    confidence=0.70,
                )

        return None

    def _suggest_generator(self, col_name: str, col_type: str,
                           label: str, is_primary: bool) -> GeneratorSuggestion | None:
        """
        推荐最合适的测试数据生成器

        优先级：语义标签 > 字段名模式 > 数据类型
        """
        # 主键 → 不推荐生成器
        if is_primary:
            return None

        # 外键字段 → constant.value
        if col_name.lower().endswith("_id") and col_name.lower() != "id":
            return GeneratorSuggestion(
                generator="constant.value",
                reason="外键字段，由关联表主键填充",
                params={"value": 1},
            )

        # 自增类名 → 不推荐
        if col_name.lower() in ("id", "uuid", "guid"):
            return None

        # 按语义标签推荐
        if label in SEMANTIC_TO_GENERATOR:
            gen, reason = SEMANTIC_TO_GENERATOR[label]
            return GeneratorSuggestion(generator=gen, reason=reason, params={})

        # 按类型推荐
        type_lower = col_type.lower().split("(")[0].strip()
        if type_lower in TYPE_TO_GENERATOR:
            gen = TYPE_TO_GENERATOR[type_lower]
            return GeneratorSuggestion(
                generator=gen,
                reason=f"按数据库类型 {col_type} 匹配",
                params={},
            )

        # 默认
        return GeneratorSuggestion(
            generator="faker.word",
            reason="无法确定语义，默认使用文本生成器",
            params={},
        )

    def _parse_llm_result(self, result_dict: dict,
                          request: SchemaAnalyzeRequest) -> SchemaAnalysisResult:
        """将 LLM 返回的 dict 解析为 SchemaAnalysisResult"""
        # 解析表
        analyzed_tables = []
        for t in result_dict.get("tables", []):
            columns = []
            for c in t.get("columns", []):
                # 敏感检测
                sd = c.get("sensitiveDetection", {})
                sensitive_detection = SensitiveDetection(
                    sensitive=sd.get("sensitive", False),
                    sensitiveType=sd.get("sensitiveType", "NONE"),
                    confidence=sd.get("confidence", 0.0),
                )

                # 外键
                fk = c.get("inferredForeignKey")
                inferred_fk = None
                if fk:
                    inferred_fk = InferredForeignKey(
                        referencedTable=fk["referencedTable"],
                        referencedColumn=fk.get("referencedColumn", "id"),
                        confidence=fk.get("confidence", 0.7),
                    )

                # 生成器
                gs = c.get("generatorSuggestion")
                generator_suggestion = None
                if gs:
                    generator_suggestion = GeneratorSuggestion(
                        generator=gs.get("generator", "faker.word"),
                        reason=gs.get("reason", ""),
                        params=gs.get("params", {}),
                    )

                columns.append(AnalyzedColumn(
                    name=c.get("name", ""),
                    type=c.get("type", ""),
                    nullable=c.get("nullable", True),
                    defaultValue=c.get("defaultValue"),
                    comment=c.get("comment"),
                    semanticLabel=c.get("semanticLabel", "UNKNOWN"),
                    sensitiveDetection=sensitive_detection,
                    inferredForeignKey=inferred_fk,
                    generatorSuggestion=generator_suggestion,
                ))

            analyzed_tables.append(AnalyzedTable(
                tableName=t.get("tableName", ""),
                tableComment=t.get("tableComment"),
                primaryKey=t.get("primaryKey", []),
                rowEstimate=t.get("rowEstimate", 100),
                columns=columns,
            ))

        # 摘要
        sm = result_dict.get("summary", {})
        summary = AnalysisSummary(
            totalTables=sm.get("totalTables", len(analyzed_tables)),
            totalColumns=sm.get("totalColumns", 0),
            sensitiveColumns=sm.get("sensitiveColumns", 0),
            foreignKeyRelations=sm.get("foreignKeyRelations", 0),
            recommendations=sm.get("recommendations", []),
        )

        return SchemaAnalysisResult(
            database=result_dict.get("database", request.database),
            dbType=result_dict.get("dbType", request.dbType),
            tables=analyzed_tables,
            summary=summary,
        )


# 单例
schema_agent = SchemaAgent()
