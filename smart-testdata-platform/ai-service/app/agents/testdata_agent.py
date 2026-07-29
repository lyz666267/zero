"""
测试数据生成规划 Agent

职责：
    接收数据库 Schema + 用户需求 → 输出结构化生成计划

流程：
    1. 解析 Schema，提取表名、字段名、类型
    2. 结合用户需求构建 LLM Prompt
    3. 调用 DeepSeek LLM 生成 JSON 计划
    4. 若无 API Key，降级为规则引擎 Mock
"""
import json
import re
from loguru import logger

from app.schemas.generation_plan import (
    GenerationPlan, TablePlan, FieldPlan, FieldRange,
    GeneratePlanRequest, GeneratePlanResponse,
)
from app.services.llm_service import llm_service


# ============================================================
# 字段名 → 生成器 映射表（规则引擎用）
# ============================================================

FIELD_GENERATOR_MAP = {
    # 姓名
    "name": "faker.name",
    "username": "faker.user_name",
    "nickname": "faker.user_name",
    "full_name": "faker.name",
    "first_name": "faker.first_name",
    "last_name": "faker.last_name",

    # 联系方式
    "email": "faker.email",
    "phone": "faker.phone_number",
    "mobile": "faker.phone_number",
    "tel": "faker.phone_number",
    "telephone": "faker.phone_number",
    "address": "faker.address",
    "city": "faker.city",
    "country": "faker.country",
    "zip": "faker.postcode",
    "zip_code": "faker.postcode",
    "postcode": "faker.postcode",

    # 公司/组织
    "company": "faker.company",
    "department": "faker.bs",
    "title": "faker.job",
    "job": "faker.job",
    "position": "faker.job",

    # 文本
    "description": "faker.text",
    "content": "faker.text",
    "remark": "faker.sentence",
    "note": "faker.sentence",
    "comment": "faker.sentence",
    "bio": "faker.text",
    "summary": "faker.paragraph",

    # 网络
    "url": "faker.url",
    "website": "faker.url",
    "ip": "faker.ipv4",
    "ip_address": "faker.ipv4",
    "domain": "faker.domain_name",

    # 时间
    "created_at": "time.past_datetime",
    "updated_at": "time.past_datetime",
    "create_time": "time.past_datetime",
    "update_time": "time.past_datetime",
    "birthday": "time.past_date",
    "birth_date": "time.past_date",
    "date": "time.date_this_year",
    "datetime": "time.past_datetime",
    "timestamp": "time.past_datetime",

    # 身份
    "id_card": "faker.ssn",
    "id_number": "faker.ssn",
    "passport": "faker.passport_number",
    "ssn": "faker.ssn",

    # 金融
    "price": "random.decimal",
    "amount": "random.decimal",
    "money": "random.decimal",
    "salary": "random.integer",
    "balance": "random.decimal",
    "total": "random.decimal",
    "fee": "random.decimal",
    "discount": "random.decimal",

    # 状态/枚举
    "status": "enum.values",
    "type": "enum.values",
    "gender": "enum.values",
    "sex": "enum.values",
    "state": "enum.values",
    "level": "enum.values",
    "role": "enum.values",
    "category": "enum.values",
    "flag": "random.boolean",
    "is_": "random.boolean",
    "has_": "random.boolean",
    "enable": "random.boolean",
    "active": "random.boolean",
    "deleted": "constant.value",
}

# 按类型映射（MySQL 类型 → 默认生成器）
TYPE_GENERATOR_MAP = {
    "varchar": "faker.word",
    "char": "faker.word",
    "text": "faker.text",
    "longtext": "faker.text",
    "mediumtext": "faker.text",
    "tinytext": "faker.word",
    "int": "random.integer",
    "bigint": "random.integer",
    "smallint": "random.integer",
    "tinyint": "random.integer",
    "mediumint": "random.integer",
    "decimal": "random.decimal",
    "float": "random.decimal",
    "double": "random.decimal",
    "datetime": "time.past_datetime",
    "timestamp": "time.past_datetime",
    "date": "time.past_date",
    "time": "time.time",
    "year": "random.integer",
    "boolean": "random.boolean",
    "bool": "random.boolean",
    "json": "faker.json",
    "blob": "constant.null",
    "enum": "enum.values",
}


# ============================================================
# LLM System Prompt
# ============================================================

SYSTEM_PROMPT = """你是一个专业的测试数据生成规划专家。你的任务是根据数据库表结构和用户需求，生成测试数据生成计划。

## 生成规则

1. 分析每个字段的名称和类型，选择最合适的生成器
2. 常用生成器分类：

**Faker 类（模拟真实数据）：**
- faker.name, faker.first_name, faker.last_name — 姓名
- faker.email — 邮箱
- faker.phone_number — 手机号
- faker.address, faker.city, faker.country — 地址
- faker.company, faker.job — 公司/职位
- faker.text, faker.sentence, faker.paragraph — 文本
- faker.url, faker.ipv4, faker.domain_name — 网络
- faker.user_name, faker.uuid4 — 标识符
- faker.ssn, faker.passport_number — 证件号

**Random 类（随机数值）：**
- random.integer — 整数（需指定 range: {min, max}）
- random.decimal — 小数
- random.boolean — 布尔值

**Time 类（时间）：**
- time.past_datetime, time.past_date, time.date_this_year

**Enum 类（枚举）：**
- enum.values — 枚举值（需指定 params: {values: [...]}）

**Constant 类（常量）：**
- constant.value — 固定值（需指定 params: {value: ...}）

3. 对于 INT/BIGINT 类型字段，优先判断是否为外键/关联字段；如果是，使用 constant.value
4. 对于 VARCHAR 类型，根据字段名语义选择最合适的 faker 生成器
5. 主键 id 字段不需要生成（数据库自增）

## 输出格式

严格返回 JSON，不要包含 markdown 代码块以外的内容：

{
  "taskName": "任务名称",
  "tables": [
    {
      "table": "表名",
      "count": 行数,
      "fields": [
        {"name": "字段名", "generator": "生成器名", "range": {"min": 0, "max": 100}, "params": {}}
      ]
    }
  ]
}

如果用户需求中指定了多张表，就生成多个 table 条目。如果只提到一张表（如"生成1000条用户数据"），就只生成一个。

注意：range 和 params 字段仅在有需要时添加，不要为每个字段都添加空对象。"""


# ============================================================
# Agent 核心逻辑
# ============================================================

class TestDataAgent:
    """
    测试数据生成规划 Agent

    两种模式：
    1. LLM 模式（有 API Key）— 调用 DeepSeek 生成计划
    2. Mock 模式（无 API Key）— 基于规则引擎生成计划
    """

    def __init__(self):
        self.has_llm = llm_service.has_key
        logger.info(f"TestDataAgent 初始化: mode={'LLM' if self.has_llm else 'MOCK'}")

    async def generate_plan(self, request: GeneratePlanRequest) -> GeneratePlanResponse:
        """
        生成测试数据计划

        Args:
            request: 包含 schema 和 requirement

        Returns:
            GeneratePlanResponse 包含生成的计划或错误信息
        """
        schema = request.schema
        requirement = request.requirement

        # 解析 Schema 中的表信息
        tables = schema.get("tables", [])

        if not tables:
            return GeneratePlanResponse(
                success=False,
                error="Schema 中没有表信息，请先分析数据库结构",
                mock=False,
            )

        if self.has_llm:
            # 使用 LLM 生成计划
            return await self._generate_with_llm(tables, requirement)
        else:
            # 使用规则引擎生成 Mock 计划
            return self._generate_mock(tables, requirement)

    async def _generate_with_llm(self, tables: list[dict],
                                  requirement: str) -> GeneratePlanResponse:
        """调用 DeepSeek LLM 生成计划"""
        # 构建简化的 Schema 描述（减少 token 消耗）
        schema_desc = self._build_schema_description(tables)

        user_prompt = f"""## 数据库表结构

{schema_desc}

## 用户需求

{requirement}

请生成测试数据生成计划。"""

        try:
            messages = [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ]

            response_text = await llm_service.chat(messages)

            # 提取 JSON
            json_text = llm_service.extract_json(response_text)
            plan_dict = json.loads(json_text)

            # 解析为 Pydantic 模型
            plan = self._parse_plan(plan_dict)
            logger.info(f"LLM 生成计划成功: taskName={plan.taskName}, tables={len(plan.tables)}")

            return GeneratePlanResponse(
                success=True,
                plan=plan,
                mock=False,
            )

        except json.JSONDecodeError as e:
            logger.error(f"LLM 返回的 JSON 解析失败: {e}")
            logger.debug(f"原始响应: {response_text}")
            # JSON 解析失败，降级到 mock
            logger.warning("降级为 Mock 模式")
            return self._generate_mock(tables, requirement)

        except Exception as e:
            logger.error(f"LLM 调用失败: {e}")
            # LLM 调用失败，降级到 mock
            return GeneratePlanResponse(
                success=False,
                error=f"LLM 调用失败: {str(e)}，请检查 API Key 配置或使用 Mock 模式",
                mock=False,
            )

    def _generate_mock(self, tables: list[dict],
                        requirement: str) -> GeneratePlanResponse:
        """
        规则引擎 Mock 模式

        根据字段名/类型映射表，自动匹配生成器
        """
        # 尝试从需求中解析行数
        count = self._extract_count(requirement)
        task_name = self._extract_task_name(requirement, tables)

        table_plans = []
        for table in tables:
            table_name = table.get("tableName", "unknown")
            columns = table.get("columns", [])

            field_plans = []
            for col in columns:
                col_name = col.get("name", "")
                col_type = col.get("type", "").lower()
                is_primary = col.get("primary", False)
                is_foreign = bool(col.get("foreignRefTable"))

                # 跳过自增主键
                if is_primary and col_type in ("int", "bigint", "smallint", "tinyint"):
                    continue

                # 外键字段：用占位值
                if is_foreign:
                    field_plans.append(FieldPlan(
                        name=col_name,
                        generator="constant.value",
                        params={"value": 1},
                    ))
                    continue

                # 按字段名匹配生成器
                generator = self._match_generator(col_name, col_type)
                field_plan = FieldPlan(name=col_name, generator=generator)

                # 数值类型添加 range
                if "integer" in generator or "decimal" in generator:
                    field_plan.range = FieldRange(min=0, max=10000)

                # 枚举类型添加默认值
                if "enum" in generator and col_name in ("status", "state", "type"):
                    field_plan.params = {"values": ["active", "inactive", "pending"]}
                elif "enum" in generator and col_name in ("gender", "sex"):
                    field_plan.params = {"values": ["male", "female"]}

                field_plans.append(field_plan)

            table_plans.append(TablePlan(
                table=table_name,
                count=count,
                fields=field_plans,
            ))

        plan = GenerationPlan(
            taskName=task_name,
            tables=table_plans,
        )

        logger.info(f"Mock 生成计划: taskName={task_name}, tables={len(table_plans)}")

        return GeneratePlanResponse(
            success=True,
            plan=plan,
            mock=True,
        )

    # ==================== 辅助方法 ====================

    def _build_schema_description(self, tables: list[dict]) -> str:
        """将 Schema JSON 转换为 LLM 友好的文本描述"""
        lines = []
        for t in tables:
            table_name = t.get("tableName", "unknown")
            comment = t.get("comment", "")
            header = f"表: {table_name}"
            if comment:
                header += f" ({comment})"
            lines.append(header)

            for col in t.get("columns", []):
                col_name = col.get("name", "")
                col_type = col.get("type", "")
                nullable = "NULL" if col.get("nullable") else "NOT NULL"
                primary = "PK" if col.get("primary") else ""
                fk = ""
                if col.get("foreignRefTable"):
                    fk = f"→ FK({col['foreignRefTable']}.{col.get('foreignRefColumn', '')})"
                col_comment = col.get("comment", "")
                extras = " ".join(filter(None, [nullable, primary, fk]))
                line = f"  - {col_name}: {col_type}"
                if extras:
                    line += f" [{extras}]"
                if col_comment:
                    line += f" // {col_comment}"
                lines.append(line)
            lines.append("")

        return "\n".join(lines)

    def _match_generator(self, col_name: str, col_type: str) -> str:
        """根据字段名和类型匹配最合适的生成器"""
        name_lower = col_name.lower().strip()

        # 1. 精确匹配字段名
        if name_lower in FIELD_GENERATOR_MAP:
            return FIELD_GENERATOR_MAP[name_lower]

        # 2. 前缀匹配（如 is_deleted → random.boolean）
        for prefix, gen in FIELD_GENERATOR_MAP.items():
            if name_lower.startswith(prefix) and prefix.endswith("_"):
                return gen

        # 3. 包含匹配（如 xxx_email → faker.email）
        for keyword, gen in FIELD_GENERATOR_MAP.items():
            if keyword in name_lower and not keyword.endswith("_"):
                return gen

        # 4. 按数据库类型匹配
        if col_type in TYPE_GENERATOR_MAP:
            return TYPE_GENERATOR_MAP[col_type]

        # 5. 默认
        return "faker.word"

    def _extract_count(self, requirement: str) -> int:
        """从需求文本中解析目标行数"""
        # 匹配 "1000条"、"1000行"、"1000条数据" 等
        match = re.search(r"(\d+)\s*(?:条|行|笔)", requirement)
        if match:
            return int(match.group(1))
        # 匹配 "生成1000个"、"generate 1000"
        match = re.search(r"(\d{2,})\s*(?:个|条|行)?", requirement)
        if match:
            return int(match.group(1))
        return 100  # 默认 100 条

    def _extract_task_name(self, requirement: str,
                            tables: list[dict]) -> str:
        """从需求中提取任务名称"""
        # 尝试从需求中提取有意义的名称
        for t in tables:
            table_name = t.get("tableName", "")
            if table_name.lower() in requirement.lower():
                return f"{table_name}表测试数据生成"

        # 返回需求原文（截断）
        if len(requirement) > 40:
            return requirement[:40] + "..."
        return requirement

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
                if "range" in f and f["range"]:
                    field_plan.range = FieldRange(**f["range"])
                if "params" in f and f["params"]:
                    field_plan.params = f["params"]
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


# 单例
testdata_agent = TestDataAgent()
