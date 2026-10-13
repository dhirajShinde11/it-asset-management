package com.company.itam.repository;

import com.company.itam.model.AppUser;
import com.company.itam.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByRole(UserRole role);

    List<AppUser> findByDepartmentIgnoreCase(String department);
}
