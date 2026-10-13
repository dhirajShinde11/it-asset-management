package com.company.itam.repository;

import com.company.itam.model.ConsumableCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsumableCheckoutRepository extends JpaRepository<ConsumableCheckout, Long> {
    List<ConsumableCheckout> findByConsumable_Id(Long consumableId);
    List<ConsumableCheckout> findByUserEmailIgnoreCase(String userEmail);
    List<ConsumableCheckout> findByUserId(Long userId);
}
