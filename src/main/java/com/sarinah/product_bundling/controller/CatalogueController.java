package com.sarinah.product_bundling.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sarinah.product_bundling.model.entity.InventoryInj;
import com.sarinah.product_bundling.model.request.InjSpecRowUpdateRequest;
import com.sarinah.product_bundling.model.response.ProductInjSpecResponse;
import com.sarinah.product_bundling.model.response.ProductOdooInjSpecResponse;
import com.sarinah.product_bundling.service.InventoryInjService;
import com.sarinah.product_bundling.service.PostCatalogueList;
import com.sarinah.product_bundling.service.ProductInjSpecService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
        productInjSpecService.saveSpecForOdooProduct(odooProductId, rows);
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
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String category
    ) {
//        if (odooProductId != null) {
//            // detail 1 produk
//           // ProductInjSpecResponse resp = productInjSpecService.getSpecForProduct(odooProductId);
//           // return ResponseEntity.ok(resp);
//        } else {
            // list semua / difilter sku
            List<ProductOdooInjSpecResponse> list = productInjSpecService.getAllSpec(category)
                    .stream()
                    .map(resp -> {
                        resp.setBrand(null);
                        resp.setOwnerId(null);
                        resp.setProductId(resp.getTemplateId());
                        resp.setProductName(resp.getTemplateName());
                        resp.setTemplateId(null);
                        resp.setTemplateName(null);

                        // (opsional) kalau ada field template yang mau di-null-kan
                        // resp.setOwnerId(null);
                        // resp.setBrand(null);
                        // dst...

                        if (resp.getVariants() != null) {
                            resp.getVariants().forEach(variant -> {

                                // (opsional) null-kan field yang “berat” di variant
                                ;
                                // variant.setListPrice(null);
                                // dst...

                                variant.setListPrice(null);

                                if (variant.getRows() != null) {
                                    variant.getRows().forEach(row -> {
                                        // null-in field per ROW di sini
                                     row.setQuantityOdoo(null);
                                     row.setAddedValuePct(null);
                                     row.setActive(null);
                                     row.setBasePrice(null);
                                     row.setStockPctToInj(null);
                                     row.setLimitStock(null);
                                     row.setMarginInjPct(null);
                                     row.setPricingMode(null);


                                    });
                                }
                            });
                        }

                        return resp;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(list);
        }
    }

