package com.example;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Simple test class for analyzer testing
 */
@Deprecated
@SuppressWarnings("unused")
public class TestClass {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomAnnotation {
        String value() default "";
    }

    @CustomAnnotation("field annotation")
    private String name;

    @CustomAnnotation("constructor annotation")
    public TestClass(@CustomAnnotation("parameter annotation") String name) {
        this.name = name;
    }

    @Override
    @CustomAnnotation("method annotation")
    public String toString() {
        return "TestClass{name='" + name + "'}";
    }

    @CustomAnnotation("getter annotation")
    public String getName() {
        return name;
    }

    @Deprecated
    @CustomAnnotation("setter annotation")
    public void setName(@CustomAnnotation("setter parameter") String name) {
        this.name = name;
    }

    @CustomAnnotation("action method")
    public void doSomething() {
        System.out.println("Doing something with: " + name);
    }
}
