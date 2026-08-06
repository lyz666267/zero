package com.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.dto.ApiResponse;
import com.platform.dto.DashboardStats;
import com.platform.dto.ProjectRequest;
import com.platform.entity.Project;
import com.platform.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 获取当前登录用户 ID
     */
    private Long getCurrentUserId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    /**
     * 仪表盘统计
     */
    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStats> dashboardStats(Authentication auth) {
        return ApiResponse.success(projectService.getDashboardStats(getCurrentUserId(auth)));
    }

    /**
     * 项目列表（分页）
     */
    @GetMapping
    public ApiResponse<Page<Project>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        return ApiResponse.success(projectService.listByUser(getCurrentUserId(auth), page, size));
    }

    /**
     * 项目详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Project> getById(@PathVariable Long id, Authentication auth) {
        return ApiResponse.success(projectService.getById(id, getCurrentUserId(auth)));
    }

    /**
     * 创建项目
     */
    @PostMapping
    public ApiResponse<Project> create(@Valid @RequestBody ProjectRequest request, Authentication auth) {
        Long userId = getCurrentUserId(auth);
        Project project = projectService.create(userId, request);
        log.info("Project created: userId={}, projectId={}", userId, project.getId());
        return ApiResponse.success(project);
    }

    /**
     * 更新项目
     */
    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id,
                                       @Valid @RequestBody ProjectRequest request,
                                       Authentication auth) {
        Long userId = getCurrentUserId(auth);
        Project project = projectService.update(id, userId, request);
        log.info("Project updated: userId={}, projectId={}", userId, id);
        return ApiResponse.success(project);
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getCurrentUserId(auth);
        projectService.delete(id, userId);
        log.info("Project deleted: userId={}, projectId={}", userId, id);
        return ApiResponse.success();
    }
}
