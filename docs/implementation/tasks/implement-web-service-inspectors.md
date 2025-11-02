# Task: Implement Web Service Inspectors

**Priority:** HIGH  
**Estimated Effort:** 4-6 hours  
**Dependencies:** None  
**Goal:** Replace remaining grep commands in phase4-8-integration.yaml

---

## Objective

Implement two inspectors to detect JAX-WS and JAX-RS web services, enabling replacement of grep commands with GRAPH_QUERY blocks.

## Current State

### Grep Commands to Replace (phase4-8-integration.yaml):
```bash
# JAX-WS Web Services
grep -rn '@WebService\|@WebMethod' ${project_root}/src --include='*.java'

# JAX-RS REST Services  
grep -rn '@Path\|@GET\|@POST\|@PUT\|@DELETE' ${project_root}/src --include='*.java'
```

### Missing Inspectors:
1. **WebServiceInspector** - JAX-WS SOAP services
2. **RestServiceInspector** - JAX-RS REST services

---

## Inspector 1: WebServiceInspector

### Location
`analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/WebServiceInspector.java`

### Annotations to Detect
**JAX-WS (javax.jws.* and jakarta.jws.*)**
- `@WebService` - Marks a class as a web service endpoint
- `@WebMethod` - Marks a method as a web service operation
- `@WebParam` - Describes a parameter
- `@WebResult` - Describes the return value
- `@SOAPBinding` - Customizes SOAP binding

### Tags to Produce
```java
// In EjbMigrationTags.java
public static final String WEBSERVICE_JAX_WS = "webservice.jaxws.detected";
public static final String WEBSERVICE_SOAP_ENDPOINT = "webservice.soap.endpoint";
public static final String WEBSERVICE_OPERATION = "webservice.operation";
public static final String SPRING_REST_CONTROLLER_CONVERSION = "spring.conversion.restController";
```

### Implementation Pattern
```java
@InspectorDependencies(
    requires = {InspectorTags.TAG_JAVA_IS_SOURCE}, 
    produces = {
        EjbMigrationTags.WEBSERVICE_JAX_WS,
        EjbMigrationTags.WEBSERVICE_SOAP_ENDPOINT,
        EjbMigrationTags.SPRING_REST_CONTROLLER_CONVERSION
    }
)
public class WebServiceInspector extends AbstractJavaClassInspector {
    
    @Override
    protected void analyzeClass(
        ProjectFile projectFile, 
        JavaClassNode classNode, 
        TypeDeclaration<?> type,
        NodeDecorator<ProjectFile> projectFileDecorator
    ) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            WebServiceDetector detector = new WebServiceDetector();
            classDecl.accept(detector, null);
            
            if (detector.isWebService()) {
                WebServiceInfo info = detector.getWebServiceInfo();
                
                // Set tags
                projectFileDecorator.enableTag(WEBSERVICE_JAX_WS);
                classNode.enableTag(WEBSERVICE_JAX_WS);
                
                if (info.hasEndpoint) {
                    classNode.enableTag(WEBSERVICE_SOAP_ENDPOINT);
                }
                
                // Migration recommendation
                classNode.enableTag(SPRING_REST_CONTROLLER_CONVERSION);
                
                // Store analysis data
                classNode.setProperty("webservice.analysis", info);
                
                // Complexity rating
                classNode.getMetrics().setMaxMetric(
                    METRIC_MIGRATION_COMPLEXITY, 
                    info.calculateComplexity()
                );
            }
        }
    }
    
    private static class WebServiceDetector extends VoidVisitorAdapter<Void> {
        private WebServiceInfo info = new WebServiceInfo();
        private boolean isWebService = false;
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check for @WebService
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String name = annotation.getNameAsString();
                if ("WebService".equals(name) || 
                    "javax.jws.WebService".equals(name) ||
                    "jakarta.jws.WebService".equals(name)) {
                    isWebService = true;
                    info.hasEndpoint = true;
                    info.className = classDecl.getNameAsString();
                    
                    // Extract annotation parameters
                    if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
                        for (MemberValuePair pair : normalAnnotation.getPairs()) {
                            String paramName = pair.getNameAsString();
                            String value = pair.getValue().toString();
                            info.annotationParams.put(paramName, value);
                        }
                    }
                }
            }
            
            // Check methods for @WebMethod
            for (MethodDeclaration method : classDecl.getMethods()) {
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    String name = annotation.getNameAsString();
                    if ("WebMethod".equals(name) ||
                        "javax.jws.WebMethod".equals(name) ||
                        "jakarta.jws.WebMethod".equals(name)) {
                        info.webMethods.add(method.getNameAsString());
                    }
                }
            }
            
            super.visit(classDecl, arg);
        }
        
        public boolean isWebService() {
            return isWebService;
        }
        
        public WebServiceInfo getWebServiceInfo() {
            return info;
        }
    }
    
    public static class WebServiceInfo {
        public String className;
        public boolean hasEndpoint;
        public List<String> webMethods = new ArrayList<>();
        public Map<String, String> annotationParams = new HashMap<>();
        
        public double calculateComplexity() {
            // Simple endpoint = LOW
            // Multiple operations = MEDIUM
            // Complex bindings = HIGH
            if (webMethods.size() > 10) return COMPLEXITY_HIGH;
            if (webMethods.size() > 5) return COMPLEXITY_MEDIUM;
            return COMPLEXITY_LOW;
        }
    }
}
```

---

## Inspector 2: RestServiceInspector

### Location
`analyzer-ejb2spring/src/main/java/com/analyzer/rules/ejb2spring/RestServiceInspector.java`

### Annotations to Detect
**JAX-RS (javax.ws.rs.* and jakarta.ws.rs.*)**
- `@Path` - Defines resource URI path
- `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH` - HTTP methods
- `@Produces` - Response media types
- `@Consumes` - Request media types
- `@PathParam`, `@QueryParam` - Parameter bindings

### Tags to Produce
```java
// In EjbMigrationTags.java
public static final String REST_JAX_RS = "rest.jaxrs.detected";
public static final String REST_RESOURCE_ENDPOINT = "rest.resource.endpoint";
public static final String REST_HTTP_METHOD = "rest.http.method";
public static final String SPRING_REST_CONTROLLER_CONVERSION = "spring.conversion.restController";
```

### Implementation Pattern
```java
@InspectorDependencies(
    requires = {InspectorTags.TAG_JAVA_IS_SOURCE},
    produces = {
        EjbMigrationTags.REST_JAX_RS,
        EjbMigrationTags.REST_RESOURCE_ENDPOINT,
        EjbMigrationTags.SPRING_REST_CONTROLLER_CONVERSION
    }
)
public class RestServiceInspector extends AbstractJavaClassInspector {
    
    @Override
    protected void analyzeClass(
        ProjectFile projectFile,
        JavaClassNode classNode,
        TypeDeclaration<?> type,
        NodeDecorator<ProjectFile> projectFileDecorator
    ) {
        if (type instanceof ClassOrInterfaceDeclaration classDecl) {
            RestResourceDetector detector = new RestResourceDetector();
            classDecl.accept(detector, null);
            
            if (detector.isRestResource()) {
                RestResourceInfo info = detector.getRestResourceInfo();
                
                // Set tags
                projectFileDecorator.enableTag(REST_JAX_RS);
                classNode.enableTag(REST_JAX_RS);
                classNode.enableTag(REST_RESOURCE_ENDPOINT);
                classNode.enableTag(SPRING_REST_CONTROLLER_CONVERSION);
                
                // Store analysis
                classNode.setProperty("rest.analysis", info);
                
                // Complexity
                classNode.getMetrics().setMaxMetric(
                    METRIC_MIGRATION_COMPLEXITY,
                    info.calculateComplexity()
                );
            }
        }
    }
    
    private static class RestResourceDetector extends VoidVisitorAdapter<Void> {
        private RestResourceInfo info = new RestResourceInfo();
        private boolean isRestResource = false;
        
        private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS",
            "javax.ws.rs.GET", "javax.ws.rs.POST", "javax.ws.rs.PUT", 
            "javax.ws.rs.DELETE", "javax.ws.rs.PATCH",
            "jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT",
            "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH"
        );
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check for @Path on class
            for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                String name = annotation.getNameAsString();
                if ("Path".equals(name) ||
                    "javax.ws.rs.Path".equals(name) ||
                    "jakarta.ws.rs.Path".equals(name)) {
                    isRestResource = true;
                    info.className = classDecl.getNameAsString();
                    
                    // Extract path value
                    if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
                        info.basePath = singleMember.getMemberValue().toString()
                            .replaceAll("\"", "");
                    }
                }
            }
            
            // Check methods for HTTP method annotations
            for (MethodDeclaration method : classDecl.getMethods()) {
                RestEndpointInfo endpoint = new RestEndpointInfo();
                endpoint.methodName = method.getNameAsString();
                
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    String name = annotation.getNameAsString();
                    
                    // HTTP methods
                    if (HTTP_METHOD_ANNOTATIONS.contains(name)) {
                        isRestResource = true;
                        endpoint.httpMethod = name.toUpperCase()
                            .replace("JAVAX.WS.RS.", "")
                            .replace("JAKARTA.WS.RS.", "");
                    }
                    
                    // @Path on method
                    if (name.endsWith("Path")) {
                        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
                            endpoint.path = singleMember.getMemberValue().toString()
                                .replaceAll("\"", "");
                        }
                    }
                    
                    // @Produces
                    if (name.endsWith("Produces")) {
                        endpoint.produces = extractArrayValue(annotation);
                    }
                    
                    // @Consumes
                    if (name.endsWith("Consumes")) {
                        endpoint.consumes = extractArrayValue(annotation);
                    }
                }
                
                if (endpoint.httpMethod != null) {
                    info.endpoints.add(endpoint);
                }
            }
            
            super.visit(classDecl, arg);
        }
        
        private List<String> extractArrayValue(AnnotationExpr annotation) {
            // Extract array or single value from annotation
            List<String> result = new ArrayList<>();
            if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
                result.add(singleMember.getMemberValue().toString().replaceAll("\"", ""));
            }
            return result;
        }
        
        public boolean isRestResource() {
            return isRestResource;
        }
        
        public RestResourceInfo getRestResourceInfo() {
            return info;
        }
    }
    
    public static class RestResourceInfo {
        public String className;
        public String basePath;
        public List<RestEndpointInfo> endpoints = new ArrayList<>();
        
        public double calculateComplexity() {
            if (endpoints.size() > 15) return COMPLEXITY_HIGH;
            if (endpoints.size() > 8) return COMPLEXITY_MEDIUM;
            return COMPLEXITY_LOW;
        }
    }
    
    public static class RestEndpointInfo {
        public String methodName;
        public String httpMethod;
        public String path;
        public List<String> produces = new ArrayList<>();
        public List<String> consumes = new ArrayList<>();
    }
}
```

---

## Implementation Steps

### Step 1: Add Tags to EjbMigrationTags.java (15 min)
```java
// In EjbMigrationTags.java, add:

// ==================== WEB SERVICE TAGS ====================
/** Tag for JAX-WS Web Service components */
public static final String WEBSERVICE_JAX_WS = "webservice.jaxws.detected";

/** Tag for SOAP endpoint services */
public static final String WEBSERVICE_SOAP_ENDPOINT = "webservice.soap.endpoint";

/** Tag for web service operations */
public static final String WEBSERVICE_OPERATION = "webservice.operation";

// ==================== REST SERVICE TAGS ====================
/** Tag for JAX-RS REST resource components */
public static final String REST_JAX_RS = "rest.jaxrs.detected";

/** Tag for REST resource endpoints */
public static final String REST_RESOURCE_ENDPOINT = "rest.resource.endpoint";

/** Tag for HTTP method handlers */
public static final String REST_HTTP_METHOD = "rest.http.method";
```

### Step 2: Implement WebServiceInspector (1.5 hours)
1. Create `WebServiceInspector.java`
2. Implement JavaParser visitor pattern
3. Add WebServiceInfo data class
4. Write unit tests

### Step 3: Implement RestServiceInspector (1.5 hours)
1. Create `RestServiceInspector.java`
2. Implement JavaParser visitor pattern
3. Add RestResourceInfo data classes
4. Write unit tests

### Step 4: Register Inspectors (15 min)
Update `Ejb2SpringInspectorBeanFactory.java`:
```java
@Bean
public WebServiceInspector webServiceInspector(/* dependencies */) {
    return new WebServiceInspector(/* ... */);
}

@Bean
public RestServiceInspector restServiceInspector(/* dependencies */) {
    return new RestServiceInspector(/* ... */);
}
```

### Step 5: Update phase4-8-integration.yaml (30 min)
Replace grep commands with GRAPH_QUERY blocks:
```yaml
- type: "GRAPH_QUERY"
  name: "query-web-services"
  description: "Query for JAX-WS web services (tagged by WebServiceInspector)"
  query-type: "BY_TAGS"
  tags:
    - "webservice.jaxws.detected"
  output-variable: "web_services"

- type: "GRAPH_QUERY"
  name: "query-rest-services"
  description: "Query for JAX-RS REST services (tagged by RestServiceInspector)"
  query-type: "BY_TAGS"
  tags:
    - "rest.jaxrs.detected"
  output-variable: "rest_services"
```

### Step 6: Test & Validate (1 hour)
1. Test inspectors on sample code
2. Verify tags are applied correctly
3. Test GRAPH_QUERY blocks
4. Ensure Maven compilation succeeds

---

## Testing

### Sample JAX-WS Code
```java
@WebService
public class UserService {
    @WebMethod
    public User getUser(int id) {
        // ...
    }
}
```

### Sample JAX-RS Code
```java
@Path("/users")
public class UserResource {
    @GET
    @Produces("application/json")
    public List<User> getUsers() {
        // ...
    }
    
    @POST
    @Consumes("application/json")
    public void createUser(User user) {
        // ...
    }
}
```

### Expected Results
- Tags applied: `webservice.jaxws.detected`, `rest.jaxrs.detected`
- Properties set: `webservice.analysis`, `rest.analysis`
- Complexity metrics calculated
- GRAPH_QUERY returns correct nodes

---

## Success Criteria

- [ ] WebServiceInspector implemented and tested
- [ ] RestServiceInspector implemented and tested
- [ ] Tags added to EjbMigrationTags.java
- [ ] Inspectors registered in factory
- [ ] Unit tests pass
- [ ] phase4-8-integration.yaml updated
- [ ] Maven compilation succeeds
- [ ] GRAPH_QUERY blocks return expected results
- [ ] Documentation updated

---

## Next Task

After completing this task, proceed to implement antipattern inspectors:
- InheritanceDepthInspector
- SingletonPatternInspector
- UtilityClassInspector
- ExceptionAntipatternInspector

See: `implement-antipattern-inspectors.md`
