package services.common;

import java.util.List;

/**
 * Interface CRUD générique pour réduire la duplication des contrats de service.
 *
 * NOTE: Elle n'impose pas les exceptions checked pour ne pas casser les implémentations existantes.
 */
public interface CrudService<T, ID> extends ServiceBase {

    List<T> getAll();

    T getById(ID id);

    void add(T entity);

    void update(T entity);

    void delete(ID id);
}
