package com.sarinah.product_bundling.repository;


import com.sarinah.product_bundling.model.entity.CatalogueOdooProductStock;
import com.sarinah.product_bundling.model.entity.CatalogueProductStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogueOdooProductStockRepository extends JpaRepository<CatalogueOdooProductStock, Long> {
    List<CatalogueOdooProductStock> findByProduct_Id(Long productId);

    List<CatalogueOdooProductStock> findByLocationId(Long locationId);
    List<CatalogueOdooProductStock> findByProductId(Long productId);
    List<CatalogueOdooProductStock> findByProductIdAndLocationId(Long productId, Long locationId);
}
