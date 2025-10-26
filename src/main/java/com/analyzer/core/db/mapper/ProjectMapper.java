package com.analyzer.core.db.mapper;

import com.analyzer.core.db.entity.ProjectEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper for project operations.
 */
public interface ProjectMapper {

    /**
     * Insert a new project.
     *
     * @param project The project entity to insert
     */
    void insertProject(ProjectEntity project);

    /**
     * Find a project by its ID.
     *
     * @param id The project ID
     * @return The project entity, or null if not found
     */
    ProjectEntity findById(@Param("id") Long id);

    /**
     * Find a project by its name.
     *
     * @param name The project name
     * @return The project entity, or null if not found
     */
    ProjectEntity findByName(@Param("name") String name);

    /**
     * Find a project by its root path.
     *
     * @param rootPath The root path
     * @return The project entity, or null if not found
     */
    ProjectEntity findByRootPath(@Param("rootPath") String rootPath);

    /**
     * Find all projects.
     *
     * @return List of all projects
     */
    List<ProjectEntity> findAll();

    /**
     * Update an existing project.
     *
     * @param project The project entity with updated data
     */
    void updateProject(ProjectEntity project);

    /**
     * Delete a project by its ID.
     *
     * @param id The project ID
     */
    void deleteProject(@Param("id") Long id);

    /**
     * Delete all projects.
     */
    void deleteAll();

    /**
     * Count total number of projects.
     *
     * @return Total project count
     */
    int countProjects();
}
