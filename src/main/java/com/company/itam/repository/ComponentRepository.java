package com.company.itam.repository;

import com.company.itam.model.Component;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComponentRepository extends JpaRepository<Component, Long> {
    List<Component> findByCategoryIgnoreCase(String category);
    List<Component> findByRemainingQuantityLessThanEqual(Integer threshold);
}
