package com.example.ejbapp.ejb2.bmp;

import javax.ejb.EJBObject;
import java.rmi.RemoteException;

/**
 * Remote interface for Customer BMP Entity Bean
 * Legacy EJB 2.0 pattern with RMI-based remote access
 * 
 * @ejb.interface remote-class="com.example.ejbapp.ejb2.bmp.Customer"
 */
public interface Customer extends EJBObject {
    
    /**
     * Get customer ID
     * @ejb.interface-method
     */
    Integer getCustomerId() throws RemoteException;
    
    /**
     * Get first name
     * @ejb.interface-method
     */
    String getFirstName() throws RemoteException;
    
    /**
     * Set first name
     * @ejb.interface-method
     */
    void setFirstName(String firstName) throws RemoteException;
    
    /**
     * Get last name
     * @ejb.interface-method
     */
    String getLastName() throws RemoteException;
    
    /**
     * Set last name
     * @ejb.interface-method
     */
    void setLastName(String lastName) throws RemoteException;
    
    /**
     * Get email
     * @ejb.interface-method
     */
    String getEmail() throws RemoteException;
    
    /**
     * Set email
     * @ejb.interface-method
     */
    void setEmail(String email) throws RemoteException;
    
    /**
     * Get phone number
     * @ejb.interface-method
     */
    String getPhoneNumber() throws RemoteException;
    
    /**
     * Set phone number
     * @ejb.interface-method
     */
    void setPhoneNumber(String phoneNumber) throws RemoteException;
    
    /**
     * Get address
     * @ejb.interface-method
     */
    String getAddress() throws RemoteException;
    
    /**
     * Set address
     * @ejb.interface-method
     */
    void setAddress(String address) throws RemoteException;
    
    /**
     * Get city
     * @ejb.interface-method
     */
    String getCity() throws RemoteException;
    
    /**
     * Set city
     * @ejb.interface-method
     */
    void setCity(String city) throws RemoteException;
    
    /**
     * Get country
     * @ejb.interface-method
     */
    String getCountry() throws RemoteException;
    
    /**
     * Set country
     * @ejb.interface-method
     */
    void setCountry(String country) throws RemoteException;
    
    /**
     * Get registration date
     * @ejb.interface-method
     */
    java.sql.Date getRegistrationDate() throws RemoteException;
    
    /**
     * Set registration date
     * @ejb.interface-method
     */
    void setRegistrationDate(java.sql.Date registrationDate) throws RemoteException;
    
    /**
     * Check if active
     * @ejb.interface-method
     */
    boolean isActive() throws RemoteException;
    
    /**
     * Set active status
     * @ejb.interface-method
     */
    void setActive(boolean active) throws RemoteException;
}
