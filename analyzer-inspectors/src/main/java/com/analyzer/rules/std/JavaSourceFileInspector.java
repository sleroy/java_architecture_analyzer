package com.analyzer.rules.std;

import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.model.ClassType;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.core.cache.LocalCache;
import com.analyzer.dev.inspectors.source.AbstractJavaParserInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import static com.analyzer.core.inspector.InspectorTags.*;

/**
 * Comprehensive inspector for Java source files (.java).
 * <p>
 * This is a foundational inspector that combines basic Java detection with
 * detailed metadata extraction using JavaParser. It performs:
 * 1. Basic Java source file detection and tagging
 * 2. Detailed package name, class name, and metadata extraction via AST parsing
 * 3. TypeDeclaration scanning for all types in the compilation unit
 * <p>
 * Uses JavaParser instead of regex for accurate AST-based analysis.
 * Other Java-specific inspectors can depend on this inspector's output.
 * <p>
 * No dependencies - this is a foundational inspector.
 */
@InspectorDependencies(requires = {}, produces = { TAG_JAVA_DETECTED, TAG_JAVA_IS_SOURCE,
        PROP_JAVA_FULLY_QUALIFIED_NAME })
public class JavaSourceFileInspector extends AbstractJavaParserInspector {

    private static final Logger logger = LoggerFactory.getLogger(JavaSourceFileInspector.class);

    @Inject
    public JavaSourceFileInspector(ResourceResolver resourceResolver, LocalCache localCache) {
        super(resourceResolver, localCache);
    }

    @Override
    public String getName() {
        return "Java Source File Detector";
    }

    @Override
    public boolean supports(ProjectFile projectFile) {
        if (projectFile == null || projectFile.getFileName() == null) {
            return false;
        }
        return projectFile.hasFileExtension("java");
    }

    @Override
    protected void analyzeCompilationUnit(CompilationUnit cu, ProjectFile projectFile,
            NodeDecorator<ProjectFile> decorator) {
        try {
            // PHASE 1: Basic Java detection and fundamental tagging
            decorator.enableTag(TAG_JAVA_IS_SOURCE);
            decorator.enableTag(TAG_JAVA_DETECTED);
            decorator.setProperty(JAVA_FORMAT, FORMAT_SOURCE);
            decorator.setProperty(TAG_LANGUAGE, LANGUAGE_JAVA);

            // PHASE 2: Extract package name from AST
            String packageName = cu.getPackageDeclaration()
                    .map(PackageDeclaration::getNameAsString)
                    .orElse("");

            if (!packageName.isEmpty()) {
                decorator.setProperty(TAG_JAVA_PACKAGE_NAME, packageName);
            }

            // PHASE 3: Scan TypeDeclarations and find the primary type
            TypeDeclaration<?> primaryType = findPrimaryTypeDeclaration(cu, projectFile);

            if (primaryType != null) {
                String className = primaryType.getNameAsString();
                decorator.setProperty(PROP_JAVA_CLASS_NAME, className);

                // Set the fully qualified name on the ProjectFile for the main type
                projectFile.setFullQualifiedName(packageName, className);
                decorator.enableTag(PROP_JAVA_FULLY_QUALIFIED_NAME);

                // Determine the type
                String typeKind = determineTypeKind(primaryType);
                decorator.setProperty(TAG_JAVA_CLASS_TYPE, typeKind);

                logger.debug("Processed Java source file: {} -> class: {}, package: {}, type: {}",
                        projectFile.getFileName(), className, packageName, typeKind);
            } else {
                logger.warn("No primary type declaration found in {}", projectFile.getFileName());
                decorator.setProperty(TAG_JAVA_CLASS_TYPE, ClassType.SOURCE_ONLY.toString());
            }

        } catch (Exception e) {
            logger.warn("Error processing Java source file {}: {}", projectFile.getFilePath(), e.getMessage());
            decorator.error("Processing error: " + e.getMessage());
        }
    }

    /**
     * Find the primary TypeDeclaration in the compilation unit.
     * The primary type is typically the public type that matches the filename,
     * or the first type declaration if no public type is found.
     */
    private TypeDeclaration<?> findPrimaryTypeDeclaration(CompilationUnit cu, ProjectFile projectFile) {
        var types = cu.getTypes();
        if (types.isEmpty()) {
            return null;
        }

        // Try to find the public type that matches the filename
        String fileNameWithoutExtension = projectFile.getFileName().replace(".java", "");
        for (TypeDeclaration<?> type : types) {
            if (type.getNameAsString().equals(fileNameWithoutExtension)) {
                return type;
            }
        }

        // If no matching type found, return the first type (primary type by convention)
        return types.get(0);
    }

    /**
     * Determine the kind of type declaration (class, interface, enum, record,
     * annotation).
     */
    private String determineTypeKind(TypeDeclaration<?> typeDecl) {
        if (typeDecl.isClassOrInterfaceDeclaration()) {
            return typeDecl.asClassOrInterfaceDeclaration().isInterface() ? "interface" : "class";
        } else if (typeDecl.isEnumDeclaration()) {
            return "enum";
        } else if (typeDecl.isAnnotationDeclaration()) {
            return "annotation";
        } else {
            // Try to detect records - check if method exists (JavaParser 3.24+)
            try {
                java.lang.reflect.Method isRecordMethod = typeDecl.getClass().getMethod("isRecordDeclaration");
                Boolean isRecord = (Boolean) isRecordMethod.invoke(typeDecl);
                if (Boolean.TRUE.equals(isRecord)) {
                    return "record";
                }
            } catch (Exception e) {
                // Method doesn't exist or invocation failed - not a record or unsupported
                // JavaParser version
                logger.debug("Record detection not available in this JavaParser version");
            }
            // Fallback - check class name
            if (typeDecl.getClass().getSimpleName().contains("Record")) {
                return "record";
            }
            return "class"; // Default fallback
        }
    }
}
