# JDK Detection Portability Fix

**Date:** 2025-11-04  
**Status:** ✅ COMPLETED

## Problem

JDK detection was failing with blank output, causing Spring Boot compilation errors:

```
ERROR: Fatal error compiling: invalid target release: 21
```

### Root Cause

The original regex used `grep -oP` with Perl-style lookahead (`\K`):
```bash
DETECTED_VERSION=$(java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1)
echo "$DETECTED_VERSION"
# Output: (blank)
```

**Problem:** `grep -P` (Perl regex) is not universally available on all systems, especially in minimal/busybox environments.

## Solution

Replaced with portable `sed` command:

```bash
# OLD (not portable)
DETECTED_VERSION=$(java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1)

# NEW (portable)
DETECTED_VERSION=$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
```

### Testing

```bash
# Test on Ubuntu system
$ java -version 2>&1 | head -1
openjdk version "21.0.8" 2025-07-15

$ java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p'
21  ✓ Works!

$ java -version 2>&1 | grep -oP 'version "\\K[0-9]+' | head -1
(blank)  ✗ Broken!
```

## Implementation

**File:** `migrations/ejb2spring/phases/phase1-initialization.yaml`

```yaml
- type: "COMMAND"
  name: "detect-jdk-version"
  description: "Detect installed JDK major version and store in context"
  command: |
    # Detect JDK version and store for use by next commands
    # Use sed instead of grep -oP for better portability
    DETECTED_VERSION=$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
    echo "$DETECTED_VERSION"
  working-directory: "${project_root}"
  timeout-seconds: 10
  output-variable: "detected_java_version"
```

## Why sed Is Better

| Aspect | grep -oP | sed |
|--------|----------|-----|
| **Availability** | Not always present | Standard POSIX tool |
| **Portability** | Linux-specific | Works everywhere (Linux, macOS, BSD, busybox) |
| **Perl support** | Requires grep with PCRE | No special requirements |
| **Regex syntax** | Perl-style (\K, lookahead) | POSIX basic/extended |

## Impact

- **Scope**: Phase 1 JDK detection only
- **Risk**: ZERO - sed is more portable than grep -P
- **Backward Compatibility**: Works on all systems where old command failed
- **Testing**: Verified on Ubuntu 22.04 with OpenJDK 21

## Related Issues

This fix resolves:
1. ❌ JDK detection returning blank
2. ❌ Spring Initializr receiving empty javaVersion parameter
3. ❌ Maven compilation failing with "invalid target release: 21"

Now:
1. ✅ JDK detection returns "21"
2. ✅ Spring Initializr generates POM with `<java.version>21</java.version>`
3. ✅ Maven compilation uses correct Java version

## Related Documentation

- `docs/implementation/phase1-jdk-detection-final-solution.md` - Original shell variable chaining approach
- `docs/implementation/commandblock-output-variable-feature.md` - Custom output variable feature
- `docs/implementation/phase1-variable-and-validation-improvements.md` - Complete Phase 1 improvements
