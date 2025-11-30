package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.CatalogueProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogueProductStockRepository extends JpaRepository<CatalogueProductStock,Long> {
    List<CatalogueProductStock> findByProductId(Long productId);
    List<CatalogueProductStock> findByProductIdAndLocationId(Long productId, Long locationId);
}
