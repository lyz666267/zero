package com.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 生成计划请求 — 转发给 AI 服务
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePlanRequest {

    /** 数据库 Schema JSON（直接透传前端传来的 schema 对象） */
    private Map<String, Object> schema;

    /** 用户需求，如 "生成1000条用户数据" */
    private String requirement;
}
