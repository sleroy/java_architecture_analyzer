package com.analyzer.core.graph;

import com.analyzer.core.model.ProjectFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository extends NodeRepository<ProjectFile> {
    List<ProjectFile> findByExtension(String extension);

    Optional<ProjectFile> findByPath(Path path);
}
