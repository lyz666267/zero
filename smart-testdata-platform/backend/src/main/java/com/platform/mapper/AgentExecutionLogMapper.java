package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.AgentExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 执行日志 Mapper
 */
@Mapper
public interface AgentExecutionLogMapper extends BaseMapper<AgentExecutionLog> {
}
