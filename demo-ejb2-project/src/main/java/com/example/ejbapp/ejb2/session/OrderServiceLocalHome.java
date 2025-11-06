package com.example.ejbapp.ejb2.session;

import javax.ejb.CreateException;
import javax.ejb.EJBLocalHome;

/**
 * EJB 2.0 Stateless Session Bean - Local Home Interface
 * This is the local home interface for the OrderService Stateless Session Bean.
 * It provides the create method for obtaining a local reference to the session bean.
 */
public interface OrderServiceLocalHome extends EJBLocalHome {

    /**
     * Creates a local reference to the OrderService Stateless Session Bean.
     * @return A local reference to the OrderServiceLocal.
     * @throws CreateException
     */
    OrderServiceLocal create() throws CreateException;
}
