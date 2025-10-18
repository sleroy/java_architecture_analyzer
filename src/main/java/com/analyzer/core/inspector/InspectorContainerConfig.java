package com.analyzer.core.inspector;

import com.analyzer.core.resource.JARClassLoaderService;
import com.analyzer.resource.ResourceResolver;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.behaviors.Caching;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Configuration class for setting up PicoContainer dependency injection for inspectors.
 * This class manages the creation and configuration of the DI container used to instantiate
 * inspector instances with proper dependency injection.
 * 
 * <p>Features:</p>
 * <ul>
 *   <li>Automatic registration of core dependencies (ResourceResolver, JARClassLoaderService)</li>
 *   <li>Auto-discovery and registration of inspector classes from classpath</li>
 *   <li>Support for @Inject annotation-based constructor injection</li>
 *   <li>Singleton lifecycle management for core services</li>
 *   <li>Plugin inspector loading from JAR files</li>
 * </ul>
 */
public class InspectorContainerConfig {

    private static final Logger logger = LoggerFactory.getLogger(InspectorContainerConfig.class);

    private final ResourceResolver resourceResolver;
    private final File pluginsDirectory;
    private final List<String> scanPackages;
    private final List<String> excludePackages;

    /**
     * Creates a new container configuration.
     * 
     * @param resourceResolver the ResourceResolver instance to register in the container
     * @param pluginsDirectory optional directory containing plugin JAR files
     */
    public InspectorContainerConfig(ResourceResolver resourceResolver, File[ERROR] Failed to process response: Too many requests, please wait before trying again. You have sent too many requests.  Wait before trying again.
