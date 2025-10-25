#!/bin/bash

# Script to fix @Override method signatures that don't match the new base class

echo "Fixing @Override method signatures..."

# Fix ClocInspector - change decorate() to analyzeSourceFile()
echo "Fixing ClocInspector.java..."
sed -i 's/@Override[[:space:]]*public void decorate(ProjectFile file, ProjectFileDecorator decorator)/@Override\n    protected void analyzeSourceFile(ProjectFile file, ResourceLocation location, NodeDecorator<ProjectFile> decorator)/g' \
  src/main/java/com/analyzer/rules/metrics/ClocInspector.java

# Fix FilenameDetector - change decorate() to inspect()
echo "Fixing FilenameInspector.java..."
sed -i 's/@Override[[:space:]]*public void decorate(ProjectFile projectFile, ProjectFileDecorator decorator)/@Override\n    public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator)/g' \
  src/main/java/com/analyzer/inspectors/core/detection/FilenameDetector.java

# Fix ApplicationServerConfigDetector - change decorate() to inspect()
echo "Fixing ApplicationServerConfigDetector.java..."
sed -i 's/@Override[[:space:]]*public void decorate(ProjectFile projectFile, ProjectFileDecorator decorator)/@Override\n    public void inspect(ProjectFile projectFile, NodeDecorator<ProjectFile> decorator)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ApplicationServerConfigDetector.java

# Fix JndiLookupInspector - change decorate() to analyzeSourceFile()
echo "Fixing JndiLookupInspector.java..."
sed -i 's/@Override[[:space:]]*public void decorate(ProjectFile projectFile, ProjectFileDecorator decorator)/@Override\n    protected void analyzeSourceFile(ProjectFile projectFile, ResourceLocation location, NodeDecorator<ProjectFile> decorator)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/JndiLookupInspector.java

echo "Done fixing @Override methods!"
