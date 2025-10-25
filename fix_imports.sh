#!/bin/bash

# Script to add missing AutoRegister imports

files=(
  "src/main/java/com/analyzer/rules/graph/BinaryJavaClassNodeInspector.java"
  "src/main/java/com/analyzer/rules/graph/JavaImportGraphInspector.java"
  "src/main/java/com/analyzer/rules/metrics/InheritanceDepthInspector.java"
  "src/main/java/com/analyzer/rules/metrics/AnnotationCountInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/CmpFieldMappingJavaBinaryInspector.java"
  "src/main/java/com/analyzer/rules/graph/SourceJavaClassNodeInspector.java"
  "src/main/java/com/analyzer/rules/metrics/MethodCountInspector.java"
  "src/main/java/com/analyzer/rules/metrics/ClocInspector.java"
  "src/main/java/com/analyzer/rules/metrics/InterfaceNumberInspector.java"
  "src/main/java/com/analyzer/rules/metrics/TypeUsageInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbDeploymentDescriptorInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbClassLoaderInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java"
  "src/main/java/com/analyzer/rules/metrics/ThreadLocalUsageInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/JndiLookupInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/MutableServiceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/ConfigurationConstantsInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/SessionBeanJavaSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/IdentifyServletSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/JBossEjbConfigurationInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbRemoteInterfaceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/MessageDrivenBeanInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/TransactionScriptInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/FactoryBeanProviderInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/JdbcDataAccessPatternInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/CacheSingletonInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/SecurityFacadeInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/StatefulSessionStateInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/DaoRepositoryInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/FormBeanDtoInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbBinaryClassInspector.java"
  "src/main/java/com/analyzer/rules/metrics/CyclomaticComplexityInspector.java"
  "src/main/java/com/analyzer/rules/std/TypeInspectorASMInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/CustomDataTransferPatternJavaSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EntityBeanJavaSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/TimerBeanInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/ComplexCmpRelationshipJavaSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbCreateMethodUsageInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/InterceptorAopInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/UtilityHelperInspector.java"
  "src/main/java/com/analyzer/rules/std/BinaryClassFQNInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/ProgrammaticTransactionUsageInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/EjbHomeInterfaceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/BusinessDelegatePatternJavaSourceInspector.java"
  "src/main/java/com/analyzer/rules/ejb2spring/DatabaseResourceManagementInspector.java"
  "src/main/java/com/analyzer/core/collector/BinaryJavaClassNodeCollector.java"
)

echo "Adding missing AutoRegister imports to ${#files[@]} files..."

for file in "${files[@]}"; do
  if [ -f "$file" ]; then
    # Check if import is missing
    if ! grep -q "import com.analyzer.core.inspector.AutoRegister;" "$file"; then
      # Find last import line and add our import after it
      awk '/^import/ {last_import = NR} 
           {lines[NR] = $0} 
           END {
             for (i = 1; i <= NR; i++) {
               print lines[i];
               if (i == last_import) {
                 print "import com.analyzer.core.inspector.AutoRegister;"
               }
             }
           }' "$file" > "$file.tmp" && mv "$file.tmp" "$file"
      echo "  [OK] $file"
    else
      echo "  [SKIP] $file (import already present)"
    fi
  else
    echo "  [WARN] $file not found"
  fi
done

echo "Done! Fixed imports in ${#files[@]} files."
echo "Run 'mvn clean compile -DskipTests' to verify."
