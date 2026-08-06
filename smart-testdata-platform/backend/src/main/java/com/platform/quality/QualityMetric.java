package com.platform.quality;

import java.util.List;
import java.util.Map;

public interface QualityMetric {

    QualityMetricResult evaluate(List<Map<String, Object>> data, SchemaInfo schema);
}
