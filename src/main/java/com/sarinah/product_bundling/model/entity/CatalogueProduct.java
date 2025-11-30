package com.sarinah.product_bundling.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "catalogue_product")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogueProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK lokal

    @Column(name = "odoo_product_id", nullable = false)
    private Long odooProductId; // id dari Odoo (224411 dsb)

    @Column(name = "sku", length = 100)
    private String sku; // default_code

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "list_price", precision = 19, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "has_image")
    private Boolean hasImage;

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonIgnore
    @Builder.Default
    private List<CatalogueProductStock> stocks = new ArrayList<>();

    @Lob
    @Column(name = "image_base64")
    private String imageBase64;

    @Column(name = "synced_at")
    private Instant syncedAt;
}
