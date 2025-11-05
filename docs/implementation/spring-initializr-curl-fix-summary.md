# Spring Initializr Curl Command Fix - Migration Tool

## Problem Summary

The Java Architecture Analyzer's EJB-to-Spring Boot migration tool was failing during Phase 2 "Spring Boot Project Initialization" with the error:
```
curl: (3) URL using bad/illegal format or missing URL
```

## Root Cause Analysis

### Primary Issues Identified

1. **Incorrect HTTP Method**: The original curl command used `-G` flag which forces GET method, but Spring Initializr expects POST requests for project generation.

2. **Invalid Spring Boot Version**: The migration plan was configured for Spring Boot 2.7.18, but Spring Initializr no longer supports versions below 3.4.0.

3. **Invalid Java Package Name**: Variable substitution created `br.com.semeru.semeru-springboot` which contains invalid hyphens for Java packages.

4. **Java Version Compatibility**: Spring Boot 3.x requires Java 17+, but the configuration specified Java 8/11.

### Original Failing Command
```bash
curl -G https://start.spring.io/starter.zip \
  -d packageName=br.com.semeru.semeru-springboot \  # Invalid package name with hyphens
  -d bootVersion=2.7.18 \                          # Unsupported version
  # ... other parameters
```

### Spring Initializr Error Response
```json
{
  "timestamp":"2025-11-03T15:17:01.195+00:00",
  "status":400,
  "error":"Bad Request",
  "message":"Invalid Spring Boot version '2.7.18', Spring Boot compatibility range is >=3.4.0",
  "path":"/starter.zip"
}
```

## Fixes Applied

### 1. Updated Variables Configuration
**File**: `migrations/ejb2spring/common/variables.yaml`

**Changes**:
```yaml
# Before
spring_boot_version: "2.7.18"
java_version: "8"

# After  
spring_boot_version: "3.4.0"
java_version: "17"
```

### 2. Fixed Curl Command Structure
**File**: `migrations/ejb2spring/phases/phase1-initialization.yaml`

**Key fixes**:
- Removed `-G` flag to use POST method
- Added explicit `-X POST` 
- Fixed package name from `${group_id}.${artifact_id}` to `${group_id}.springboot`
- Updated dependency list to essential ones for Spring Boot 3.x compatibility

**Corrected Command**:
```bash
curl -X POST https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=${spring_boot_version} \
  -d baseDir=semeru-springboot \
  -d groupId=${group_id} \
  -d artifactId=${artifact_id} \
  -d name="Semeru Spring Boot Application" \
  -d description="Semeru application migrated from JBoss EJB 2 to Spring Boot" \
  -d packageName=${group_id}.springboot \
  -d packaging=jar \
  -d javaVersion=${java_version} \
  -d dependencies=web,data-jpa,validation,actuator,devtools,configuration-processor,h2 \
  -o springboot-baseline.zip
```

## Validation Process

### 1. Command Line Testing
✅ **Test 1**: Verified Spring Boot 2.7.18 rejection
```bash
# Returns 400 error with version compatibility message
curl -X POST https://start.spring.io/starter.zip -d bootVersion=2.7.18 ...
```

✅ **Test 2**: Validated Spring Boot 3.4.0 success
```bash
# Downloads 17KB valid Spring Boot project
curl -X POST https://start.spring.io/starter.zip -d bootVersion=3.4.0 ...
```

✅ **Test 3**: Verified project structure
- Extracted project contains proper Maven structure
- POM.xml has correct Spring Boot 3.4.0 parent
- Java version automatically set to 17
- All requested dependencies included

### 2. Generated Project Verification
✅ **POM Structure**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.0</version>
</parent>
<properties>
    <java.version>17</java.version>
</properties>
```

✅ **Package Structure**: `br/com/semeru/springboot/` (valid Java package)

✅ **Dependencies**: All essential Spring Boot 3.x starters included

## Impact Analysis

### Migration Compatibility
- **Spring Boot 3.x Changes**: The migration tool now targets modern Spring Boot with:
  - Jakarta EE namespace (javax.* → jakarta.*)
  - Updated dependency management
  - Java 17+ requirement

### Breaking Changes
1. **Java Version**: Minimum Java 17 required (was Java 8)
2. **Spring Boot Version**: 3.4.0 (was 2.7.18) 
3. **Package Names**: Updated namespace from legacy EJB patterns

## Prevention Measures

### 1. Version Validation
**Recommendation**: Add Spring Initializr API validation before project generation:
```bash
# Check supported versions
curl -s https://start.spring.io/actuator/info | jq '.git.build.version'
```

### 2. Package Name Validation
**Recommendation**: Add validation in `CommandBlock` to check Java package naming rules:
- No hyphens allowed
- Must follow Java identifier rules
- Validate before variable substitution

### 3. Dependency Compatibility Check
**Recommendation**: Implement dependency version compatibility validation against Spring Boot BOM.

### 4. Integration Testing
**Recommendation**: Add automated tests for Spring Initializr integration:
```java
@Test
public void testSpringInitializrGeneration() {
    // Test actual curl command execution
    // Validate generated project structure
    // Verify Maven compilation
}
```

## Related Files Modified

1. **Variables**: `migrations/ejb2spring/common/variables.yaml`
2. **Curl Command**: `migrations/ejb2spring/phases/phase1-initialization.yaml` 
3. **Documentation**: This file

## Future Enhancements

1. **Dynamic Version Detection**: Query Spring Initializr API for supported versions
2. **Fallback POM Generation**: Manual POM creation if Spring Initializr fails
3. **Version Migration Guide**: Documentation for Spring Boot 2.x→3.x migration considerations
4. **Parameter Validation**: Pre-flight validation of all curl parameters

## Testing Commands

To test the fix:
```bash
# Test the corrected command
curl -X POST https://start.spring.io/starter.zip \
  -d type=maven-project \
  -d bootVersion=3.4.0 \
  -d groupId=br.com.semeru \
  -d artifactId=semeru-springboot \
  -d packageName=br.com.semeru.springboot \
  -d javaVersion=17 \
  -d dependencies=web,data-jpa,validation \
  -o test-project.zip

# Verify project structure
unzip -l test-project.zip
```

## Conclusion

The migration tool now successfully generates Spring Boot 3.4.0 projects compatible with modern Java development practices. The fix addresses both immediate technical issues and provides a foundation for more robust project generation in future updates.
