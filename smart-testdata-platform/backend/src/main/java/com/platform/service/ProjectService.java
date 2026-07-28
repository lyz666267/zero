package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.dto.DashboardStats;
import com.platform.dto.ProjectRequest;
import com.platform.entity.Project;
import com.platform.exception.BusinessException;
import com.platform.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    /**
     * 创建项目
     */
    public Project create(Long userId, ProjectRequest request) {
        Project project = new Project();
        project.setUserId(userId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        projectMapper.insert(project);
        return project;
    }

    /**
     * 查询用户的项目列表（分页）
     */
    public Page<Project> listByUser(Long userId, int page, int size) {
        LambdaQueryWrapper<Project> query = new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .orderByDesc(Project::getCreatedAt);
        return projectMapper.selectPage(new Page<>(page, size), query);
    }

    /**
     * 根据 ID 获取项目
     */
    public Project getById(Long id, Long userId) {
        Project project = projectMapper.selectById(id);
        if (project == null || !project.getUserId().equals(userId)) {
            throw new BusinessException(404, "项目不存在");
        }
        return project;
    }

    /**
     * 更新项目
     */
    public Project update(Long id, Long userId, ProjectRequest request) {
        Project project = getById(id, userId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        projectMapper.updateById(project);
        return project;
    }

    /**
     * 删除项目
     */
    public void delete(Long id, Long userId) {
        Project project = getById(id, userId);
        projectMapper.deleteById(project.getId());
    }

    /**
     * 仪表盘统计数据
     */
    public DashboardStats getDashboardStats(Long userId) {
        long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId));
        // 任务统计在后续阶段实现，先返回项目数
        DashboardStats stats = new DashboardStats();
        stats.setProjectCount(projectCount);
        stats.setTaskCount(0);
        stats.setSuccessTaskCount(0);
        return stats;
    }
}
