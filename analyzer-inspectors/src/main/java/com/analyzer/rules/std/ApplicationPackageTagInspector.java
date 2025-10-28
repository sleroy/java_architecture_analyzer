package com.analyzer.rules.std;

import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.Inspector;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.core.inspector.InspectorTargetType;
import com.analyzer.core.model.ProjectHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

import static com.analyzer.core.inspector.InspectorTags.*;

/**
 * Inspector that tags Java classes as application code (not library/framework
 * code)
 * based on configured package prefixes.
 * 
 * <p>
 * This inspector checks if a class's fully qualified name starts with any of
 * the
 * configured application package prefixes and tags it with
 * TAG_APPLICATION_CLASS.
 * This allows other inspectors to filter their analysis to only application
 * code.
 * </p>
 * 
 * <p>
 * Configuration:
 * Package prefixes are configured via the InventoryCommand --packages option
 * (e.g., --packages com.myapp,com.mycompany). These are stored in the Project
 * metadata and accessed via ProjectHolder.
 * </p>
 * 
 * <p>
 * Dependencies:
 * This inspector requires that the class has been identified with its FQN,
 * class name,
 * and Java detection before it can run.
 * </p>
 * 
 * @author Application Package Filtering Feature
 */
@InspectorDependencies(requires = {
        TAG_JAVA_DETECTED
}, produces = {
        TAG_APPLICATION_CLASS
})
public class ApplicationPackageTagInspector implements Inspector<JavaClassNode> {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationPackageTagInspector.class);

    private final ProjectHolder projectHolder;

    /**
     * Creates a new ApplicationPackageTagInspector with ProjectHolder injection.
     * 
     * @param projectHolder holder containing the Project with package filter
     *                      configuration
     */
    @Inject
    public ApplicationPackageTagInspector(ProjectHolder projectHolder) {
        this.projectHolder = projectHolder;
    }

    @Override
    public String getName() {
        return "Application Package Tag Inspector";
    }

    @Override
    public InspectorTargetType getTargetType() {
        return InspectorTargetType.JAVA_CLASS_NODE;
    }

    @Override
    public boolean canProcess(JavaClassNode classNode) {
        // Only process if all required tags/properties are present
        return classNode.hasTag(TAG_JAVA_DETECTED)
                && classNode.hasProperty(PROP_JAVA_CLASS_NAME)
                && classNode.hasProperty(PROP_JAVA_FULLY_QUALIFIED_NAME);
    }


    @Override
    public void inspect(JavaClassNode classNode, NodeDecorator<JavaClassNode> decorator) {
        // Get application package prefixes from Project via ProjectHolder
        List<String> appPackages = projectHolder.getApplicationPackages();

        if (appPackages.isEmpty()) {
            // No packages configured - skip tagging
            logger.trace("No application packages configured, skipping tagging for: {}",
                    classNode.getFullyQualifiedName());
            return;
        }

        String fqn = classNode.getFullyQualifiedName();

        // Check if class FQN starts with any configured package prefix
        for (String packagePrefix : appPackages) {
            if (fqn.startsWith(packagePrefix)) {
                decorator.enableTag(TAG_APPLICATION_CLASS);
                logger.debug("Tagged as application class: {} (matched prefix: {})", fqn, packagePrefix);
                return;
            }
        }

        // Class doesn't match any application package prefix
        logger.trace("Not an application class: {} (no prefix match)", fqn);
    }
}
