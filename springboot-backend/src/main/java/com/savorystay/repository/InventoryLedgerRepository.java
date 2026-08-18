package com.savorystay.repository;

import com.savorystay.entity.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * INSERT-only repository — no UPDATE or DELETE operations.
 * The immutable inventory audit trail is never mutated after creation.
 * Matches the reference: inventory_ledger is append-only.
 */
@Repository
public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, Long> {

    List<InventoryLedger> findByInventoryIdOrderByRecordedAtAsc(String inventoryId);

    List<InventoryLedger> findByReferenceId(String referenceId);
}