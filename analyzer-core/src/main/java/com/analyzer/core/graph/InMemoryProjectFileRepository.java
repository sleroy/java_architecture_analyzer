package com.analyzer.core.graph;

import com.analyzer.api.graph.GraphRepository;
import com.analyzer.api.graph.ProjectFileRepository;
import com.analyzer.core.model.ProjectFile;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ProjectFileRepository that delegates to GraphRepository.
 * This ensures a single source of truth for all graph nodes by storing
 * ProjectFile instances in the central GraphRepository rather than maintaining
 * separate storage.
 * 
 * <p>This implementation follows the same pattern as DelegatingClassNodeRepository,
 * providing type-specific query methods while delegating storage to the unified
 * GraphRepository.</p>
 */
public class InMemoryProjectFileRepository implements ProjectFileRepository {

    private final GraphRepository graphRepository;

    @Inject
    public InMemoryProjectFileRepository(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Override
    public Optional<ProjectFile> findById(String id) {
        return graphRepository.getNodeById(id)
                .filter(node -> node instanceof ProjectFile)
                .map(node -> (ProjectFile) node);
    }

    @Override
    public ProjectFile getOrCreate(String id) {
        // ProjectFile requires Path and project root for construction
        // Cannot create from ID alone - this would be a design flaw
        throw new UnsupportedOperationException(
                "Cannot create ProjectFile from ID alone. " +
                        "Use findByPath() or save() with a properly constructed ProjectFile.");
    }

    @Override
    public List<ProjectFile> findAll() {
        return graphRepository.getNodesByClass(ProjectFile.class).stream()
                .toList();
    }

    @Override
    public List<ProjectFile> findByTag(String tag) {
        return graphRepository.getNodesByClass(ProjectFile.class).stream()
                .filter(file -> file.hasTag(tag))
                .toList();
    }

    @Override
    public void save(ProjectFile node) {
        graphRepository.addNode(node);
    }

    @Override
    public List<ProjectFile> findByExtension(String extension) {
        return graphRepository.getNodesByClass(ProjectFile.class).stream()
                .filter(file -> file.hasFileExtension(extension))
                .toList();
    }

    @Override
    public Optional<ProjectFile> findByPath(Path path) {
        return findById(path.toString());
    }
}
