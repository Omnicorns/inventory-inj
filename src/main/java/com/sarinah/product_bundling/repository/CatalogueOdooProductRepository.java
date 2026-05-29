package com.sarinah.product_bundling.repository;

import com.sarinah.product_bundling.model.entity.CatalogOdooProduct;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CatalogueOdooProductRepository extends JpaRepository<CatalogOdooProduct, Long> {
    Optional<CatalogOdooProduct> findByOdooProductId(Long odooProductId);

    List<CatalogOdooProduct> findByTemplateId(Long templateId);

    List<CatalogOdooProduct> findByBrand(String brand);

    List<CatalogOdooProduct> findByWarnaJenis(String warnaJenis);
    List<CatalogOdooProduct> findBySkuContainingIgnoreCase(String sku);
    List<CatalogOdooProduct> findByCategoryContainingIgnoreCase(String keyword);
    Page<CatalogOdooProduct> findBySkuContainingIgnoreCaseOrNameContainingIgnoreCase(
            String sku,
            String name,
            Pageable pageable
    );
    @EntityGraph(attributePaths = {"attributes"}) // ONLY ONE BAG
    Optional<CatalogOdooProduct> findGraphByOdooProductId(Long odooProductId);


    @Query("SELECT DISTINCT p.templateId FROM CatalogOdooProduct p ORDER BY p.templateId")
    Page<Long> findDistinctTemplateIds(Pageable pageable);

    @Query("""
    SELECT DISTINCT p.templateId FROM CatalogOdooProduct p
    WHERE LOWER(p.sku)  LIKE LOWER(CONCAT('%', :q, '%'))
       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
    ORDER BY p.templateId
    """)
    Page<Long> findDistinctTemplateIdsBySearch(@Param("q") String q, Pageable pageable);

    List<CatalogOdooProduct> findByTemplateIdIn(List<Long> templateIds);




}
