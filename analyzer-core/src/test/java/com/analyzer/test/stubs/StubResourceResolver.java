package com.analyzer.test.stubs;

import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.resource.ResourceMetadata;
import com.analyzer.api.resource.ResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stub implementation of ResourceResolver for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 */
public class StubResourceResolver implements ResourceResolver {
    
    private final Map<ResourceLocation, String> fileContents = new HashMap<>();
    private final Map<ResourceLocation, byte[]> binaryContents = new HashMap<>();
    private final Map<ResourceLocation, Boolean> fileExists = new HashMap<>();
    private final Map<ResourceLocation, IOException> ioExceptions = new HashMap<>();
    
    public void setFileContent(ResourceLocation location, String content) {
        fileContents.put(location, content);
        fileExists.put(location, true);
    }
    
    public void setBinaryContent(ResourceLocation location, byte[] content) {
        binaryContents.put(location, content);
        fileExists.put(location, true);
    }
    
    public void setFileExists(ResourceLocation location, boolean exists) {
        fileExists.put(location, exists);
    }
    
    public void setIOException(ResourceLocation location, IOException exception) {
        ioExceptions.put(location, exception);
    }

    @Override
    public boolean exists(ResourceLocation location) {
        return fileExists.getOrDefault(location, false);
    }

    @Override
    public InputStream openStream(ResourceLocation location) throws IOException {
        if (ioExceptions.containsKey(location)) {
            throw ioExceptions.get(location);
        }
        
        // Check for binary content first
        if (binaryContents.containsKey(location)) {
            byte[] content = binaryContents.get(location);
            return new ByteArrayInputStream(content);
        }
        
        // Fall back to string content
        String content = fileContents.get(location);
        if (content == null) {
            throw new IOException("File not found: " + location);
        }
        
        return new ByteArrayInputStream(content.getBytes());
    }

    @Override
    public ResourceMetadata getMetadata(ResourceLocation location) throws IOException {
        // Simple stub implementation
        return ResourceMetadata.file(100L, java.time.Instant.now(), "text/plain");
    }

    @Override
    public Collection<ResourceLocation> listChildren(ResourceLocation location) throws IOException {
        // Simple stub implementation - return empty list
        return List.of();
    }
    
    @Override
    public boolean supports(ResourceLocation location) {
        // Simple stub - supports all locations
        return true;
    }
}
