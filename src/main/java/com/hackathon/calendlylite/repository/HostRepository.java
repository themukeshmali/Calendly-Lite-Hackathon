package com.hackathon.calendlylite.repository;

import com.hackathon.calendlylite.entity.Host;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HostRepository extends JpaRepository<Host, Long> {

    // Check if email is already registered (used in HostService.createHost)
    boolean existsByEmail(String email);

    // FIX BUG 8: Removed unused findByEmail() — it was declared but never called anywhere.
}
