package com.sarinah.product_bundling.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinah.product_bundling.adaptor.SarinahGetModulAdaptor;
import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import com.sarinah.product_bundling.model.entity.CatalogueProductStock;
import com.sarinah.product_bundling.repository.CatalogueProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostSaveCatalogueProductService {
    private final ObjectMapper objectMapper;
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;
    private final CatalogueProductRepository catalogueProductRepository;


    public void execute(){
       var response = sarinahGetModulAdaptor.getCatalogueCart();
        if (response == null || response.isEmpty()) {
            return;
        }

        for (JsonNode node : response) {
            upsertFromNode(node);
        }

    }

    private void upsertFromNode(JsonNode node) {
        // --- ambil field basic ---
        Long odooId = node.path("id").isNumber() ? node.get("id").asLong() : null;
        if (odooId == null) {
            // kalau ga ada id, skip
            return;
        }

        String sku = node.path("default_code").asText(null);
        String name = node.path("name").asText(null);
        String barcode = node.path("barcode").asText(null);
        BigDecimal listPrice = asBigDecimal(node.get("list_price"));

        // --- handle image: bisa false atau base64 ---
        JsonNode imageNode = node.get("image");
        boolean hasImage = false;
        String imageBase64 = null; // pakai ini kalau entity-mu sudah ada field imageBase64

        if (imageNode != null && !imageNode.isNull()) {
            if (imageNode.isBoolean()) {
                // kalau literal boolean dari Odoo (false / true)
                hasImage = imageNode.asBoolean(false);
            } else if (imageNode.isTextual()) {
                // kalau base64 string
                String img = imageNode.asText();
                if (img != null && !img.isBlank() && !"false".equalsIgnoreCase(img)) {
                    hasImage = true;
                    imageBase64 = img;
                }
            }
        }

        // --- cari existing product ---
        CatalogueProduct product = catalogueProductRepository
                .findByOdooProductId(odooId)
                .orElseGet(CatalogueProduct::new);

        // --- isi / update header ---
        product.setOdooProductId(odooId);
        product.setSku(sku);
        product.setName(name);
        product.setBarcode(barcode);
        product.setListPrice(listPrice);
        product.setHasImage(hasImage);
        // kalau entity-mu ada kolom imageBase64, un-comment baris ini:
        product.setImageBase64(imageBase64);
        product.setSyncedAt(Instant.now());

        // pastikan list stocks tidak null
        if (product.getStocks() == null) {
            product.setStocks(new ArrayList<>());
        }

        // index stok existing berdasarkan (locationId + pricelistName)
        Map<String, CatalogueProductStock> existingIndex = product.getStocks().stream()
                .collect(Collectors.toMap(
                        s -> stockKey(s.getLocationId(), s.getPricelistName()),
                        Function.identity(),
                        (a, b) -> a // kalau duplicate key di list, ambil salah satu saja
                ));

        // untuk menandai key mana saja yang masih dipakai oleh payload baru
        Set<String> seenKeys = new HashSet<>();

        // --- mapping stock_by_location ---
        JsonNode stockByLocation = node.get("stock_by_location");
        if (stockByLocation != null && stockByLocation.isObject()) {
            Iterator<String> fieldNames = stockByLocation.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();       // contoh: "311143"
                JsonNode locNode = stockByLocation.get(field);

                Long locationId = locNode.path("id").asLong();
                String locationName = locNode.path("location").asText(null);    // "THA/Stock/Thamrin"
                String pricelistName = locNode.path("pricelist_name").asText(null);
                BigDecimal quantity = asBigDecimal(locNode.get("quantity"));    // 11.0
                String uom = locNode.path("uom").asText(null);                  // "Pcs"
                BigDecimal price = asBigDecimal(locNode.get("price"));          // 87000.0

                String key = stockKey(locationId, pricelistName);
                seenKeys.add(key);

                // kalau sudah ada stok lama dengan key ini → update
                CatalogueProductStock stock = existingIndex.get(key);
                if (stock == null) {
                    // belum ada → buat baru
                    stock = new CatalogueProductStock();
                    stock.setProduct(product);
                    product.getStocks().add(stock);
                }

                stock.setLocationId(locationId);
                stock.setLocationName(locationName);
                stock.setPricelistName(pricelistName);
                stock.setQuantity(quantity);
                stock.setUom(uom);
                stock.setPrice(price);
            }
        }

        // hapus stok yang sudah tidak ada di payload Odoo
        product.getStocks().removeIf(s ->
                !seenKeys.contains(stockKey(s.getLocationId(), s.getPricelistName()))
        );

        // --- save (CascadeType.ALL akan ikut save stocks) ---
        catalogueProductRepository.save(product);
    }

    private String stockKey(Long locationId, String pricelistName) {
        return (locationId != null ? locationId : 0L) + "::" + (pricelistName != null ? pricelistName : "");
    }

    private BigDecimal asBigDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }


    @Scheduled(cron = "0 */1 * * * *", zone = "Asia/Jakarta")
    @Transactional
    public void scheduledExecute() {
        try {
            log.info("Start sync catalogue product_inj (scheduler)");
            execute();
            log.info("Finish sync catalogue product_inj");
        } catch (Exception e) {
            log.error("Error saat sync catalogue product_inj (scheduler)", e);
        }
    }

}
