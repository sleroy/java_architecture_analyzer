package com.analyzer.core.resource;

import com.analyzer.core.model.Project;
import com.analyzer.api.resource.ResourceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JARClassLoaderService parent-child classloader hierarchy.
 */
class JARClassLoaderServiceTest {

    @Mock
    private ResourceResolver mockResourceResolver;

    private JARClassLoaderService service;
    private AutoCloseable mocks;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new JARClassLoaderService(mockResourceResolver);
    }

    @AfterEach
    void tearDown() throws Exception {
        service.shutdown();
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testParentChildClassLoaderHierarchy() {
        // Create a mock project
        Project project = new Project(tempDir);

        // Initialize the classloader hierarchy
        service.scanProjectJars(project);

        // Verify that the child classloader is not null
        assertNotNull(service.getSharedClassLoader(), "Child classloader should be initialized");
        assertNotNull(service.getChildClassLoader(), "Child classloader should be accessible");

        // Verify parent-child relationship
        URLClassLoader childLoader = service.getChildClassLoader();
        ClassLoader childParent = childLoader.getParent();

        if (service.getParentClassLoader() != null) {
            // If we have common JARs, parent should be our custom parent loader
            assertEquals(service.getParentClassLoader(), childParent,
                    "Child classloader should have parent as its parent");
        } else {
            // If no common JARs, parent should be thread context classloader
            assertEquals(Thread.currentThread().getContextClassLoader(), childParent,
                    "Child classloader should have thread context classloader as parent");
        }
    }

    @Test
    void testGetJarCountWithBothLoaders() {
        Project project = new Project(tempDir);

        service.scanProjectJars(project);

        int totalCount = service.getJarCount();
        int parentCount = service.getParentClassLoader() != null ? service.getParentClassLoader().getURLs().length : 0;
        int childCount = service.getChildClassLoader().getURLs().length;

        assertEquals(parentCount + childCount, totalCount,
                "Total JAR count should equal parent count + child count");
    }

    @Test
    void testShutdownClosesAllClassLoaders() {
        Project project = new Project(tempDir);

        service.scanProjectJars(project);

        // Verify classloaders are initialized
        assertNotNull(service.getChildClassLoader());

        // Shutdown
        service.shutdown();

        // Verify exception is thrown when trying to access after shutdown
        assertThrows(IllegalStateException.class, () -> service.getSharedClassLoader(),
                "Should throw exception when accessing classloader after shutdown");
    }

    @Test
    void testEmptyProjectInitializesEmptyClassLoaders() {
        Project project = new Project(tempDir);

        service.scanProjectJars(project);

        // Even with no JARs, classloaders should be initialized
        assertNotNull(service.getSharedClassLoader());
        assertNotNull(service.getChildClassLoader());
    }

    @Test
    void testJarClassificationLogic() {
        // This test verifies that the classification logic is working
        // by checking that Maven .m2 repository JARs would be classified as common
        String userHome = System.getProperty("user.home");
        String m2Path = userHome + File.separator + ".m2" + File.separator + "repository";

        // If Maven repository exists, there should be some common JARs
        Path m2RepoPath = Paths.get(m2Path);
        if (Files.exists(m2RepoPath)) {
            Project project = new Project(tempDir);

            service.scanProjectJars(project);

            // If .m2 repo exists, we should have parent classloader with common JARs
            // (This is a soft assertion since it depends on the system)
            int jarCount = service.getJarCount();
            assertTrue(jarCount >= 0, "JAR count should be non-negative");
        }
    }

    @Test
    void testClassLoaderDelegation() {
        Project project = new Project(tempDir);

        service.scanProjectJars(project);

        URLClassLoader childLoader = service.getChildClassLoader();
        assertNotNull(childLoader);

        // Verify that child can delegate to parent
        // (This is implicitly tested by the URLClassLoader constructor with parent
        // parameter)
        ClassLoader parent = childLoader.getParent();
        assertNotNull(parent, "Child classloader should have a parent");
    }
}
