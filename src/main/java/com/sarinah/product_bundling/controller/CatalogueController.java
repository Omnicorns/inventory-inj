package com.sarinah.product_bundling.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sarinah.product_bundling.model.entity.InventoryInj;
import com.sarinah.product_bundling.model.request.InjSpecRowUpdateRequest;
import com.sarinah.product_bundling.model.response.ProductInjSpecResponse;
import com.sarinah.product_bundling.service.InventoryInjService;
import com.sarinah.product_bundling.service.PostCatalogueList;
import com.sarinah.product_bundling.service.ProductInjSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sarinah-forwarder/v1/catalogue")
public class CatalogueController {
    private final PostCatalogueList postCatalogueList;
    private final ProductInjSpecService productInjSpecService;
    private final InventoryInjService inventoryInjService;

    @PostMapping(value = "/list")
    public ArrayNode postScanResponse() {
        return postCatalogueList.execute();
    }

    @PutMapping("/save/inj-spec/{odooProductId}")
    public ResponseEntity<Void> saveInjSpec(
            @PathVariable Long odooProductId,
            @RequestBody List<InjSpecRowUpdateRequest> rows
    ) {
        productInjSpecService.saveSpecForProduct(odooProductId, rows);
        return ResponseEntity.noContent().build();
    }

    // === 3. POST sync ke tabel inventory_inj untuk produk tsb ===
    @PostMapping("/inventory-inj/sync/{odooProductId}")
    public List<InventoryInj> syncInventoryInj(@PathVariable Long odooProductId) {
        return inventoryInjService.syncProduct(odooProductId);
    }

    @GetMapping("/inj-spec")
    public ResponseEntity<?> getInjSpec(
            @RequestParam(required = false) Long odooProductId,
            @RequestParam(required = false) String sku
    ) {
        if (odooProductId != null) {
            // detail 1 produk
            ProductInjSpecResponse resp = productInjSpecService.getSpecForProduct(odooProductId);
            return ResponseEntity.ok(resp);
        } else {
            // list semua / difilter sku
            List<ProductInjSpecResponse> list = productInjSpecService.getAllSpec(sku);
            return ResponseEntity.ok(list);
        }
    }
}

