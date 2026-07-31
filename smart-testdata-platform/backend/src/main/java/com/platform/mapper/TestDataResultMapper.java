package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.TestDataResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试数据生成结果 Mapper
 */
@Mapper
public interface TestDataResultMapper extends BaseMapper<TestDataResult> {
}
