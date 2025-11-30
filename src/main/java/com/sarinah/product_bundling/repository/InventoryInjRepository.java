package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.InventoryInj;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryInjRepository extends JpaRepository<InventoryInj,Long> {
    Optional<InventoryInj> findFirstByOdooProductIdAndLocationId(Long odooProductId, Long locationId);
    Optional<InventoryInj> findFirstByOdooProductIdOrderBySyncedAtDesc(Long odooProductId);
}
