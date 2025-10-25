# Class-Centric Inspector Refactoring Plan

This document outlines the necessary steps to refactor the existing inspectors to be class-centric, using the `JavaClassNode` as the primary target for analysis results.

## Refactoring Strategy

The core idea is to shift from tagging `ProjectFile` objects to setting properties on `JavaClassNode` objects. This will be achieved by:

1.  Changing the base class of the inspector to either `AbstractJavaClassInspector` (for source analysis) or a new `AbstractBinaryClassNodeInspector` (for binary analysis).
2.  Replacing `projectFile.addTag(...)` or `projectFile.setTag(...)` with `classNode.setProperty(...)`.
3.  Adapting the inspector's logic to work with a `JavaClassNode` instead of a `ProjectFile`.

---

## EJB to Spring Inspectors (`ejb2spring`)

### `ApplicationServerConfigDetector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (This inspector operates on configuration files, not Java classes, so it does not need to be refactored.)
- **Modifications:** None.

### `BusinessDelegatePatternJavaSourceInspector.java`
- **Current Base Class:** `AbstractSourceFileInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature to `inspect(ProjectFile projectFile, JavaClassNode classNode)`.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `CmpFieldMappingJavaBinaryInspector.java`
- **Current Base Class:** `AbstractBinaryClassInspector`
- **Target Base Class:** `AbstractBinaryClassNodeInspector` (A new base class to be created)
- **Modifications:**
    - Create a new `AbstractBinaryClassNodeInspector` similar to `AbstractJavaClassInspector`.
    - Extend `AbstractBinaryClassNodeInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `ComplexCmpRelationshipJavaSourceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `CustomDataTransferPatternJavaSourceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `DatabaseResourceManagementInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `DeclarativeTransactionJavaSourceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `EjbBinaryClassInspector.java`
- **Current Base Class:** `AbstractBinaryClassInspector`
- **Target Base Class:** `AbstractBinaryClassNodeInspector`
- **Modifications:**
    - Extend `AbstractBinaryClassNodeInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `EjbClassLoaderInspector.java`
- **Current Base Class:** `AbstractClassLoaderBasedInspector`
- **Target Base Class:** `AbstractClassLoaderBasedInspector` (This inspector operates on the class loader and does not need to be refactored.)
- **Modifications:** None.

### `EjbCreateMethodUsageInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `EjbDeploymenDescriptorAnalyzerInspector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (Operates on XML files.)
- **Modifications:** None.

### `EjbDeploymentDescriptorDetector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (Operates on XML files.)
- **Modifications:** None.

### `EjbHomeInterfaceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `EjbRemoteInterfaceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `EntityBeanJavaSourceInspector.java`
- **Status:** Already refactored.

### `IdentifyServletSourceInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `JBossEjbConfigurationInspector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (Operates on XML files.)
- **Modifications:** None.

### `JdbcDataAccessPatternInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `JndiLookupInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `LegacyFrameworkDetector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (Operates on XML files.)
- **Modifications:** None.

### `MessageDrivenBeanInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `ProgrammaticTransactionUsageInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `SessionBeanJavaSourceInspector.java`
- **Status:** Already refactored.

### `StatefulSessionStateInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

---

## Graph Inspectors (`graph`)

### `BinaryJavaClassNodeInspector.java`
- **Current Base Class:** `AbstractBinaryClassInspector`
- **Target Base Class:** `AbstractBinaryClassInspector` (This inspector creates the `JavaClassNode`, so it remains as is.)
- **Modifications:** None.

### `JavaImportGraphInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Adapt logic to create edges between `JavaClassNode`s.

### `SourceJavaClassNodeInspector.java`
- **Current Base Class:** `AbstractSourceFileInspector`
- **Target Base Class:** `AbstractSourceFileInspector` (This inspector creates the `JavaClassNode`, so it remains as is.)
- **Modifications:** None.

---

## Metrics Inspectors (`metrics`)

### `AnnotationCountInspector.java`
- **Current Base Class:** `AbstractJavaAnnotationCountInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `ClocInspector.java`
- **Current Base Class:** `AbstractProjectFileInspector`
- **Target Base Class:** `AbstractProjectFileInspector` (Operates on files.)
- **Modifications:** None.

### `CyclomaticComplexityInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `InheritanceDepthInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `InterfaceNumberInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `MethodCountInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

### `TypeUsageInspector.java`
- **Current Base Class:** `AbstractJavaParserInspector`
- **Target Base Class:** `AbstractJavaClassInspector`
- **Modifications:**
    - Extend `AbstractJavaClassInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.

---

## Standard Inspectors (`std`)

### `BinaryClassFQNInspector.java`
- **Current Base Class:** `AbstractBinaryClassInspector`
- **Target Base Class:** `AbstractBinaryClassInspector` (This inspector is fine as it is, it extracts the FQN).
- **Modifications:** None.

### `JavaVersionInspector.java`
- **Current Base Class:** `AbstractBinaryClassInspector`
- **Target Base Class:** `AbstractBinaryClassInspector` (Operates on class files.)
- **Modifications:** None.

### `TypeInspectorASMInspector.java`
- **Current Base Class:** `AbstractASMInspector`
- **Target Base Class:** `AbstractBinaryClassNodeInspector`
- **Modifications:**
    - Extend `AbstractBinaryClassNodeInspector`.
    - Change the `inspect` method signature.
    - Replace `projectFile.addTag(...)` with `classNode.setProperty(...)`.
