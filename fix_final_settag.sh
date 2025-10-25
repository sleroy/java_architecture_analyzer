#!/bin/bash

echo "Fixing final setTag() calls..."

# Fix ConfigConstantsInspector.java
echo "Fixing ConfigConstantsInspector.java..."
find src/main/java/com/analyzer/rules/ejb2spring/ConfigConstantsInspector.java -type f -exec \
  sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([^)]*\))/projectFileDecorator.setProperty(\1, \2)/g' {} \;

# Fix FactoryBeanProviderInspector.java
echo "Fixing FactoryBeanProviderInspector.java..."
find src/main/java/com/analyzer/rules/ejb2spring/FactoryBeanProviderInspector.java -type f -exec \
  sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([^)]*\))/projectFileDecorator.setProperty(\1, \2)/g' {} \;

# Fix InterceptorAopInspector.java
echo "Fixing InterceptorAopInspector.java..."
find src/main/java/com/analyzer/rules/ejb2spring/InterceptorAopInspector.java -type f -exec \
  sed -i 's/projectFileDecorator\.setTag(\([^,]*\), *\([^)]*\))/projectFileDecorator.setProperty(\1, \2)/g' {} \;

echo "Done fixing final setTag() calls!"
