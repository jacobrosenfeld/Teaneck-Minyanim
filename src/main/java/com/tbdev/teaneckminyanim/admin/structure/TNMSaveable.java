package com.tbdev.teaneckminyanim.admin.structure;

import java.util.List;

public interface TNMSaveable<T extends TNMObject> {
//    T findByName(String name);

    T findById(String id);

    List<T> getAll();

    boolean save(T objectToSave);

//    boolean disable(T objectToDisable);

    boolean delete(T objectToDelete);

    boolean update(T objectToUpdate);
}
