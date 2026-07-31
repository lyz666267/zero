package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.TestDataTaskPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试数据生成计划 Mapper
 */
@Mapper
public interface TestDataTaskPlanMapper extends BaseMapper<TestDataTaskPlan> {
}
