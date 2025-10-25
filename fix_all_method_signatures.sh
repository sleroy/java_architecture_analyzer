#!/bin/bash

echo "Fixing all remaining method signature issues..."

# Function to fix inspect() method signatures (for Inspector implementations)
fix_inspect_method() {
    local file=$1
    echo "Fixing $file..."
    
    # Replace import
    sed -i 's/import com\.analyzer\.core\.export\.ProjectFileDecorator;/import com.analyzer.core.export.NodeDecorator;/g' "$file"
    
    # Fix method signature - handle both with and without @Override
    sed -i 's/public void decorate(ProjectFile projectFile, ProjectFileDecorator decorator)/public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator)/g' "$file"
    sed -i 's/public void decorate(ProjectFile \([a-zA-Z]*\), ProjectFileDecorator \([a-zA-Z]*\))/public void inspect(ProjectFile \1, NodeDecorator<ProjectFile> \2)/g' "$file"
}

# Function to fix analyzeSourceFile() method signatures (for AbstractSourceFileInspector subclasses)
fix_analyze_method() {
    local file=$1
    echo "Fixing $file..."
    
    # Replace import
    sed -i 's/import com\.analyzer\.core\.export\.ProjectFileDecorator;/import com.analyzer.core.export.NodeDecorator;/g' "$file"
    
    # Fix method signature
    sed -i 's/protected void analyzeSourceFile(ProjectFile \([a-zA-Z]*\), ResourceLocation \([a-zA-Z]*\), ProjectFileDecorator \([a-zA-Z]*\))/protected void analyzeSourceFile(ProjectFile \1, ResourceLocation \2, NodeDecorator<ProjectFile> \3)/g' "$file"
}

# Files needing inspect() method
echo "=== Fixing Inspector implementations ==="
fix_inspect_method "src/main/java/com/analyzer/inspectors/core/detection/FilenameInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/ejb2spring/ApplicationServerConfigDetector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/metrics/InheritanceDepthInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/metrics/AnnotationCountInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/metrics/ThreadLocalUsageInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/ejb2spring/EjbClassLoaderInspector.java"
fix_inspect_method "src/main/java/com/analyzer/inspectors/core/detection/BinaryClassDetector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/metrics/InterfaceNumberInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/std/TypeUsageInspector.java"

# Files needing analyzeSourceFile() method
echo "=== Fixing AbstractSourceFileInspector subclasses ==="
fix_analyze_method "src/main/java/com/analyzer/rules/ejb2spring/JndiLookupInspector.java"
fix_analyze_method "src/main/java/com/analyzer/rules/ejb2spring/EjbDeploymentDescriptorInspector.java"
fix_analyze_method "src/main/java/com/analyzer/rules/ejb2spring/JBossEjbConfigurationInspector.java"

# Abstract classes needing updates
echo "=== Fixing abstract classes ==="
fix_inspect_method "src/main/java/com/analyzer/rules/metrics/AbstractJavaAnnotationCountInspector.java"
fix_inspect_method "src/main/java/com/analyzer/rules/ejb2spring/CmpFieldMappingJavaBinaryInspector.java"
fix_inspect_method "src/main/java/com/analyzer/inspectors/packages/CodeQualityInspectorAbstractAbstract.java"

echo ""
echo "=== Fix complete! ==="
echo "Run 'mvn clean compile' to verify the fixes."
