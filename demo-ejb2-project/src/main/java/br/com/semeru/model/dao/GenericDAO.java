package br.com.semeru.model.dao;

import java.io.Serializable;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;

public class GenericDAO<T> implements InterfaceDAO<T>, Serializable {

    private static final long serialVersionUID = 1L;
    
    @Inject
    private EntityManager entityManager;
    
    private Class<T> clazz;

    public GenericDAO(Class<T> clazz) {
        super();
        this.clazz = clazz;
    }

    @Override
    public void save(T entity) {
    	entityManager.persist(entity);
    }

    @Override
    public void update(T entity) {
    	entityManager.merge(entity);
    }

    @Override
    public void remove(T entity) {
    	entityManager.remove(entity);
    }

	public void executeNativeQuery(String stringQuery) {
		entityManager.createNativeQuery(stringQuery);
	}

    @Override
    public T getEntity(Serializable id) {
        return entityManager.find(clazz, id);
    }

    @Override
    public T getEntityByCriteria(CriteriaQuery<T> criteria) {
    	return (T) entityManager.createQuery(criteria).getSingleResult();
    }

	@Override
    @SuppressWarnings("unchecked") //REMOVE-ME
    public T getEntityByJPQLQuery(String stringQuery) {
    	return (T) entityManager.createQuery(stringQuery).getSingleResult();
    }

    @Override
    public List<T> getListByCriteria(CriteriaQuery<T> criteria) {
    	return entityManager.createQuery(criteria).getResultList();
    }

    @Override
	@SuppressWarnings("unchecked") //REMOVE-ME
    public List<T> getEntities() {
    	CriteriaBuilder builder = entityManager.getCriteriaBuilder();
    	return (List<T>)  builder.createQuery(clazz).getOrderList();
    }
    
	@Override
	@SuppressWarnings("unchecked") //REMOVE-ME
    public List<T> getListByJPQLQuery(String stringQuery) {
        return entityManager.createQuery(stringQuery).getResultList();
    }
}