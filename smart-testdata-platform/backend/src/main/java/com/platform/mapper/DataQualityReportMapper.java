package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.DataQualityReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据质量评分报告 Mapper
 */
@Mapper
public interface DataQualityReportMapper extends BaseMapper<DataQualityReport> {
}
