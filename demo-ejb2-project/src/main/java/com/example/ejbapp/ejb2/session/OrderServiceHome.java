package com.example.ejbapp.ejb2.session;

import javax.ejb.CreateException;
import javax.ejb.EJBHome;
import java.rmi.RemoteException;

/**
 * EJB 2.0 Stateless Session Bean - Remote Home Interface
 * This is the remote home interface for the OrderService Stateless Session Bean.
 * It provides the create method for obtaining a remote reference to the session bean.
 */
public interface OrderServiceHome extends EJBHome {

    /**
     * Creates a remote reference to the OrderService Stateless Session Bean.
     * @return A remote reference to the OrderService.
     * @throws CreateException
     * @throws RemoteException
     */
    OrderService create() throws CreateException, RemoteException;
}
