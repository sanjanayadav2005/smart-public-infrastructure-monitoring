package com.smartinfra.smart_public_infra.repository;

import com.smartinfra.smart_public_infra.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
}
