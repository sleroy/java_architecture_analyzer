package com.example.interceptor;

import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A sample interceptor class that logs method invocations.
 * This should be detected by InterceptorAopInspector.
 */
@Interceptor
public class LoggingInterceptor {

    private static final Logger LOGGER = Logger.getLogger(LoggingInterceptor.class.getName());

    /**
     * This method is invoked around method calls to intercepted beans.
     * It logs the method entry and exit along with parameters.
     */
    @AroundInvoke
    public Object logMethodCall(InvocationContext ctx) throws Exception {
        String className = ctx.getTarget().getClass().getName();
        String methodName = ctx.getMethod().getName();
        Object[] parameters = ctx.getParameters();

        // Log method entry
        LOGGER.info("Entering method: " + className + "." + methodName +
                " with parameters: " + Arrays.toString(parameters));

        try {
            // Invoke the actual method
            Object result = ctx.proceed();

            // Log method exit
            LOGGER.info("Exiting method: " + className + "." + methodName +
                    " with result: " + (result != null ? result.toString() : "null"));

            return result;
        } catch (Exception e) {
            // Log exception
            LOGGER.severe("Exception in method: " + className + "." + methodName +
                    " - " + e.getMessage());
            throw e;
        }
    }

    /**
     * Lifecycle callback - invoked after bean construction
     */
    @javax.annotation.PostConstruct
    public void onPostConstruct(InvocationContext ctx) throws Exception {
        LOGGER.info("Bean constructed: " + ctx.getTarget().getClass().getName());
        ctx.proceed();
    }

    /**
     * Lifecycle callback - invoked before bean destruction
     */
    @javax.annotation.PreDestroy
    public void onPreDestroy(InvocationContext ctx) throws Exception {
        LOGGER.info("Bean being destroyed: " + ctx.getTarget().getClass().getName());
        ctx.proceed();
    }
}
