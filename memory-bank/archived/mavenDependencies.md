# Maven Dependencies

## Required for Inspector Base Classes

### Essential (No additional dependencies)
- RegExpFileInspector, CountRegexpInspector, TextFileInspector (Java standard library)

### High Priority
```xml
<!-- JavaParser for AST analysis -->
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.7</version>
</dependency>

<!-- Apache BCEL for bytecode analysis -->
<dependency>
    <groupId>org.apache.bcel</groupId>
    <artifactId>bcel</artifactId>
    <version>6.7.0</version>
</dependency>
```

### Lower Priority
- Javassist (3.29.2-GA) - Runtime bytecode manipulation
- SonarSource Java Frontend - Advanced parsing (complex setup)
- JBoss Forge Roaster - Code generation (specialized)

## Licensing
- JavaParser, BCEL, Javassist: Apache License 2.0 ✅
- SonarSource: LGPL 3.0 ⚠️
- Roaster: Eclipse Public License ⚠️