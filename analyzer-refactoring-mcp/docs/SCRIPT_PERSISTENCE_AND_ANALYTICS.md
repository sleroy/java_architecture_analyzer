# Groovy Script Persistence and Analytics

## Overview

This document describes the persistent storage and analytics tracking features for dynamically generated Groovy visitor scripts. These features ensure scripts survive server restarts and provide comprehensive usage tracking for monitoring and optimization.

## Features

### 1. Persistent Script Storage

**Location**: `${user.home}/.java-refactoring-mcp/scripts/`

Successfully generated Groovy scripts are automatically saved to disk with associated metadata:

**Script Files**:
- Format: `{sha256-hash}.groovy`
- Contains: Complete Groovy visitor script source code

**Metadata Files**:
- Format: `{sha256-hash}.json`
- Contains: Script generation details and usage statistics

**Metadata Structure**:
```json
{
  "scriptHash": "abc123...",
  "patternDescription": "singleton classes with private constructor",
  "nodeType": "ClassDeclaration",
  "projectPath": "/path/to/project",
  "filePaths": ["src/main/java/..."],
  "generatedAt": "2025-01-13T16:05:00Z",
  "generationAttempts": 1,
  "validated": true,
  "usageCount": 5,
  "lastUsed": "2025-01-13T16:10:00Z",
  "successCount": 5,
  "failureCount": 0
}
```

### 2. Cache Warming

On application startup, all persisted scripts are automatically loaded into memory cache:

1. Scans script storage directory
2. Loads script source and metadata
3. Compiles scripts
4. Populates in-memory cache
5. Logs number of scripts loaded

**Benefits**:
- Instant availability of previously generated scripts
- Faster cold-start performance
- Reduced API calls to Bedrock on restart

### 3. Two-Tier Caching

**Tier 1: In-Memory (Caffeine)**
- Max size: 100 scripts
- Expiration: 60 minutes after write
- Sub-millisecond access time

**Tier 2: Disk Storage**
- Max scripts: 500
- Auto-cleanup: Scripts unused for 90+ days
- Millisecond access time

**Lookup Flow**:
```
Request → Memory Cache?
    ├─ Hit: Return script
    └─ Miss → Disk Storage?
        ├─ Hit: Load to memory → Return script
        └─ Miss → Generate new script
```

### 4. Usage Analytics

**Tracking Location**: `${user.home}/.java-refactoring-mcp/scripts/analytics/`

Every tool call is tracked with comprehensive metrics:

**Analytics Files**:
- Format: `calls-{YYYY-MM}.jsonl` (JSON Lines)
- One event per line for easy streaming/parsing
- Monthly rotation for manageability

**Tracked Metrics**:
```json
{
  "callId": "uuid-123",
  "timestamp": "2025-01-13T16:05:00Z",
  "patternDescription": "singleton classes",
  "nodeType": "ClassDeclaration",
  "projectPath": "/path/to/project",
  "filePaths": ["..."],
  "success": true,
  "scriptGenerated": true,
  "cacheHit": false,
  "matchesFound": 5,
  "executionTimeMs": 345,
  "generationAttempts": 1,
  "errorMessage": null,
  "filesAnalyzed": 150,
  "linesScanned": 45000,
  "tokensUsed": 3500,
  "estimatedCost": 0.0042
}
```

### 5. Automated Reporting

**Daily Summary** (midnight):
```
=== Groovy Script Analytics - Daily Summary ===
Date: 2025-01-13
Total Calls: 47
Successful: 45 (95.7%)
Failed: 2 (4.3%)
Cache Hit Rate: 68.1%
Scripts Generated: 15
Average Execution Time: 287ms
Total Matches Found: 1,234
Total Bedrock Tokens: 52,400
Estimated Cost: $0.63
Top Patterns:
  1. singleton classes (12 calls)
  2. factory pattern (8 calls)
  3. static methods (7 calls)
```

**Weekly Impact Report** (available via API):
- Pattern effectiveness (matches per call)
- Cost efficiency (tokens per pattern)
- Performance trends
- Script reusability statistics

## Configuration

### Storage Configuration

```properties
# Enable/disable script persistence
groovy.script.storage.enabled=true

# Storage directory path
groovy.script.storage.path=${user.home}/.java-refactoring-mcp/scripts

# Maximum number of scripts to store
groovy.script.storage.max-scripts=500

# Enable automatic cleanup of old scripts
groovy.script.storage.cleanup.enabled=true

# Remove scripts unused for this many days
groovy.script.storage.cleanup.unused-days=90
```

### Analytics Configuration

```properties
# Enable/disable analytics tracking
groovy.analytics.enabled=true

# Analytics storage directory
groovy.analytics.storage.path=${groovy.script.storage.path}/analytics

# Keep analytics data for this many months
groovy.analytics.retention-months=6
```

### Cache Configuration

```properties
# In-memory cache settings
groovy.cache.enabled=true
groovy.cache.max-size=100
groovy.cache.expire-after-write-minutes=60
groovy.cache.record-stats=true
```

## Benefits

### Performance

- **Cold Start**: Scripts available immediately after restart
- **Cache Hit**: <100ms response time (vs. 5-10s for generation)
- **Reduced Latency**: Two-tier caching minimizes API calls

### Cost Savings

- **Fewer API Calls**: Reuse scripts across sessions
- **Token Savings**: ~94% reduction compared to full source analysis
- **Estimated ROI**: Payback after ~20 script reuses

### Operational Visibility

- **Usage Patterns**: Understand which patterns are most requested
- **Performance Metrics**: Track execution times and success rates
- **Cost Tracking**: Monitor Bedrock API usage and costs
- **Trend Analysis**: Identify patterns over time

### Reliability

- **Persistence**: Scripts survive crashes and restarts
- **Backup**: File-based storage easy to backup/restore
- **Audit Trail**: Complete history of tool usage
- **Debugging**: Access to generated scripts for troubleshooting

## Storage Management

### Automatic Cleanup

**On Startup**:
- Removes scripts unused for 90+ days
- Removes analytics files older than 6 months
- Enforces maximum script limit (LRU eviction)

**Manual Cleanup**:
```bash
# Remove all scripts
rm -rf ~/.java-refactoring-mcp/scripts/*.groovy
rm -rf ~/.java-refactoring-mcp/scripts/*.json

# Remove all analytics
rm -rf ~/.java-refactoring-mcp/scripts/analytics/

# Keep storage, remove data
find ~/.java-refactoring-mcp/scripts -type f -delete
```

### Disk Space Usage

**Typical Script Size**: 2-5 KB
**Metadata Size**: 1 KB
**Total per Script**: ~3-6 KB

**Estimated Storage**:
- 100 scripts: ~500 KB
- 500 scripts (max): ~2.5 MB
- Analytics (6 months @ 50 calls/day): ~3 MB

**Total**: ~5.5 MB maximum

### Backup and Restore

**Backup**:
```bash
# Create backup
tar -czf mcp-scripts-backup.tar.gz ~/.java-refactoring-mcp/scripts/

# Or use rsync
rsync -av ~/.java-refactoring-mcp/scripts/ /backup/location/
```

**Restore**:
```bash
# Extract backup
tar -xzf mcp-scripts-backup.tar.gz -C ~/

# Or use rsync
rsync -av /backup/location/ ~/.java-refactoring-mcp/scripts/
```

## Querying Analytics

### Programmatic Access

```java
@Autowired
private GroovyScriptAnalytics analytics;

// Get summary for last 7 days
Instant endDate = Instant.now();
Instant startDate = endDate.minus(7, ChronoUnit.DAYS);
AnalyticsSummary summary = analytics.getSummary(startDate, endDate);

// Access metrics
int totalCalls = summary.totalCalls;
double cacheHitRate = summary.cacheHitRate;
Map<String, Long> topPatterns = summary.topPatterns;
```

### Manual Analysis

**Parse JSON Lines**:
```bash
# Count calls per day
jq -r '.timestamp[:10]' calls-2025-01.jsonl | sort | uniq -c

# Find most common patterns
jq -r '.patternDescription' calls-2025-01.jsonl | sort | uniq -c | sort -rn | head -10

# Calculate average execution time
jq '.executionTimeMs' calls-2025-01.jsonl | awk '{sum+=$1; count++} END {print sum/count}'

# Find failed calls
jq 'select(.success == false)' calls-2025-01.jsonl
```

## Monitoring

### Key Metrics to Monitor

1. **Cache Hit Rate**: Should be >60% for stable usage patterns
2. **Success Rate**: Should be >95% for well-configured system
3. **Average Execution Time**: Should trend down as cache warms
4. **Script Count**: Monitor growth towards max limit
5. **Disk Usage**: Should stay under 10 MB

### Performance Indicators

**Good**:
- Cache hit rate >70%
- Average execution <500ms
- Success rate >95%

**Needs Attention**:
- Cache hit rate <50%
- Average execution >2s
- Success rate <90%

**Critical**:
- Cache hit rate <30%
- Average execution >5s
- Success rate <80%

## Troubleshooting

### Scripts Not Persisting

**Check**:
1. `groovy.script.storage.enabled=true` in config
2. Write permissions on storage directory
3. Disk space available
4. Logs for I/O errors

### Analytics Not Recording

**Check**:
1. `groovy.analytics.enabled=true` in config
2. Write permissions on analytics directory
3. `GroovyScriptAnalytics` bean initialized
4. Check logs for errors during `flushPendingMetrics()`

### Cache Not Warming on Startup

**Check**:
1. Scripts exist in storage directory
2. `@PostConstruct` method executing
3. Script compilation succeeds
4. Check logs for "Warmed cache with N scripts"

### High Disk Usage

**Actions**:
1. Reduce `groovy.script.storage.max-scripts`
2. Reduce `groovy.script.storage.cleanup.unused-days`
3. Reduce `groovy.analytics.retention-months`
4. Manual cleanup of old data

## Security Considerations

### Script Storage

- Scripts stored with user-only permissions (0600)
- No sensitive data in script source
- Project paths may be sensitive (configure anonymization if needed)

### Analytics Data

- Contains project paths and pattern descriptions
- No source code stored
- GDPR-compliant with configurable retention
- Easy data export/deletion

### Access Control

- Files owned by application user
- No network exposure
- Local filesystem only

## Future Enhancements

### Planned Features

1. **Script Sharing**: Export/import script packs
2. **Remote Storage**: S3/cloud storage support
3. **Advanced Analytics**: ML-based pattern recommendations
4. **Script Versioning**: Track script evolution over time
5. **Performance Profiling**: Detailed execution breakdowns
6. **Cost Optimization**: Automatic script consolidation

### Extensibility

- Custom analytics providers
- Pluggable storage backends
- Event-based notifications
- Integration with monitoring systems

## References

- [Groovy Visitor Generation](./GROOVY_VISITOR_GENERATION.md)
- [Configuration Guide](./CONFIGURATION.md)
- [Search Java Pattern Tool](./SEARCH_JAVA_PATTERN_TOOL.md)
