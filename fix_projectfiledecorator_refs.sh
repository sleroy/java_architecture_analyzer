#!/bin/bash

echo "Fixing all ProjectFileDecorator references to NodeDecorator<ProjectFile>..."

# Find all Java files with ProjectFileDecorator and fix them
find src/main/java -name "*.java" -type f -exec grep -l "ProjectFileDecorator" {} \; | while read file; do
    echo "Fixing ProjectFileDecorator in: $file"
    
    # Replace ProjectFileDecorator with NodeDecorator<ProjectFile> in variable declarations
    sed -i 's/ProjectFileDecorator decorator/NodeDecorator<ProjectFile> decorator/g' "$file"
    sed -i 's/ProjectFileDecorator projectFileDecorator/NodeDecorator<ProjectFile> projectFileDecorator/g' "$file"
    sed -i 's/ProjectFileDecorator \([a-zA-Z][a-zA-Z0-9]*\)/NodeDecorator<ProjectFile> \1/g' "$file"
    
    # Fix any remaining import statements
    sed -i 's/import com\.analyzer\.core\.export\.ProjectFileDecorator;/import com.analyzer.core.export.NodeDecorator;/g' "$file"
done

echo ""
echo "=== Complete! ==="
echo "Fixed all ProjectFileDecorator references."
