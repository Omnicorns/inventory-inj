package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.ProductBundling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBundlingRepository extends JpaRepository<ProductBundling,Long> {
    Optional<ProductBundling> findFirstByOdooProductIdAndLocationId(Long odooProductId, Long locationId);

    List<ProductBundling> findByOdooProductId(Long odooProductId);
}
