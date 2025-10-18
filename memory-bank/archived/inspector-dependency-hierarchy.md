# Inspector Dependency Hierarchy

## Dependency Levels

### Level 1: Fundamental Detectors (No Dependencies)
- FileExtensionInspector, FilenameInspector
- SourceFileTagInspector, JavaSourceFileInspector, IsBinaryJavaClassInspector
- Produces: Basic file type and language detection tags

### Level 2: Base Classes
- BinaryClassInspector (depends: RESOURCE_HAS_BINARY)
- SourceFileInspector (depends: SOURCE_FILE)

### Level 3: Language-Specific
- JavaSourceFileInspector (depends: JAVA_DETECTED + JAVA_IS_SOURCE)
- BinaryClassFQNInspector (depends: JAVA_IS_BINARY + JAVA_DETECTED)
- Produces: JAVA_CLASS_NAME, JAVA_PACKAGE_NAME, JAVA_FULLY_QUALIFIED_NAME

### Level 4: Specialized Analysis
- Rule inspectors inherit dependencies from base classes
- AbstractClassLoaderBasedInspector depends on both FQN extractors

## Key Benefits
- Clean separation: supports() for format, dependencies for prerequisites
- Inheritance-safe: super.depends(tags) pattern eliminates manual merging
- Consistent tags: All use InspectorTags constants