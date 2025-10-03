package com.analyzer.test.stubs;

import com.analyzer.core.Clazz;
import com.analyzer.resource.ResourceLocation;

/**
 * Stub implementation of Clazz for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 */
public class StubClazz extends Clazz {
    
    private ResourceLocation sourceLocation;
    private ResourceLocation binaryLocation;
    
    public StubClazz(String className) {
        super(className, "com.test", ClassType.SOURCE_ONLY, null, null);
    }
    
    public StubClazz(String className, String packageName, ClassType classType, 
                     ResourceLocation sourceLocation, ResourceLocation binaryLocation) {
        super(className, packageName, classType, sourceLocation, binaryLocation);
        this.sourceLocation = sourceLocation;
        this.binaryLocation = binaryLocation;
    }
    
    public void setHasSourceCode(boolean hasSourceCode) {
        // For backward compatibility with tests, but actual hasSourceCode depends on sourceLocation
    }
    
    public void setSourceLocation(ResourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }
    
    public void setBinaryLocation(ResourceLocation binaryLocation) {
        this.binaryLocation = binaryLocation;
    }
    
    @Override
    public ResourceLocation getSourceLocation() {
        return sourceLocation != null ? sourceLocation : super.getSourceLocation();
    }
    
    @Override
    public ResourceLocation getBinaryLocation() {
        return binaryLocation != null ? binaryLocation : super.getBinaryLocation();
    }
    
    @Override
    public boolean hasSourceCode() {
        return getSourceLocation() != null;
    }
    
    @Override
    public boolean hasBinaryCode() {
        return getBinaryLocation() != null;
    }
}
