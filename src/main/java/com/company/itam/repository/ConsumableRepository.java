package com.company.itam.repository;

import com.company.itam.model.Consumable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsumableRepository extends JpaRepository<Consumable, Long> {
    List<Consumable> findByCategoryIgnoreCase(String category);
    List<Consumable> findByRemainingQuantityLessThanEqual(Integer threshold);

    @Query("SELECT COUNT(c) FROM Consumable c WHERE c.remainingQuantity <= COALESCE(c.minQuantity, 5)")
    long countLowStock();
}
