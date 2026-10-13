package com.company.itam.repository;

import com.company.itam.model.AccessoryCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessoryCheckoutRepository extends JpaRepository<AccessoryCheckout, Long> {
    List<AccessoryCheckout> findByAccessory_Id(Long accessoryId);
    List<AccessoryCheckout> findByUserEmailIgnoreCase(String userEmail);
    List<AccessoryCheckout> findByUserId(Long userId);
}
