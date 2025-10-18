// 1. Enhanced ProjectFile with metadata storage
public class ProjectFile {
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    public <T> void setMetadata(String key, T value) {
        metadata.put(key, value);
    }
    
    public <T> Optional<T> getMetadata(String key, Class<T> type) {
        return Optional.ofNullable(metadata.get(key))
                .filter(type::isInstance)
                .map(type::cast);
    }
}

// 2. Typed metadata classes
public record EjbBeanMetadata(
    String beanType,
    String beanName, 
    List<String> methods,
    List<String> dependencies,
    int complexityScore
) {}

public record JBossConfigMetadata(
    String configType,
    Priority migrationPriority,
    List<String> dataSourceConfigs,
    List<String> securityConfigs,
    Map<String, String> properties
) {}

// 3. Clean inspector dependencies
@InspectorDependencies(
    need = { "JAVA_CLASS_DETECTED" },
    produces = { "EJB_BEAN_DETECTED" }  // Only trigger tags!
)
public class EjbBeanInspector {
    
    public void analyze(ProjectFile file) {
        // Analysis logic...
        
        // Set trigger tag
        file.setTag("EJB_BEAN_DETECTED", true);
        
        // Store rich metadata
        EjbBeanMetadata metadata = new EjbBeanMetadata(
            beanType, beanName, methods, dependencies, complexityScore
        );
        file.setMetadata("ejb.bean", metadata);
    }
}

// 4. Usage in dependent inspectors
@InspectorDependencies(need = { "EJB_BEAN_DETECTED" })
public class EjbMigrationInspector {
    
    public void analyze(ProjectFile file) {
        // Get rich metadata for analysis
        file.getMetadata("ejb.bean", EjbBeanMetadata.class)
            .ifPresent(ejbData -> {
                // Use typed metadata for migration analysis
                if (ejbData.complexityScore() > 10) {
                    // Complex migration logic
                }
            });
    }
}