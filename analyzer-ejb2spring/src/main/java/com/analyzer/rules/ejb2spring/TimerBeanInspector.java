package com.analyzer.rules.ejb2spring;

import com.analyzer.api.inspector.Inspector;
import com.analyzer.core.export.NodeDecorator;
import com.analyzer.api.graph.ClassNodeRepository;
import com.analyzer.api.graph.JavaClassNode;
import com.analyzer.api.inspector.InspectorDependencies;
import com.analyzer.core.inspector.InspectorTags;
import com.analyzer.core.model.ProjectFile;
import com.analyzer.dev.inspectors.source.AbstractJavaClassInspector;
import com.analyzer.api.resource.ResourceResolver;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inspector that detects Timer Bean patterns in Java source code
 * for EJB-to-Spring migration analysis.
 * 
 * <p>
 * The TimerBeanInspector (CS-040) identifies classes that use
 * javax.ejb.TimerService
 * for scheduled task execution. These are candidates for migration to Spring
 * Boot's
 * 
 * @Scheduled annotation mechanism.
 *            </p>
 * 
 *            <p>
 *            Timer Bean characteristics detected include:
 *            <ul>
 *            <li>Usage of javax.ejb.TimerService</li>
 *            <li>Timer creation methods (createTimer)</li>
 *            <li>Implementation of ejbTimeout method</li>
 *            <li>Timer callback interfaces</li>
 *            <li>Calendar-based timers (EJB 3.1+)</li>
 *            </ul>
 *            </p>
 */
@InspectorDependencies(requires = { InspectorTags.TAG_JAVA_IS_SOURCE, InspectorTags.TAG_APPLICATION_CLASS}, produces = {
        TimerBeanInspector.TAGS.TAG_IS_TIMER_BEAN,
        EjbMigrationTags.SPRING_COMPONENT_CONVERSION,
        EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM
})
public class TimerBeanInspector extends AbstractJavaClassInspector {

    @Inject
    public TimerBeanInspector(ResourceResolver resourceResolver, ClassNodeRepository classNodeRepository) {
        super(resourceResolver, classNodeRepository);
    }

    @Override
    public String getName() {
        return "Timer Bean Detector";
    }

    @Override
    protected void analyzeClass(ProjectFile projectFile, JavaClassNode classNode, TypeDeclaration<?> type,
                                NodeDecorator<ProjectFile> projectFileDecorator) {

        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return;
        }

        ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) type;
        String className = classDecl.getNameAsString();

        // Initial assessment based on class name
        boolean hasTimerName = className.contains("Timer") ||
                className.contains("Job") ||
                className.contains("Schedule") ||
                className.contains("Cron") ||
                className.endsWith("Task");

        // Detailed code analysis
        TimerDetector detector = new TimerDetector();
        classDecl.accept(detector, null);

        // Decision logic - a class is a timer bean if it uses TimerService or has timer
        // methods
        boolean isTimerBean = detector.usesTimerService() || detector.hasTimerMethods();

        if (isTimerBean) {
            TimerInfo info = detector.getTimerInfo();
            info.className = className;
            info.hasTimerName = hasTimerName;

            // Set tags according to the produces contract
            projectFileDecorator.setProperty(TAGS.TAG_IS_TIMER_BEAN, true);
            projectFileDecorator.setProperty(EjbMigrationTags.SPRING_COMPONENT_CONVERSION, true);
            projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, true);

            // Set property on class node for detailed analysis
            classNode.setProperty("timer.analysis", info.toString());

            // Set analysis statistics
            projectFileDecorator.setProperty("timer.timer_service_references", info.timerServiceReferences);
            projectFileDecorator.setProperty("timer.create_timer_calls", info.createTimerCalls.size());

            // Set Spring Boot migration target
            projectFileDecorator.setProperty("spring.conversion.target", "@Component+@Scheduled");

            // If calendar-based timers are used, migration is more complex
            if (info.usesCalendarBasedTimer) {
                projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_HIGH, true);
                // Override medium complexity
                projectFileDecorator.setProperty(EjbMigrationTags.MIGRATION_COMPLEXITY_MEDIUM, false);
            }
        }
    }

    /**
     * Visitor that detects Timer Bean characteristics by analyzing
     * methods and TimerService references.
     */
    private static class TimerDetector extends VoidVisitorAdapter<Void> {
        private final TimerInfo info = new TimerInfo();

        // Timer-related types
        private static final Set<String> TIMER_TYPES = Set.of(
                "TimerService", "javax.ejb.TimerService", "jakarta.ejb.TimerService",
                "Timer", "javax.ejb.Timer", "jakarta.ejb.Timer",
                "TimerHandle", "javax.ejb.TimerHandle", "jakarta.ejb.TimerHandle",
                "ScheduleExpression", "javax.ejb.ScheduleExpression", "jakarta.ejb.ScheduleExpression");

        // Timer-related method names
        private static final Set<String> TIMER_METHODS = Set.of(
                "createTimer", "getTimers", "createSingleActionTimer", "createIntervalTimer",
                "createCalendarTimer", "getTimerService", "getTimer", "cancel");

        // Timer callback methods
        private static final Set<String> TIMER_CALLBACKS = Set.of(
                "ejbTimeout", "timeout", "onTimeout");

        public boolean usesTimerService() {
            return info.timerServiceReferences > 0 || !info.createTimerCalls.isEmpty();
        }

        public boolean hasTimerMethods() {
            return !info.timerCallbacks.isEmpty();
        }

        public TimerInfo getTimerInfo() {
            return info;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Check implemented interfaces for timer-related interfaces
            classDecl.getImplementedTypes().forEach(implementedType -> {
                String typeName = implementedType.getNameAsString();
                if (typeName.contains("TimedObject") || typeName.contains("TimedObjectInvoker")) {
                    info.implementsTimerInterface = true;
                }
            });

            super.visit(classDecl, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);

            String methodName = method.getNameAsString();

            // Check for timer callback methods
            if (TIMER_CALLBACKS.contains(methodName)) {
                info.timerCallbacks.add(methodName);

                // Check if the method has a Timer parameter
                method.getParameters().forEach(param -> {
                    String paramType = param.getType().asString();
                    if (paramType.contains("Timer")) {
                        info.hasTimerParameter = true;
                    }
                });
            }

            // Check for @Schedule or @Schedules annotations
            method.getAnnotations().forEach(annotation -> {
                String annotName = annotation.getNameAsString();
                if (annotName.equals("Schedule") || annotName.equals("Schedules")) {
                    info.usesScheduleAnnotation = true;
                }
            });
        }

        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);

            String methodName = methodCall.getNameAsString();

            // Check for timer creation methods
            if (TIMER_METHODS.contains(methodName)) {
                info.timerMethodCalls.add(methodName);

                // Check specifically for createTimer calls
                if (methodName.contains("createTimer")) {
                    info.createTimerCalls.add(methodCall.toString());

                    // Check if calendar-based timer is used
                    if (methodName.equals("createCalendarTimer") ||
                            methodCall.toString().contains("ScheduleExpression")) {
                        info.usesCalendarBasedTimer = true;
                    }
                }
            }
        }

        @Override
        public void visit(NameExpr nameExpr, Void arg) {
            super.visit(nameExpr, arg);

            // Look for timer-related types
            String name = nameExpr.getNameAsString();
            if (TIMER_TYPES.contains(name)) {
                info.timerServiceReferences++;
                info.timerTypes.add(name);
            }
        }
    }

    /**
     * Data class to hold Timer Bean analysis information
     */
    public static class TimerInfo {
        public String className;
        public boolean hasTimerName = false;
        public boolean implementsTimerInterface = false;
        public boolean hasTimerParameter = false;
        public boolean usesScheduleAnnotation = false;
        public boolean usesCalendarBasedTimer = false;
        public int timerServiceReferences = 0;
        public List<String> timerCallbacks = new ArrayList<>();
        public List<String> timerMethodCalls = new ArrayList<>();
        public List<String> createTimerCalls = new ArrayList<>();
        public Set<String> timerTypes = new HashSet<>();

        @Override
        public String toString() {
            return String.format(
                    "TimerBean{class=%s, timerName=%b, timerInterface=%b, timerParam=%b, " +
                            "scheduleAnnotation=%b, calendarTimer=%b, serviceRefs=%d, " +
                            "callbacks=%d, timerCalls=%d, createCalls=%d}",
                    className,
                    hasTimerName,
                    implementsTimerInterface,
                    hasTimerParameter,
                    usesScheduleAnnotation,
                    usesCalendarBasedTimer,
                    timerServiceReferences,
                    timerCallbacks.size(),
                    timerMethodCalls.size(),
                    createTimerCalls.size());
        }
    }

    public static class TAGS {
        public static final String TAG_IS_TIMER_BEAN = "timer_bean_inspector.is_timer_bean";
    }
}
