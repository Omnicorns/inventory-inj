package com.sarinah.product_bundling.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "catalogue_odoo_product_attribute")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogOdooProductAttribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // relasi ke product
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private CatalogOdooProduct product;

    @Column(name = "attribute_name", length = 150)
    private String attributeName;    // "Size", "Motif", "Warna / Jenis"

    @Column(name = "value", length = 255)
    private String value;            // "L", "MOTIF ABSTRAK", "HITAM"

    @Column(name = "display_name", length = 255)
    private String displayName;      // "Size: L"
}
