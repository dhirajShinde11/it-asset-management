package com.company.itam.repository;

import com.company.itam.model.Accessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessoryRepository extends JpaRepository<Accessory, Long> {
    List<Accessory> findByCategoryIgnoreCase(String category);
    List<Accessory> findByRemainingQuantityLessThanEqual(Integer threshold);
    List<Accessory> findByNameContainingIgnoreCase(String name);

    @Query("SELECT COUNT(a) FROM Accessory a WHERE a.remainingQuantity <= COALESCE(a.minQuantity, 5)")
    long countLowStock();
}
