package com.example.factory;

/**
 * A sample factory class that creates Product instances.
 * This should be detected by FactoryBeanProviderInspector.
 */
public class ProductFactory {

    // Private constructor to prevent instantiation
    private ProductFactory() {
    }

    // Static factory method
    public static Product createProduct(String name, double price) {
        return new Product(name, price);
    }

    // Get a default product
    public static Product getDefaultProduct() {
        return new Product("Default", 9.99);
    }

    // Get product by ID (simulated)
    public static Product getProductById(int id) {
        return new Product("Product " + id, id * 10.0);
    }

    // Static inner class
    public static class Product {
        private final String name;
        private final double price;

        public Product(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }
    }
}
