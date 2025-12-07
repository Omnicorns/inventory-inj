package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.CatalogOdooProduct;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogueOdooProductRepository extends JpaRepository<CatalogOdooProduct, Long> {
    Optional<CatalogOdooProduct> findByOdooProductId(Long odooProductId);

    List<CatalogOdooProduct> findByTemplateId(Long templateId);

    List<CatalogOdooProduct> findByBrand(String brand);

    List<CatalogOdooProduct> findByWarnaJenis(String warnaJenis);
    List<CatalogOdooProduct> findBySkuContainingIgnoreCase(String sku);
    Page<CatalogOdooProduct> findBySkuContainingIgnoreCaseOrNameContainingIgnoreCase(
            String sku,
            String name,
            Pageable pageable
    );


}
