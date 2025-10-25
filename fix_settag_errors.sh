#!/bin/bash

# Script to fix .setTag() calls to use NodeDecorator API
# .setTag() → .setProperty() or .enableTag()

echo "Fixing .setTag() calls in inspector files..."

# Fix MutableServiceInspector.java
echo "Fixing MutableServiceInspector.java..."
sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\(true\|false\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/MutableServiceInspector.java

sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/MutableServiceInspector.java

# Fix CacheSingletonInspector.java
echo "Fixing CacheSingletonInspector.java..."
sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\(true\|false\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/CacheSingletonInspector.java

sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/CacheSingletonInspector.java

# Fix ServletInspector.java
echo "Fixing ServletInspector.java..."
sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\(true\|false\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java

sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java

sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\("[^"]*"\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServletInspector.java

# Fix ServiceLocatorInspector.java
echo "Fixing ServiceLocatorInspector.java..."
sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\(true\|false\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/ejb2spring/ServiceLocatorInspector.java

# Fix CyclomaticComplexityInspector.java
echo "Fixing CyclomaticComplexityInspector.java..."
sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([0-9]\+\))/projectFileDecorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/metrics/CyclomaticComplexityInspector.java

# Fix inner class setTag calls (TypeInspectorASMInspector, MethodCountInspector)
echo "Fixing TypeInspectorASMInspector.java inner class..."
sed -i 's/this\.setTag(\([^,]*\), *\("[^"]*"\))/this.decorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/std/TypeInspectorASMInspector.java

echo "Fixing MethodCountInspector.java inner class..."
sed -i 's/this\.setTag(\([^,]*\), *\([0-9]\+\))/this.decorator.setProperty(\1, \2)/g' \
  src/main/java/com/analyzer/rules/metrics/MethodCountInspector.java

echo "Done fixing .setTag() calls!"
