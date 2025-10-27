package com.example;

public class TestClass {
    private int count = 0;
    
    public void method1() {
        count++;
    }
    
    public void method2() {
        if (count > 0) {
            count--;
        }
    }
    
    public int getCount() {
        return count;
    }
}
