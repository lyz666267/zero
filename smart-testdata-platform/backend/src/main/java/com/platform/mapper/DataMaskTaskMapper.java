package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.DataMaskTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库脱敏任务 Mapper
 */
@Mapper
public interface DataMaskTaskMapper extends BaseMapper<DataMaskTask> {
}
