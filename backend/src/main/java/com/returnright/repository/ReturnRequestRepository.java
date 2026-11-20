package com.returnright.repository;

import com.returnright.model.entity.ReturnRequest;
import com.returnright.model.enums.ReturnStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Return persistence. */
public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

  /** Creation times of the customer's returns since {@code since}, ignoring one status. */
  @Query(
      """
      select r.createdAt from ReturnRequest r
      where r.customerEmail = :email and r.status <> :excludedStatus and r.createdAt >= :since
      """)
  List<Instant> findRequestTimes(
      @Param("email") String email,
      @Param("excludedStatus") ReturnStatus excludedStatus,
      @Param("since") Instant since);

  /** Next value for the running part of the RMA number. Never reused, even on rollback. */
  @Query(value = "select nextval('rma_number_seq')", nativeQuery = true)
  long nextRmaSequence();
}
