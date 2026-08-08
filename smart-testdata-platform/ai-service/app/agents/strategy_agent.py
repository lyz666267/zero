"""
策略生成 Agent

职责：
    接收 Schema 语义分析结果 + 用户需求 → 输出可执行的 GenerationPlan

流程：
    1. 解析 SchemaAnalysisResult，提取每个字段的语义标签、生成器推荐、FK 关系
    2. 结合用户需求构建 LLM Prompt
    3. 调用 DeepSeek LLM → Qwen 备用 → Mock 降级
    4. 输出结构化 GenerationPlan

与 TestDataAgent 的区别：
    - TestDataAgent: 直接接收原始 Schema dict + requirement，自行做字段分析
    - StrategyAgent: 接收 SchemaAgent 的分析结果，做更精准的策略规划
      利用已有的 semanticLabel / generatorSuggestion / inferredForeignKey
      而不是重新做字段名匹配
"""
import asyncio
import json
import re
from loguru import logger

from app.schemas.generation_plan import (
    GenerationPlan, TablePlan, FieldPlan, FieldRange,
    GeneratePlanResponse, ForeignKeyInfo, default_enum_values,
)
from app.schemas.schema_analysis import (
    SchemaAnalysisResult, AnalyzedTable, AnalyzedColumn,
)
from app.llm import llm_router, RouterExhaustedError, LLMProviderError
from app.prompts.strategy_prompt import STRATEGY_SYSTEM_PROMPT


# 需求文本中的中文表名 → 实际表名
TABLE_ALIASES = {
    "users": ["users", "user", "用户"],
    "products": ["products", "product", "商品"],
    "orders": ["orders", "order", "订单"],
}


class StrategyAgent:
    """
    策略生成 Agent

    两种模式：
    1. LLM 模式 — 调用 LLM 基于语义分析结果生成最优策略
    2. Mock 模式 — 直接将 Schema 分析中的 generatorSuggestion 转为计划
    """

    def __init__(self):
        self.has_llm = llm_router.has_llm
        logger.info(f"StrategyAgent 初始化: mode={'LLM' if self.has_llm else 'MOCK'}")

    async def generate(
        self,
        analysis: SchemaAnalysisResult,
        requirement: str,
    ) -> GeneratePlanResponse:
        """
        根据 Schema 分析结果生成数据生成策略

        Args:
            analysis: SchemaAgent 的输出（语义标签 + FK + 生成器推荐）
            requirement: 用户需求描述（如"生成1000条用户和订单数据"）

        Returns:
            GeneratePlanResponse 包含 GenerationPlan
        """
        tables = analysis.tables

        if not tables:
            return GeneratePlanResponse(
                success=False,
                error="Schema 分析结果中没有表信息",
                mock=False,
            )

        if self.has_llm:
            try:
                return await self._generate_with_llm(analysis, requirement)
            except (LLMProviderError, RouterExhaustedError, json.JSONDecodeError, asyncio.TimeoutError) as e:
                logger.warning(f"LLM 策略生成失败，降级为 Mock 模式: {e}")
                return self._generate_mock(analysis, requirement)

        return self._generate_mock(analysis, requirement)

    async def _generate_with_llm(
        self,
        analysis: SchemaAnalysisResult,
        requirement: str,
    ) -> GeneratePlanResponse:
        """调用 LLM 生成最优策略"""
        # 将分析结果构建为 LLM 友好的描述
        analysis_desc = self._build_analysis_description(analysis)

        user_prompt = f"""## Schema 语义分析结果

{analysis_desc}

## 用户需求

{requirement}

请生成最优的测试数据生成计划。"""

        try:
            messages = [
                {"role": "system", "content": STRATEGY_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ]

            response_text = await llm_router.chat(
                messages, temperature=0.1, max_tokens=8192,
            )

            json_text = llm_router.extract_json(response_text)
            plan_dict = json.loads(json_text)

            plan = self._parse_plan(plan_dict)
            plan = self._ensure_primary_keys(plan, analysis)
            plan = self._apply_requirement_counts(plan, requirement, analysis)
            logger.info(
                f"LLM 策略生成成功: taskName={plan.taskName}, tables={len(plan.tables)}"
            )

            return GeneratePlanResponse(
                success=True,
                plan=plan,
                mock=False,
            )

        except RouterExhaustedError as e:
            logger.warning(f"所有 LLM 模型均已失败，降级为 Mock: {e}")
            return self._generate_mock(analysis, requirement)

        except json.JSONDecodeError as e:
            logger.error(f"LLM 策略 JSON 解析失败: {e}")
            return self._generate_mock(analysis, requirement)

        except LLMProviderError as e:
            logger.error(f"LLM 策略生成调用失败: {e}")
            return self._generate_mock(analysis, requirement)

        except asyncio.TimeoutError:
            logger.error("LLM 策略生成超时，降级为 Mock 模式")
            return self._generate_mock(analysis, requirement)

    def _generate_mock(
        self,
        analysis: SchemaAnalysisResult,
        requirement: str,
    ) -> GeneratePlanResponse:
        """
        Mock 模式 — 直接将 SchemaAgent 的生成器推荐转为 GenerationPlan

        利用已分析的 semanticLabel / generatorSuggestion / inferredForeignKey，
        不需要重新做字段名匹配（比 TestDataAgent 的 mock 更精准）。
        """
        extracted_count = self._extract_count(requirement)
        table_counts = self._extract_table_counts(requirement, analysis)
        task_name = self._extract_task_name(requirement, analysis)

        # 按外键依赖排序：被引用表在前
        ordered_tables = self._order_by_dependency(analysis.tables)

        table_plans = []
        for table in ordered_tables:
            field_plans = []
            pk_plans = []

            for col in table.columns:
                # 主键：保留并标记 primaryKey=true，供外键生成器引用
                if col.name in table.primaryKey:
                    pk_plans.append(self._primary_key_field(col))
                    continue

                # 外键字段 → constant.value 或 fk.reference
                if col.inferredForeignKey:
                    fk = col.inferredForeignKey
                    field_plans.append(FieldPlan(
                        name=col.name,
                        generator="constant.value",
                        params={
                            "value": 1,
                            "refTable": fk.referencedTable,
                            "refColumn": fk.referencedColumn,
                        },
                        foreignKey=ForeignKeyInfo(
                            table=fk.referencedTable,
                            column=fk.referencedColumn,
                        ),
                    ))
                    continue

                # 使用已推荐的生成器
                if col.generatorSuggestion:
                    gs = col.generatorSuggestion
                    field_plan = FieldPlan(
                        name=col.name,
                        generator=gs.generator,
                    )

                    # 数值类型 → 添加 range
                    if "integer" in gs.generator:
                        field_plan.range = FieldRange(min=0, max=10000)
                    elif "decimal" in gs.generator:
                        field_plan.range = FieldRange(min=0, max=99999)

                    # 枚举类型 → 添加默认 values
                    if "enum" in gs.generator:
                        if col.name.lower() in ("status", "state", "type"):
                            field_plan.params = {"values": ["active", "inactive", "pending"]}
                        elif col.name.lower() in ("gender", "sex"):
                            field_plan.params = {"values": ["male", "female"]}
                        elif col.name.lower() == "role":
                            field_plan.params = {"values": ["user", "admin", "manager"]}

                    self._ensure_required_params(field_plan)
                    field_plans.append(field_plan)
                    continue

                # 无推荐生成器 → 按语义标签默认
                fallback_gen = self._fallback_generator(col)
                field_plan = FieldPlan(name=col.name, generator=fallback_gen)

                if "integer" in fallback_gen or "decimal" in fallback_gen:
                    field_plan.range = FieldRange(min=0, max=10000)

                self._ensure_required_params(field_plan)
                field_plans.append(field_plan)

            # 行数：按表指定 > 全局指定 > 表级 rowEstimate > 默认 100
            if table_counts:
                row_count = table_counts.get(
                    table.tableName.lower(),
                    max(table.rowEstimate, 100),
                )
            elif extracted_count is not None:
                row_count = extracted_count
            else:
                row_count = max(table.rowEstimate, 100)

            table_plans.append(TablePlan(
                table=table.tableName,
                count=row_count,
                fields=pk_plans + field_plans,
            ))

        plan = GenerationPlan(
            taskName=task_name,
            tables=table_plans,
        )

        logger.info(
            f"Mock 策略生成: taskName={task_name}, tables={len(table_plans)}, "
            f"totalFields={sum(len(t.fields) for t in table_plans)}"
        )

        return GeneratePlanResponse(
            success=True,
            plan=plan,
            mock=True,
        )

    # ==================== 辅助方法 ====================

    def _build_analysis_description(self, analysis: SchemaAnalysisResult) -> str:
        """将 SchemaAnalysisResult 转换为 LLM 友好的文本"""
        lines = [
            f"数据库: {analysis.database}",
            f"类型: {analysis.dbType}",
            f"表数量: {analysis.summary.totalTables}",
            f"字段总数: {analysis.summary.totalColumns}",
            f"敏感字段数: {analysis.summary.sensitiveColumns}",
            f"外键关系数: {analysis.summary.foreignKeyRelations}",
            "",
        ]

        if analysis.summary.recommendations:
            lines.append("## 分析建议")
            for rec in analysis.summary.recommendations:
                lines.append(f"  - {rec}")
            lines.append("")

        for table in analysis.tables:
            lines.append(f"### 表: {table.tableName}")
            if table.tableComment:
                lines.append(f"  注释: {table.tableComment}")
            if table.primaryKey:
                lines.append(f"  主键: {', '.join(table.primaryKey)}")
            lines.append(f"  建议行数: {table.rowEstimate}")
            lines.append("  字段:")

            for col in table.columns:
                parts = [f"    {col.name} ({col.type})"]

                # 语义标签
                if col.semanticLabel != "UNKNOWN":
                    parts.append(f"语义={col.semanticLabel}")

                # 主键标记
                if col.name in table.primaryKey:
                    parts.append("[PK]")

                # 敏感检测
                if col.sensitiveDetection.sensitive:
                    parts.append(
                        f"[敏感: {col.sensitiveDetection.sensitiveType} "
                        f"({col.sensitiveDetection.confidence:.0%})]"
                    )

                # 外键
                if col.inferredForeignKey:
                    fk = col.inferredForeignKey
                    parts.append(
                        f"→ FK({fk.referencedTable}.{fk.referencedColumn})"
                    )

                # 生成器推荐
                if col.generatorSuggestion:
                    gs = col.generatorSuggestion
                    parts.append(f"→ 推荐: {gs.generator}")
                    if gs.reason:
                        parts.append(f"({gs.reason})")

                lines.append(" | ".join(parts))

            lines.append("")

        return "\n".join(lines)

    def _order_by_dependency(self, tables: list[AnalyzedTable]) -> list[AnalyzedTable]:
        """
        按外键依赖关系排序：被引用表在前

        使用简单拓扑排序：如果表 A 有字段引用表 B，则 B 在 A 前面
        """
        # 构建表名 → 表对象的映射
        table_map = {t.tableName.lower(): t for t in tables}

        # 构建依赖图：{表名: [依赖的表名列表]}
        dependencies: dict[str, list[str]] = {}
        for t in tables:
            deps = []
            for col in t.columns:
                if col.inferredForeignKey:
                    ref = col.inferredForeignKey.referencedTable.lower()
                    if ref in table_map and ref != t.tableName.lower():
                        deps.append(ref)
            dependencies[t.tableName.lower()] = deps

        # Kahn 拓扑排序
        in_degree = {t.tableName.lower(): 0 for t in tables}
        for t in tables:
            for dep in dependencies.get(t.tableName.lower(), []):
                if dep in in_degree:
                    in_degree[t.tableName.lower()] += 1

        queue = [name for name, deg in in_degree.items() if deg == 0]
        ordered_names = []

        while queue:
            current = queue.pop(0)
            ordered_names.append(current)
            # 减少依赖当前表的所有表的入度
            for name, deps in dependencies.items():
                if current in deps and name not in ordered_names and name not in queue:
                    in_degree[name] -= 1
                    if in_degree[name] == 0:
                        queue.append(name)

        # 未排序的表追加到末尾
        for t in tables:
            if t.tableName.lower() not in ordered_names:
                ordered_names.append(t.tableName.lower())

        # 映射回 AnalyzedTable
        result = []
        for name in ordered_names:
            t = table_map.get(name)
            if t:
                result.append(t)

        return result

    def _fallback_generator(self, col: AnalyzedColumn) -> str:
        """无推荐生成器时的兜底策略：按语义标签 + 类型"""
        label_to_gen = {
            "PERSON_NAME": "faker.name",
            "EMAIL": "faker.email",
            "PHONE": "faker.phone_number",
            "ID_CARD": "faker.ssn",
            "ADDRESS": "faker.address",
            "BANK_CARD": "faker.ssn",
            "AMOUNT": "random.decimal",
            "DATE_TIME": "time.past_datetime",
            "BOOLEAN_FLAG": "random.boolean",
            "ENUM_VALUE": "enum.values",
            "IDENTIFIER": "uuid",
            "TEXT_CONTENT": "faker.text",
            "URL_PATH": "faker.url",
        }

        label = col.semanticLabel
        if label in label_to_gen:
            return label_to_gen[label]

        # 按类型
        type_lower = col.type.lower().split("(")[0].strip()
        type_map = {
            "varchar": "faker.word", "char": "faker.word",
            "text": "faker.text", "longtext": "faker.text",
            "int": "random.integer", "bigint": "random.integer",
            "smallint": "random.integer", "tinyint": "random.integer",
            "decimal": "random.decimal", "float": "random.decimal",
            "double": "random.decimal",
            "datetime": "time.past_datetime", "timestamp": "time.past_datetime",
            "date": "time.past_date",
            "boolean": "random.boolean", "bool": "random.boolean",
        }
        return type_map.get(type_lower, "faker.word")

    def _extract_count(self, requirement: str) -> int | None:
        """从需求文本中解析目标行数，未找到返回 None"""
        match = re.search(r"(\d+)\s*(?:条|行|笔|个)", requirement)
        if match:
            return int(match.group(1))
        match = re.search(r"(\d+)\s*(?:个|条|行)?", requirement)
        if match:
            return int(match.group(1))
        return None

    @staticmethod
    def _extract_table_counts(
        requirement: str,
        analysis: SchemaAnalysisResult,
    ) -> dict[str, int]:
        """从需求中解析每张表对应的行数，例如 50条用户数据 / 20条商品数据"""
        counts: dict[str, int] = {}
        if not requirement:
            return counts

        req_lower = requirement.lower()
        for table in analysis.tables:
            canonical = table.tableName.lower()
            aliases = TABLE_ALIASES.get(
                canonical,
                [canonical, canonical.rstrip("s")],
            )
            for alias in aliases:
                pattern = re.compile(
                    r"(\d+)\s*(?:条|行|笔|个)\s*" + re.escape(alias) + r"(?:数据|记录|信息)?"
                )
                match = pattern.search(req_lower)
                if match:
                    counts[canonical] = int(match.group(1))
                    break
        return counts

    @staticmethod
    def _apply_requirement_counts(
        plan: GenerationPlan,
        requirement: str,
        analysis: SchemaAnalysisResult,
    ) -> GenerationPlan:
        """LLM 模式：用需求中的按表行数覆盖 LLM 返回的行数"""
        counts = StrategyAgent._extract_table_counts(requirement, analysis)
        if not counts:
            return plan

        for table_plan in plan.tables:
            canonical = (table_plan.table or "").lower()
            if canonical in counts:
                table_plan.count = counts[canonical]
        return plan

    def _extract_task_name(
        self, requirement: str, analysis: SchemaAnalysisResult
    ) -> str:
        """从需求中提取任务名称"""
        table_names = [t.tableName for t in analysis.tables]

        for name in table_names:
            if name.lower() in requirement.lower():
                return f"{name}表测试数据生成"

        if len(requirement) > 40:
            return requirement[:40] + "..."
        return requirement if requirement else "测试数据生成任务"

    def _parse_plan(self, plan_dict: dict) -> GenerationPlan:
        """将 LLM 返回的 dict 解析为 GenerationPlan"""
        tables = []
        raw_tables = plan_dict.get("tables", [])

        for t in raw_tables:
            fields = []
            for f in t.get("fields", []):
                field_plan = FieldPlan(
                    name=f["name"],
                    generator=f["generator"],
                )
                fk = f.get("foreignKey")
                if fk:
                    field_plan.foreignKey = ForeignKeyInfo(
                        table=fk.get("table", ""),
                        column=fk.get("column", "id"),
                    )
                if "range" in f and f["range"]:
                    field_plan.range = FieldRange(**f["range"])
                if "params" in f and f["params"]:
                    field_plan.params = f["params"]
                self._ensure_required_params(field_plan)
                fields.append(field_plan)

            tables.append(TablePlan(
                table=t["table"],
                count=t.get("count", 0),
                fields=fields,
            ))

        return GenerationPlan(
            taskName=plan_dict.get("taskName", "数据生成任务"),
            tables=tables,
        )

    @staticmethod
    def _ensure_required_params(field_plan: FieldPlan) -> None:
        """补齐 AI/LLM 可能遗漏的生成器必填参数"""
        generator = field_plan.generator or ""
        if "enum" in generator:
            if not field_plan.params or "values" not in field_plan.params:
                field_plan.params = {
                    **(field_plan.params or {}),
                    "values": default_enum_values(field_plan.name),
                }
        elif generator == "constant.value":
            if field_plan.params and "refTable" in field_plan.params:
                field_plan.generator = "fk.reference"
                field_plan.foreignKey = ForeignKeyInfo(
                    table=str(field_plan.params["refTable"]),
                    column=str(field_plan.params.get("refColumn", "id")),
                )
            elif not field_plan.params or "value" not in field_plan.params:
                field_plan.params = {
                    **(field_plan.params or {}),
                    "value": 1,
                }

    @staticmethod
    def _primary_key_field(col: AnalyzedColumn) -> FieldPlan:
        """构造主键字段计划，使后端多表生成上下文能记录主键值"""
        type_lower = col.type.lower()
        if any(t in type_lower for t in ("int", "bigint", "smallint", "tinyint", "decimal")):
            return FieldPlan(
                name=col.name,
                generator="random.integer",
                range=FieldRange(min=1, max=999999),
                params={"primaryKey": True},
            )
        return FieldPlan(
            name=col.name,
            generator="uuid",
            params={"primaryKey": True},
        )

    @staticmethod
    def _ensure_primary_keys(
        plan: GenerationPlan,
        analysis: SchemaAnalysisResult,
    ) -> GenerationPlan:
        """LLM 计划漏掉主键时自动补齐，保证外键引用可用"""
        table_map = {t.tableName.lower(): t for t in analysis.tables}
        for table_plan in plan.tables:
            analyzed = table_map.get((table_plan.table or "").lower())
            if not analyzed or not analyzed.primaryKey:
                continue

            existing = {f.name for f in table_plan.fields}
            pk_plans = []
            for pk_name in analyzed.primaryKey:
                if pk_name in existing:
                    continue
                col = next((c for c in analyzed.columns if c.name == pk_name), None)
                if col:
                    pk_plans.append(StrategyAgent._primary_key_field(col))

            if pk_plans:
                table_plan.fields = pk_plans + table_plan.fields
        return plan


# 单例
strategy_agent = StrategyAgent()
