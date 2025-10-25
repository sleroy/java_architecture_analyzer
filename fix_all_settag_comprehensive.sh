#!/bin/bash

echo "Comprehensive fix for ALL remaining setTag() calls..."

# Find all Java files with setTag and fix them
find src/main/java -name "*.java" -type f -exec grep -l "setTag(" {} \; | while read file; do
    echo "Fixing setTag in: $file"
    
    # Replace all variations of setTag with setProperty
    # setTag(String, boolean) -> setProperty(String, boolean)
    sed -i 's/\.setTag(\([^,)]*\), *true)/\.setProperty(\1, true)/g' "$file"
    sed -i 's/\.setTag(\([^,)]*\), *false)/\.setProperty(\1, false)/g' "$file"
    
    # setTag(String, int) -> setProperty(String, int)
    sed -i 's/\.setTag(\([^,)]*\), *\([0-9][0-9]*\))/\.setProperty(\1, \2)/g' "$file"
    
    # setTag(String, variable) -> setProperty(String, variable) for variables
    sed -i 's/\.setTag(\([^,)]*\), *\([a-zA-Z][a-zA-Z0-9]*\))/\.setProperty(\1, \2)/g' "$file"
    
    # setTag(String, String) -> setProperty(String, String)
    sed -i 's/\.setTag(\([^,)]*\), *\("[^"]*"\))/\.setProperty(\1, \2)/g' "$file"
    
    # Handle this.setTag patterns (in inner classes)
    sed -i 's/this\.setTag(\([^,)]*\), *true)/this.decorator.setProperty(\1, true)/g' "$file"
    sed -i 's/this\.setTag(\([^,)]*\), *false)/this.decorator.setProperty(\1, false)/g' "$file"
    sed -i 's/this\.setTag(\([^,)]*\), *\([0-9][0-9]*\))/this.decorator.setProperty(\1, \2)/g' "$file"
    sed -i 's/this\.setTag(\([^,)]*\), *\([a-zA-Z][a-zA-Z0-9]*\))/this.decorator.setProperty(\1, \2)/g' "$file"
    sed -i 's/this\.setTag(\([^,)]*\), *\("[^"]*"\))/this.decorator.setProperty(\1, \2)/g' "$file"
done

echo ""
echo "=== Complete! ==="
echo "Fixed all setTag() calls across the codebase."
