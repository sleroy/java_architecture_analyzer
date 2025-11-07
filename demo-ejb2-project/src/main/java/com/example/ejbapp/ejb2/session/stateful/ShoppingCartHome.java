package com.example.ejbapp.ejb2.session.stateful;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import java.rmi.RemoteException;

/**
 * Remote Home interface for ShoppingCart Stateful Session Bean
 * 
 * @ejb.home remote-class="com.example.ejbapp.ejb2.session.stateful.ShoppingCartHome"
 */
public interface ShoppingCartHome extends EJBHome {
    
    /**
     * Create a new shopping cart for customer
     * 
     * @ejb.create-method
     */
    ShoppingCart create(String customerId) 
        throws CreateException, RemoteException;
}
