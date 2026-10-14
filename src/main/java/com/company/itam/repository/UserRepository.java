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

    @org.springframework.data.jpa.repository.Query("SELECT u FROM AppUser u WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.department) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.jobTitle) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<AppUser> searchUsers(@org.springframework.data.repository.query.Param("query") String query);
}
