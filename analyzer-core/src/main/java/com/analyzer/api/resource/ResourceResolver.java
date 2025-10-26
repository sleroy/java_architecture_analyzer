package com.analyzer.api.resource;

import com.analyzer.core.resource.ResourceLocation;
import com.analyzer.core.resource.ResourceMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Interface for resolving different types of resources to InputStreams.
 * Supports various URI schemes like file://, jar://, etc.
 */
public interface ResourceResolver {

    /**
     * Open an InputStream for the specified resource.
     * 
     * @param resourceLocation The resource location to open
     * @return InputStream for reading the resource content
     * @throws IOException if the resource cannot be opened
     */
    InputStream openStream(ResourceLocation resourceLocation) throws IOException;

    /**
     * Check if a resource exists at the specified location.
     * 
     * @param resourceLocation The resource location to check
     * @return true if the resource exists, false otherwise
     */
    boolean exists(ResourceLocation resourceLocation);

    /**
     * List child resources for directory-like resources.
     * 
     * @param directoryLocation The directory location to list
     * @return Collection of child resource locations
     * @throws IOException                   if listing fails
     * @throws UnsupportedOperationException if the resource type doesn't support
     *                                       listing
     */
    Collection<ResourceLocation> listChildren(ResourceLocation directoryLocation) throws IOException;

    /**
     * Get metadata for a resource.
     * 
     * @param resourceLocation The resource location
     * @return ResourceMetadata containing information about the resource
     * @throws IOException if metadata cannot be retrieved
     */
    ResourceMetadata getMetadata(ResourceLocation resourceLocation) throws IOException;

    /**
     * Check if this resolver supports the given resource location type.
     * 
     * @param resourceLocation The resource location to check
     * @return true if this resolver can handle the resource type
     */
    boolean supports(ResourceLocation resourceLocation);

    /**
     * Close any resources held by this resolver.
     * Should be called when the resolver is no longer needed.
     */
    default void close() throws IOException {
        // Default implementation does nothing
    }
}
