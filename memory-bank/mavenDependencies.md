# Maven Dependencies for Missing Inspectors

## Required Dependencies for New Inspector Base Classes

### 1. JavaParser Library
**Inspector**: `JavaParserInspector`  
**Purpose**: Modern Java source code parsing with AST support

```xml
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.7</version>
</dependency>
```

**Features**:
- Complete Java 17+ syntax support
- AST traversal and manipulation
- Symbol resolution capabilities
- Excellent documentation and community support

### 2. Apache BCEL
**Inspector**: `BCELInspector`  
**Purpose**: Bytecode Engineering Library for class file analysis

```xml
<dependency>
    <groupId>org.apache.bcel</groupId>
    <artifactId>bcel</artifactId>
    <version>6.7.0</version>
</dependency>
```

**Features**:
- Mature bytecode analysis library
- High-level abstractions for class analysis
- Extensive bytecode manipulation capabilities
- Good alternative to ASM for certain use cases

### 3. Javassist
**Inspector**: `JavassistInspector`  
**Purpose**: Runtime bytecode manipulation and analysis

```xml
<dependency>
    <groupId>org.javassist</groupId>
    <artifactId>javassist</artifactId>
    <version>3.29.2-GA</version>
</dependency>
```

**Features**:
- Simple API for bytecode manipulation
- Runtime class modification
- Reflection-like interface
- Good for dynamic analysis scenarios

### 4. SonarSource Java Frontend (Optional)
**Inspector**: `SonarParserInspector`  
**Purpose**: Advanced Java parsing used by SonarQube

```xml
<dependency>
    <groupId>org.sonarsource.java</groupId>
    <artifactId>java-frontend</artifactId>
    <version>7.16.0.30901</version>
</dependency>
<dependency>
    <groupId>org.sonarsource.analyzer-commons</groupId>
    <artifactId>sonar-analyzer-commons</artifactId>
    <version>2.5.0.1358</version>
</dependency>
```

**Features**:
- Production-grade parser used by SonarQube
- Sophisticated semantic analysis
- Rule-based analysis framework
- May require additional SonarSource dependencies

### 5. JBoss Forge Roaster (Optional)
**Inspector**: `RoasterInspector`  
**Purpose**: Code generation and source manipulation

```xml
<dependency>
    <groupId>org.jboss.forge.roaster</groupId>
    <artifactId>roaster-api</artifactId>
    <version>2.28.0.Final</version>
</dependency>
<dependency>
    <groupId>org.jboss.forge.roaster</groupId>
    <artifactId>roaster-jdt</artifactId>
    <version>2.28.0.Final</version>
</dependency>
```

**Features**:
- Eclipse JDT-based parsing
- Code generation capabilities
- Fluent API for source manipulation
- Good for code transformation scenarios

## Implementation Priority

### Phase 1: Essential Dependencies (Immediate)
1. **No additional dependencies needed** for:
   - `RegExpFileInspector` (uses standard Java regex)
   - `CountRegexpInspector` (uses standard Java regex)
   - `TextFileInspector` (uses standard Java I/O)

### Phase 2: Popular Libraries (High Priority)
2. **JavaParser** - Most widely used, excellent documentation
3. **Apache BCEL** - Mature, stable alternative to ASM

### Phase 3: Specialized Tools (Lower Priority)
4. **Javassist** - For runtime scenarios
5. **SonarSource** - Advanced analysis (complex setup)
6. **Roaster** - Code generation focus (specialized use case)

## Version Management Strategy

### Dependency Management
```xml
<dependencyManagement>
    <dependencies>
        <!-- JavaParser BOM for version consistency -->
        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>
        
        <!-- Apache BCEL -->
        <dependency>
            <groupId>org.apache.bcel</groupId>
            <artifactId>bcel</artifactId>
            <version>${bcel.version}</version>
        </dependency>
        
        <!-- Javassist -->
        <dependency>
            <groupId>org.javassist</groupId>
            <artifactId>javassist</artifactId>
            <version>${javassist.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Properties
```xml
<properties>
    <javaparser.version>3.25.7</javaparser.version>
    <bcel.version>6.7.0</bcel.version>
    <javassist.version>3.29.2-GA</javassist.version>
</properties>
```

## Testing Dependencies

Each new inspector will require test dependencies:

```xml
<!-- For testing with sample Java files -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>

<!-- For assertion utilities -->
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.24.2</version>
    <scope>test</scope>
</dependency>
```

## Size and Licensing Considerations

### Dependency Sizes (Approximate)
- **JavaParser**: ~2.5MB
- **Apache BCEL**: ~600KB  
- **Javassist**: ~800KB
- **SonarSource**: ~15MB+ (multiple JARs)
- **Roaster**: ~5MB+ (includes Eclipse JDT)

### Licensing
- **JavaParser**: Apache License 2.0 ✅
- **Apache BCEL**: Apache License 2.0 ✅
- **Javassist**: Apache License 2.0 ✅
- **SonarSource**: LGPL 3.0 (⚠️ copyleft)
- **Roaster**: Eclipse Public License 1.0 (⚠️ copyleft)

## Recommendation

Start with **JavaParser** and **Apache BCEL** as they provide the best balance of:
- Functionality coverage
- Community support
- License compatibility
- Reasonable size overhead
- Mature, stable APIs
