package com.example.ejbapp.ejb2.bmp;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import javax.ejb.FinderException;
import java.rmi.RemoteException;
import java.util.Collection;

/**
 * Remote Home interface for Customer BMP Entity Bean
 * Legacy EJB 2.0 pattern with RMI-based remote interfaces
 * 
 * @ejb.home remote-class="com.example.ejbapp.ejb2.bmp.CustomerHome"
 */
public interface CustomerHome extends EJBHome {
    
    /**
     * Create a new customer
     * 
     * @ejb.create-method
     */
    Customer create(String firstName, String lastName, String email) 
        throws CreateException, RemoteException;
    
    /**
     * Find customer by primary key
     * 
     * @ejb.finder
     */
    Customer findByPrimaryKey(Integer primaryKey) 
        throws FinderException, RemoteException;
    
    /**
     * Find all customers
     * 
     * @ejb.finder
     */
    Collection findAll() throws FinderException, RemoteException;
    
    /**
     * Find customers by city
     * 
     * @ejb.finder
     */
    Collection findByCity(String city) throws FinderException, RemoteException;
}
