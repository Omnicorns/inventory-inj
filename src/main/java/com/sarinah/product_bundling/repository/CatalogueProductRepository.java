package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogueProductRepository extends JpaRepository<CatalogueProduct,Long> {
    Optional<CatalogueProduct> findByOdooProductId(Long odooProductId);
    List<CatalogueProduct> findBySkuContainingIgnoreCase(String sku);
    Page<CatalogueProduct> findBySkuContainingIgnoreCaseOrNameContainingIgnoreCase(
            String sku,
            String name,
            Pageable pageable
    );
}
