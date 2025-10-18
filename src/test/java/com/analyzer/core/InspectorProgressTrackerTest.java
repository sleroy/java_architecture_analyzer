package com.analyzer.core;

import com.analyzer.core.inspector.InspectorProgressTracker;
import com.analyzer.core.model.ProjectFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for InspectorProgressTracker.
 * Tests first-time trigger detection, processing order, progress tracking,
 * and thread-safety aspects.
 */
class InspectorProgressTrackerTest {

    private InspectorProgressTracker tracker;
    private ProjectFile mockProjectFile1;
    private ProjectFile mockProjectFile2;

    @BeforeEach
    void setUp() {
        tracker = new InspectorProgressTracker();
        
        // Create mock ProjectFile objects
        mockProjectFile1 = Mockito.mock(ProjectFile.class);
        when(mockProjectFile1.getRelativePath()).thenReturn("com/example/TestClass1.java");
        
        mockProjectFile2 = Mockito.mock(ProjectFile.class);
        when(mockProjectFile2.getRelativePath()).thenReturn("com/example/TestClass2.java");
    }

    @Test
    @DisplayName("Should detect first-time inspector trigger")
    void shouldDetectFirstTimeTrigger() {
        // Given
        String inspectorName = "TestInspector";
        
        // When
        tracker.recordInspectorTrigger(inspectorName, mockProjectFile1);
        
        // Then
        assertTrue(tracker.hasBeenTriggered(inspectorName));
        Optional<InspectorProgressTracker.InspectorTriggerInfo> triggerInfo = 
            tracker.getFirstTriggerInfo(inspectorName);
        
        assertTrue(triggerInfo.isPresent());
        assertEquals(inspectorName, triggerInfo.get().getInspectorName());
        assertEquals(1, triggerInfo.get().getProcessingOrder());
        assertEquals("com/example/TestClass1.java", triggerInfo.get().getFirstProjectFilePath());
        assertNotNull(triggerInfo.get().getFirstTriggerTime());
    }

    @Test
    @DisplayName("Should track processing order for multiple inspectors")
    void shouldTrackProcessingOrder() {
        // Given
        String inspector1 = "Inspector1";
        String inspector2 = "Inspector2";
        String inspector3 = "Inspector3";
        
        // When - trigger inspectors in specific order
        tracker.recordInspectorTrigger(inspector1, mockProjectFile1);
        tracker.recordInspectorTrigger(inspector2, mockProjectFile1);
        tracker.recordInspectorTrigger(inspector3, mockProjectFile1);
        
        // Then
        assertEquals(1, tracker.getProcessingOrder(inspector1));
        assertEquals(2, tracker.getProcessingOrder(inspector2));
        assertEquals(3, tracker.getProcessingOrder(inspector3));
        
        List<String> orderedInspectors = tracker.getTriggeredInspectorsInOrder();
        assertEquals(List.of(inspector1, inspector2, inspector3), orderedInspectors);
    }

    @Test
    @DisplayName("Should not change processing order on subsequent triggers")
    void shouldNotChangeProcessingOrderOnSubsequentTriggers() {
        // Given
        String inspector = "TestInspector";
        
        // When - trigger same inspector multiple times
        tracker.recordInspectorTrigger(inspector, mockProjectFile1);
        tracker.recordInspectorTrigger(inspector, mockProjectFile2);
        tracker.recordInspectorTrigger(inspector, "com/example/TestClass3.java");
        
        // Then - processing order should remain 1
        assertEquals(1, tracker.getProcessingOrder(inspector));
        
        // But files processed count should increase
        assertEquals(3, tracker.getFilesProcessedCount(inspector));
        
        Set<String> processedFiles = tracker.getFilesProcessed(inspector);
        assertEquals(3, processedFiles.size());
        assertTrue(processedFiles.contains("com/example/TestClass1.java"));
        assertTrue(processedFiles.contains("com/example/TestClass2.java"));
        assertTrue(processedFiles.contains("com/example/TestClass3.java"));
    }

    @Test
    @DisplayName("Should track files processed per inspector")
    void shouldTrackFilesProcessedPerInspector() {
        // Given
        String inspector1 = "Inspector1";
        String inspector2 = "Inspector2";
        
        // When
        tracker.recordInspectorTrigger(inspector1, mockProjectFile1);
        tracker.recordInspectorTrigger(inspector1, mockProjectFile2);
        tracker.recordInspectorTrigger(inspector2, mockProjectFile1);
        
        // Then
        assertEquals(2, tracker.getFilesProcessedCount(inspector1));
        assertEquals(1, tracker.getFilesProcessedCount(inspector2));
        assertEquals(0, tracker.getFilesProcessedCount("NonExistentInspector"));
        
        Set<String> inspector1Files = tracker.getFilesProcessed(inspector1);
        assertEquals(2, inspector1Files.size());
        assertTrue(inspector1Files.contains("com/example/TestClass1.java"));
        assertTrue(inspector1Files.contains("com/example/TestClass2.java"));
        
        Set<String> inspector2Files = tracker.getFilesProcessed(inspector2);
        assertEquals(1, inspector2Files.size());
        assertTrue(inspector2Files.contains("com/example/TestClass1.java"));
    }

    @Test
    @DisplayName("Should handle string-based file path recording")
    void shouldHandleStringBasedFilePaths() {
        // Given
        String inspector = "TestInspector";
        String filePath = "com/example/StringPath.java";
        
        // When
        tracker.recordInspectorTrigger(inspector, filePath);
        
        // Then
        assertTrue(tracker.hasBeenTriggered(inspector));
        assertEquals(1, tracker.getFilesProcessedCount(inspector));
        assertTrue(tracker.getFilesProcessed(inspector).contains(filePath));
        
        Optional<InspectorProgressTracker.InspectorTriggerInfo> triggerInfo = 
            tracker.getFirstTriggerInfo(inspector);
        assertTrue(triggerInfo.isPresent());
        assertEquals(filePath, triggerInfo.get().getFirstProjectFilePath());
    }

    @Test
    @DisplayName("Should return empty optional for non-existent inspector")
    void shouldReturnEmptyOptionalForNonExistentInspector() {
        // When/Then
        assertFalse(tracker.hasBeenTriggered("NonExistentInspector"));
        assertTrue(tracker.getFirstTriggerInfo("NonExistentInspector").isEmpty());
        assertEquals(-1, tracker.getProcessingOrder("NonExistentInspector"));
        assertTrue(tracker.getFilesProcessed("NonExistentInspector").isEmpty());
    }

    @Test
    @DisplayName("Should provide progress summary")
    void shouldProvideProgressSummary() {
        // Given
        tracker.recordInspectorTrigger("Inspector1", mockProjectFile1);
        tracker.recordInspectorTrigger("Inspector1", mockProjectFile2);
        tracker.recordInspectorTrigger("Inspector2", mockProjectFile1);
        
        // When
        InspectorProgressTracker.ProgressSummary summary = tracker.getProgressSummary();
        
        // Then
        assertEquals(2, summary.getTriggeredInspectorsCount());
        assertEquals(3, summary.getTotalExecutions());
        assertEquals(2, summary.getInspectorsWithFiles());
        assertNotNull(summary.getTrackingStartTime());
        assertNull(summary.getTrackingCompletedTime());
        assertEquals("ongoing", summary.getTrackingDuration());
    }

    @Test
    @DisplayName("Should handle tracking completion")
    void shouldHandleTrackingCompletion() {
        // Given
        tracker.recordInspectorTrigger("Inspector1", mockProjectFile1);
        
        // When
        tracker.markTrackingCompleted();
        
        // Then
        InspectorProgressTracker.ProgressSummary summary = tracker.getProgressSummary();
        assertNotNull(summary.getTrackingCompletedTime());
        assertNotEquals("ongoing", summary.getTrackingDuration());
        assertTrue(summary.getTrackingDuration().endsWith("s"));
    }

    @Test
    @DisplayName("Should reset all tracking data")
    void shouldResetAllTrackingData() {
        // Given - populate tracker with data
        tracker.recordInspectorTrigger("Inspector1", mockProjectFile1);
        tracker.recordInspectorTrigger("Inspector2", mockProjectFile2);
        tracker.markTrackingCompleted();
        
        // Verify data exists
        assertTrue(tracker.hasBeenTriggered("Inspector1"));
        assertEquals(2, tracker.getProgressSummary().getTriggeredInspectorsCount());
        
        // When
        tracker.reset();
        
        // Then
        assertFalse(tracker.hasBeenTriggered("Inspector1"));
        assertFalse(tracker.hasBeenTriggered("Inspector2"));
        assertEquals(0, tracker.getFilesProcessedCount("Inspector1"));
        assertEquals(-1, tracker.getProcessingOrder("Inspector1"));
        assertTrue(tracker.getTriggeredInspectorsInOrder().isEmpty());
        
        InspectorProgressTracker.ProgressSummary summary = tracker.getProgressSummary();
        assertEquals(0, summary.getTriggeredInspectorsCount());
        assertEquals(0, summary.getTotalExecutions());
        assertNull(summary.getTrackingCompletedTime());
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent inspector triggers")
    void shouldBeThreadSafeForConcurrentTriggers() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int triggersPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When - multiple threads trigger inspectors concurrently
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < triggersPerThread; j++) {
                        String inspectorName = "Inspector" + (threadId % 3); // Use 3 different inspectors
                        String filePath = "file" + threadId + "_" + j + ".java";
                        tracker.recordInspectorTrigger(inspectorName, filePath);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Then
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent execution");
        
        // Verify that exactly 3 inspectors were triggered (Inspector0, Inspector1, Inspector2)
        assertEquals(3, tracker.getProgressSummary().getTriggeredInspectorsCount());
        
        // Verify total executions
        assertEquals(numberOfThreads * triggersPerThread, tracker.getProgressSummary().getTotalExecutions());
        
        // Verify processing order is consistent (each inspector should have a unique order)
        List<String> orderedInspectors = tracker.getTriggeredInspectorsInOrder();
        assertEquals(3, orderedInspectors.size());
        
        // Verify each inspector has correct processing order
        for (String inspector : orderedInspectors) {
            int order = tracker.getProcessingOrder(inspector);
            assertTrue(order >= 1 && order <= 3, "Processing order should be between 1 and 3");
        }
    }

    @Test
    @DisplayName("Should return all first triggers in processing order")
    void shouldReturnAllFirstTriggersInProcessingOrder() {
        // Given
        String inspector1 = "Inspector1";
        String inspector2 = "Inspector2";
        String inspector3 = "Inspector3";
        
        // When - trigger in specific order
        tracker.recordInspectorTrigger(inspector2, "file2.java");
        tracker.recordInspectorTrigger(inspector1, "file1.java");
        tracker.recordInspectorTrigger(inspector3, "file3.java");
        
        // Then
        List<InspectorProgressTracker.InspectorTriggerInfo> allTriggers = tracker.getAllFirstTriggers();
        assertEquals(3, allTriggers.size());
        
        // Should be ordered by processing order, not by trigger sequence
        assertEquals(inspector2, allTriggers.get(0).getInspectorName());
        assertEquals(1, allTriggers.get(0).getProcessingOrder());
        
        assertEquals(inspector1, allTriggers.get(1).getInspectorName());
        assertEquals(2, allTriggers.get(1).getProcessingOrder());
        
        assertEquals(inspector3, allTriggers.get(2).getInspectorName());
        assertEquals(3, allTriggers.get(2).getProcessingOrder());
    }

    @Test
    @DisplayName("Should handle duplicate file processing for same inspector")
    void shouldHandleDuplicateFileProcessing() {
        // Given
        String inspector = "TestInspector";
        String filePath = "com/example/TestClass.java";
        
        // When - process same file multiple times
        tracker.recordInspectorTrigger(inspector, filePath);
        tracker.recordInspectorTrigger(inspector, filePath);
        tracker.recordInspectorTrigger(inspector, filePath);
        
        // Then - file should only be counted once in the set, but executions should be counted
        assertEquals(1, tracker.getFilesProcessed(inspector).size());
        assertEquals(3, tracker.getFilesProcessedCount(inspector)); // Executions are counted separately
        assertEquals(3, tracker.getProgressSummary().getTotalExecutions());
    }

    @Test
    @DisplayName("InspectorTriggerInfo should have correct toString representation")
    void inspectorTriggerInfoShouldHaveCorrectToString() {
        // Given
        tracker.recordInspectorTrigger("TestInspector", "test.java");
        
        // When
        Optional<InspectorProgressTracker.InspectorTriggerInfo> triggerInfo = 
            tracker.getFirstTriggerInfo("TestInspector");
        
        // Then
        assertTrue(triggerInfo.isPresent());
        String toString = triggerInfo.get().toString();
        assertTrue(toString.contains("TestInspector"));
        assertTrue(toString.contains("order=1"));
        assertTrue(toString.contains("test.java"));
        assertTrue(toString.contains("InspectorTriggerInfo{"));
    }

    @Test
    @DisplayName("ProgressSummary should have correct toString representation")
    void progressSummaryShouldHaveCorrectToString() {
        // Given
        tracker.recordInspectorTrigger("Inspector1", "file1.java");
        tracker.recordInspectorTrigger("Inspector2", "file2.java");
        
        // When
        InspectorProgressTracker.ProgressSummary summary = tracker.getProgressSummary();
        String toString = summary.toString();
        
        // Then
        assertTrue(toString.contains("triggered=2"));
        assertTrue(toString.contains("executions=2"));
        assertTrue(toString.contains("duration=ongoing"));
        assertTrue(toString.contains("ProgressSummary{"));
    }

    @Test
    @DisplayName("Should not modify returned file sets")
    void shouldNotModifyReturnedFileSets() {
        // Given
        String inspector = "TestInspector";
        tracker.recordInspectorTrigger(inspector, "file1.java");
        
        // When
        Set<String> files = tracker.getFilesProcessed(inspector);
        
        // Then - should throw exception when trying to modify
        assertThrows(UnsupportedOperationException.class, () -> {
            files.add("file2.java");
        });
    }
}