package br.com.semeru.model.dao;

import java.io.Serializable;
import java.util.List;

import javax.persistence.criteria.CriteriaQuery;

public interface InterfaceDAO<T> {
	
	void save (T entity);
    void update (T entity);
    void remove (T entity);
    void executeNativeQuery(String stringQuery);
    T getEntity(Serializable id);
    T getEntityByCriteria(CriteriaQuery<T> criteria);
    T getEntityByJPQLQuery(String stringQuery);
    List<T> getEntities();
    List<T> getListByCriteria(CriteriaQuery<T> criteria);  
    List<T> getListByJPQLQuery(String stringQuery); 
}
