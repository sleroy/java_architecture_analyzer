package com.analyzer.resource;
import com.analyzer.core.inspector.InspectorDependencies;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composite ResourceResolver that delegates to specific resolvers based on URI
 * scheme.
 */
public class CompositeResourceResolver implements ResourceResolver {

    private final Map<String, ResourceResolver> resolvers = new ConcurrentHashMap<>();

    /**
     * Register a resolver for a specific URI scheme.
     */
    public void registerResolver(String scheme, ResourceResolver resolver) {
        resolvers.put(scheme.toLowerCase(), resolver);
    }

    /**
     * Remove a resolver for a scheme.
     */
    public void unregisterResolver(String scheme) {
        ResourceResolver removed = resolvers.remove(scheme.toLowerCase());
        if (removed != null) {
            try {
                removed.close();
            } catch (IOException e) {
                System.err.println("Warning: Failed to close resolver for scheme " + scheme + ": " + e.getMessage());
            }
        }
    }

    /**
     * Get the appropriate resolver for the given resource location.
     */
    private ResourceResolver getResolver(ResourceLocation resourceLocation) {
        String scheme = resourceLocation.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("Resource location has no scheme: " + resourceLocation);
        }

        ResourceResolver resolver = resolvers.get(scheme.toLowerCase());
        if (resolver == null) {
            throw new UnsupportedOperationException("No resolver registered for scheme: " + scheme);
        }

        return resolver;
    }

    @Override
    public InputStream openStream(ResourceLocation resourceLocation) throws IOException {
        return getResolver(resourceLocation).openStream(resourceLocation);
    }

    @Override
    public boolean exists(ResourceLocation resourceLocation) {
        try {
            return getResolver(resourceLocation).exists(resourceLocation);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    @Override
    public Collection<ResourceLocation> listChildren(ResourceLocation directoryLocation) throws IOException {
        return getResolver(directoryLocation).listChildren(directoryLocation);
    }

    @Override
    public ResourceMetadata getMetadata(ResourceLocation resourceLocation) throws IOException {
        return getResolver(resourceLocation).getMetadata(resourceLocation);
    }

    @Override
    public boolean supports(ResourceLocation resourceLocation) {
        String scheme = resourceLocation.getScheme();
        if (scheme == null) {
            return false;
        }

        ResourceResolver resolver = resolvers.get(scheme.toLowerCase());
        return resolver != null && resolver.supports(resourceLocation);
    }

    @Override
    public void close() throws IOException {
        List<IOException> exceptions = new ArrayList<>();

        for (ResourceResolver resolver : resolvers.values()) {
            try {
                resolver.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        resolvers.clear();

        // If there were any exceptions during close, throw the first one
        if (!exceptions.isEmpty()) {
            IOException first = exceptions.get(0);
            for (int i = 1; i < exceptions.size(); i++) {
                first.addSuppressed(exceptions.get(i));
            }
            throw first;
        }
    }

    /**
     * Get all registered schemes.
     */
    public Set<String> getRegisteredSchemes() {
        return new HashSet<>(resolvers.keySet());
    }

    /**
     * Create a default instance with common resolvers.
     */
    public static CompositeResourceResolver createDefault() {
        CompositeResourceResolver composite = new CompositeResourceResolver();

        // Register default resolvers
        composite.registerResolver("file", new FileResourceResolver());
        composite.registerResolver("jar", new JarResourceResolver());

        return composite;
    }
}
