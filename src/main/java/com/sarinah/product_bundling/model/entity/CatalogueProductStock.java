package com.sarinah.product_bundling.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "catalogue_product_stock",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_location_pricelist",
                columnNames = {"product_id", "location_id", "pricelist_name"}
        )
)
public class CatalogueProductStock{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // relasi ke header product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private CatalogueProduct product;

    @Column(name = "location_id")
    private Long locationId; // 311143

    @Column(name = "location_name", length = 255)
    private String locationName; // "THA/Stock/Thamrin"

    @Column(name = "pricelist_name", length = 255)
    private String pricelistName; // "Thamrin Pricelist"

    @Column(name = "quantity", precision = 19, scale = 3)
    private BigDecimal quantity;

    @Column(name = "uom", length = 50)
    private String uom;

    @Column(name = "price", precision = 19, scale = 2)
    private BigDecimal price;

}
