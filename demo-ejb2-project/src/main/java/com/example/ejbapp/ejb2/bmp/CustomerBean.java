package com.example.ejbapp.ejb2.bmp;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Bean Managed Persistence (BMP) Entity Bean for Customer
 * Demonstrates legacy EJB 2.0 BMP pattern with manual JDBC handling
 * 
 * @ejb.bean name="Customer"
 *           type="Entity"
 *           jndi-name="ejb/Customer"
 *           local-jndi-name="ejb/CustomerLocal"
 *           primkey-field="customerId"
 *           
 * @ejb.persistence table-name="CUSTOMERS"
 * @ejb.finder signature="java.util.Collection findAll()"
 *             query="SELECT customer_id FROM customers"
 */
public class CustomerBean implements EntityBean {
    
    private EntityContext context;
    
    // Entity fields
    private Integer customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String city;
    private String country;
    private java.sql.Date registrationDate;
    private boolean active;
    
    // Legacy pattern: Direct JNDI lookup in bean (antipattern)
    private static final String DS_JNDI = "java:jboss/datasources/ExampleDS";
    
    /**
     * ejbCreate method - BMP requires manual INSERT
     */
    public Integer ejbCreate(String firstName, String lastName, String email) 
            throws CreateException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            
            // Legacy antipattern: Manually generating primary keys
            pstmt = conn.prepareStatement(
                "SELECT MAX(customer_id) FROM customers");
            rs = pstmt.executeQuery();
            int newId = 1;
            if (rs.next()) {
                newId = rs.getInt(1) + 1;
            }
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            
            this.customerId = new Integer(newId);
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.active = true;
            this.registrationDate = new java.sql.Date(System.currentTimeMillis());
            
            // Insert into database
            pstmt = conn.prepareStatement(
                "INSERT INTO customers (customer_id, first_name, last_name, email, " +
                "phone_number, address, city, country, registration_date, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            
            pstmt.setInt(1, this.customerId.intValue());
            pstmt.setString(2, this.firstName);
            pstmt.setString(3, this.lastName);
            pstmt.setString(4, this.email);
            pstmt.setString(5, this.phoneNumber);
            pstmt.setString(6, this.address);
            pstmt.setString(7, this.city);
            pstmt.setString(8, this.country);
            pstmt.setDate(9, this.registrationDate);
            pstmt.setBoolean(10, this.active);
            
            int rows = pstmt.executeUpdate();
            if (rows != 1) {
                throw new CreateException("Failed to insert customer");
            }
            
            return this.customerId;
            
        } catch (SQLException e) {
            throw new CreateException("Database error: " + e.getMessage());
        } finally {
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    public void ejbPostCreate(String firstName, String lastName, String email) {
        // Required but empty
    }
    
    /**
     * ejbLoad - BMP requires manual SELECT
     */
    public void ejbLoad() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            Integer pk = (Integer) context.getPrimaryKey();
            conn = getConnection();
            
            pstmt = conn.prepareStatement(
                "SELECT first_name, last_name, email, phone_number, address, " +
                "city, country, registration_date, active FROM customers " +
                "WHERE customer_id = ?");
            pstmt.setInt(1, pk.intValue());
            
            rs = pstmt.executeQuery();
            if (rs.next()) {
                this.customerId = pk;
                this.firstName = rs.getString("first_name");
                this.lastName = rs.getString("last_name");
                this.email = rs.getString("email");
                this.phoneNumber = rs.getString("phone_number");
                this.address = rs.getString("address");
                this.city = rs.getString("city");
                this.country = rs.getString("country");
                this.registrationDate = rs.getDate("registration_date");
                this.active = rs.getBoolean("active");
            } else {
                throw new EJBException("Customer not found: " + pk);
            }
            
        } catch (SQLException e) {
            throw new EJBException("Failed to load customer: " + e.getMessage());
        } finally {
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    /**
     * ejbStore - BMP requires manual UPDATE
     */
    public void ejbStore() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = getConnection();
            
            pstmt = conn.prepareStatement(
                "UPDATE customers SET first_name=?, last_name=?, email=?, " +
                "phone_number=?, address=?, city=?, country=?, " +
                "registration_date=?, active=? WHERE customer_id=?");
            
            pstmt.setString(1, this.firstName);
            pstmt.setString(2, this.lastName);
            pstmt.setString(3, this.email);
            pstmt.setString(4, this.phoneNumber);
            pstmt.setString(5, this.address);
            pstmt.setString(6, this.city);
            pstmt.setString(7, this.country);
            pstmt.setDate(8, this.registrationDate);
            pstmt.setBoolean(9, this.active);
            pstmt.setInt(10, this.customerId.intValue());
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new EJBException("Failed to store customer: " + e.getMessage());
        } finally {
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    /**
     * ejbRemove - BMP requires manual DELETE
     */
    public void ejbRemove() throws RemoveException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        
        try {
            conn = getConnection();
            
            pstmt = conn.prepareStatement(
                "DELETE FROM customers WHERE customer_id = ?");
            pstmt.setInt(1, this.customerId.intValue());
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RemoveException("Failed to remove customer: " + e.getMessage());
        } finally {
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    // Finder methods
    public Integer ejbFindByPrimaryKey(Integer primaryKey) throws FinderException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            
            pstmt = conn.prepareStatement(
                "SELECT customer_id FROM customers WHERE customer_id = ?");
            pstmt.setInt(1, primaryKey.intValue());
            
            rs = pstmt.executeQuery();
            if (rs.next()) {
                return primaryKey;
            } else {
                throw new FinderException("Customer not found: " + primaryKey);
            }
            
        } catch (SQLException e) {
            throw new FinderException("Database error: " + e.getMessage());
        } finally {
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    public Collection ejbFindAll() throws FinderException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            
            pstmt = conn.prepareStatement("SELECT customer_id FROM customers");
            rs = pstmt.executeQuery();
            
            Collection customerIds = new ArrayList();
            while (rs.next()) {
                customerIds.add(new Integer(rs.getInt("customer_id")));
            }
            
            return customerIds;
            
        } catch (SQLException e) {
            throw new FinderException("Database error: " + e.getMessage());
        } finally {
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    public Collection ejbFindByCity(String city) throws FinderException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = getConnection();
            
            pstmt = conn.prepareStatement(
                "SELECT customer_id FROM customers WHERE city = ?");
            pstmt.setString(1, city);
            rs = pstmt.executeQuery();
            
            Collection customerIds = new ArrayList();
            while (rs.next()) {
                customerIds.add(new Integer(rs.getInt("customer_id")));
            }
            
            return customerIds;
            
        } catch (SQLException e) {
            throw new FinderException("Database error: " + e.getMessage());
        } finally {
            closeResultSet(rs);
            closePreparedStatement(pstmt);
            closeConnection(conn);
        }
    }
    
    // EntityBean lifecycle methods
    public void setEntityContext(EntityContext context) {
        this.context = context;
    }
    
    public void unsetEntityContext() {
        this.context = null;
    }
    
    public void ejbActivate() {
        this.customerId = (Integer) context.getPrimaryKey();
    }
    
    public void ejbPassivate() {
        this.customerId = null;
    }
    
    // Getters and Setters
    public Integer getCustomerId() {
        return customerId;
    }
    
    public String getFirstName() {
        return firstName;
    }
    
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    
    public String getLastName() {
        return lastName;
    }
    
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }
    
    public java.sql.Date getRegistrationDate() {
        return registrationDate;
    }
    
    public void setRegistrationDate(java.sql.Date registrationDate) {
        this.registrationDate = registrationDate;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    // Legacy helper methods - antipattern: resource management in bean
    private Connection getConnection() throws SQLException {
        try {
            Context ctx = new InitialContext();
            DataSource ds = (DataSource) ctx.lookup(DS_JNDI);
            return ds.getConnection();
        } catch (NamingException e) {
            throw new SQLException("Failed to lookup datasource: " + e.getMessage());
        }
    }
    
    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                // Log but don't throw
                System.err.println("Failed to close connection: " + e.getMessage());
            }
        }
    }
    
    private void closePreparedStatement(PreparedStatement pstmt) {
        if (pstmt != null) {
            try {
                pstmt.close();
            } catch (SQLException e) {
                System.err.println("Failed to close statement: " + e.getMessage());
            }
        }
    }
    
    private void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                System.err.println("Failed to close result set: " + e.getMessage());
            }
        }
    }
}
