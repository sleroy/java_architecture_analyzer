#!/bin/bash

echo "Fixing all remaining compilation errors..."

# Fix remaining setTag calls that were missed - these are integer setProperty calls
echo "Fixing integer setProperty calls..."

# MutableServiceInspector - lines 115, 116
sed -i 's/projectFileDecorator\.setTag(\("mutable\.field_count"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/MutableServiceInspector.java
sed -i 's/projectFileDecorator\.setTag(\("mutable\.field_count"\), *\(fieldCount\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/MutableServiceInspector.java

# CacheSingletonInspector - lines 99, 100
sed -i 's/projectFileDecorator\.setTag(\("cache\.field_count"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/CacheSingletonInspector.java
sed -i 's/projectFileDecorator\.setTag(\("cache\.field_count"\), *\(fieldCount\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/CacheSingletonInspector.java

# CyclomaticComplexityInspector - line 72
sed -i 's/projectFileDecorator\.setTag(\("complexity\.cyclomatic"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/metrics/CyclomaticComplexityInspector.java
sed -i 's/projectFileDecorator\.setTag(\("complexity\.cyclomatic"\), *\(complexity\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/metrics/CyclomaticComplexityInspector.java

# ServletInspector - line 75
sed -i 's/projectFileDecorator\.setTag(\("servlet\.lifecycle_method_count"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java
sed -i 's/projectFileDecorator\.setTag(\("servlet\.lifecycle_method_count"\), *\(lifecycleMethodCount\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java

# ServiceLocatorInspector - lines 78, 79
sed -i 's/projectFileDecorator\.setTag(\("service_locator\.lookup_count"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java
sed -i 's/projectFileDecorator\.setTag(\("service_locator\.lookup_count"\), *\(lookupCount\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java
sed -i 's/projectFileDecorator\.setTag(\("service_locator\.cache_field_count"\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java
sed -i 's/projectFileDecorator\.setTag(\("service_locator\.cache_field_count"\), *\(cacheFieldCount\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java

echo "Done fixing remaining errors!"
