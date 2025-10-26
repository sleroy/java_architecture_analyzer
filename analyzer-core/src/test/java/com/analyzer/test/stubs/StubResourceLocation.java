package com.analyzer.test.stubs;

import com.analyzer.core.resource.ResourceLocation;

import java.net.URI;

/**
 * Stub implementation of ResourceLocation for testing purposes.
 * Avoids Mockito dependency and provides simple, controllable behavior.
 */
public class StubResourceLocation extends ResourceLocation {
    
    private final String path;
    private final URI uri;
    
    public StubResourceLocation(String path) {
        super(URI.create("file://" + path));
        this.path = path;
        this.uri = URI.create("file://" + path);
    }
    
    public StubResourceLocation(URI uri) {
        super(uri);
        this.uri = uri;
        this.path = uri.getPath();
    }
    
    @Override
    public URI getUri() {
        return uri;
    }
    
    @Override
    public String toString() {
        return path;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        StubResourceLocation that = (StubResourceLocation) obj;
        return uri.equals(that.uri);
    }
    
    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
