package com.platform.quality;

import com.platform.dto.QualityReportResponse.QualityIssue;

import java.util.List;

public record QualityMetricResult(
        String key,
        double score,
        List<QualityIssue> issues
) {
}
