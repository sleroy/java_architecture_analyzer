package com.example.ejbapp.jdbc.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Customer Entity - Simple JavaBean
 * 
 * Plain Old Java Object (POJO) representing a Customer.
 * Follows JavaBean conventions from the EJB 2.0 era:
 * - Implements Serializable
 * - Private fields with public getters/setters
 * - No-arg constructor
 * - No business logic (anemic domain model)
 * 
 * @author EJB 2.0 Era Developer
 */
public class Customer implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer id;
    private String name;
    private String email;
    private String phone;
    private Timestamp createdAt;
    
    /**
     * Default constructor (required for JavaBeans)
     */
    public Customer() {
    }
    
    /**
     * Constructor with all fields except ID and createdAt
     */
    public Customer(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
    
    /**
     * Constructor with all fields
     */
    public Customer(Integer id, String name, String email, String phone, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    // toString for debugging (common practice)
    @Override
    public String toString() {
        return "Customer{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
