package com.returnright.repository;

import com.returnright.model.entity.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

  List<AuditLogEntry> findByReturnIdOrderByIdAsc(UUID returnId);
}
