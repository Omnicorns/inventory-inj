package com.sarinah.product_bundling.service;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarinah.product_bundling.adaptor.SarinahGetModulAdaptor;
import com.sarinah.product_bundling.model.entity.CatalogOdooProduct;
import com.sarinah.product_bundling.model.entity.CatalogOdooProductAttribute;
import com.sarinah.product_bundling.model.entity.CatalogueOdooProductStock;
import com.sarinah.product_bundling.repository.CatalogueOdooProductRepository;
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



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 1 FILE SIAP COPAS:
 * - Scheduler aman (anti overlap, rate limit, time budget, max writes, cooldown error)
 * - Upsert per-variant TRANSACTION (biar ga transaksi raksasa)
 * - Tetap pakai logic "save only if changed" yang kamu sudah buat
 *
 * CATATAN:
 * - Kamu perlu sesuaikan package/import entity & repo sesuai project kamu:
 *   CatalogOdooProduct, CatalogueOdooProductStock, CatalogOdooProductAttribute,
 *   CatalogueOdooProductRepository, SarinahGetModulAdaptor
 */




/**
 * 1-FILE COPYPASTE VERSION (Service + TxService + Repository interface)
 *
 * FIXES:
 * - @Transactional tidak jalan karena self-invocation -> dipindah ke bean terpisah (CatalogueUpsertTxService)
 * - LazyInitializationException pada product.getAttributes()/getStocks -> fetch pakai @EntityGraph
 *
 * NOTE:
 * - Pastikan entity CatalogOdooProduct punya relasi bernama "attributes" dan "stocks".
 *   Kalau beda, ganti string di @EntityGraph(attributePaths = {"attributes","stocks"}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostSaveOdooCatalogueProductService {

    private final ObjectMapper objectMapper; // biarin kalau memang dipakai di tempat lain
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;
    private final CatalogueUpsertTxService upsertTxService;

    // =============================
    // Scheduler Safety Tuning
    // =============================
    private static final Duration MIN_GAP_BETWEEN_RUNS = Duration.ofSeconds(30); // "realtime" tapi aman
    private static final Duration MAX_RUN_DURATION = Duration.ofSeconds(20); // time budget per run
    private static final int MAX_CHANGED_SAVES = 200;                    // batasi write per run
    private static final Duration ERROR_COOLDOWN = Duration.ofSeconds(60); // kalau error, jangan spam

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastStart = Instant.EPOCH;
    private volatile Instant lastError = Instant.EPOCH;

    /**
     * Scheduler yang aman:
     * - fixedDelay lebih aman daripada cron ketat (mengurangi overlap)
     * - tetap ada rate-limit MIN_GAP
     * - skip kalau masih running
     * - cooldown kalau error
     * <p>
     * Bisa override delay via env/property: sync.catalogue.delay-ms
     */
    @Scheduled(fixedDelayString = "${sync.catalogue.delay-ms:15000}", zone = "Asia/Jakarta")
    public void scheduledExecuteSafe() {
        // cooldown kalau sebelumnya error (biar gak hammering Odoo/DB)
        if (Instant.now().isBefore(lastError.plus(ERROR_COOLDOWN))) {
            return;
        }

        // rate limit
        if (Instant.now().isBefore(lastStart.plus(MIN_GAP_BETWEEN_RUNS))) {
            return;
        }

        // anti overlap
        if (!running.compareAndSet(false, true)) {
            log.debug("Catalogue sync: skip (previous run still running)");
            return;
        }

        lastStart = Instant.now();
        long t0 = System.nanoTime();

        try {
            SyncRunResult result = executeSafely(MAX_CHANGED_SAVES, MAX_RUN_DURATION);

            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("Catalogue sync done: changedSaved={}, scannedVariants={}, durationMs={}",
                    result.getSavedChanged(), result.getScannedVariants(), ms);

        } catch (Exception e) {
            lastError = Instant.now();
            log.error("Catalogue sync error. Enter cooldown {}s", ERROR_COOLDOWN.getSeconds(), e);
        } finally {
            running.set(false);
        }
    }

    /**
     * Ini eksekusi sync "aman":
     * - ada time budget
     * - batasi max write (changed saves)
     * - upsert per variant pakai TX bean (CatalogueUpsertTxService)
     * <p>
     * NOTE: method ini sengaja TANPA @Transactional
     */
    public SyncRunResult executeSafely(int maxChangedSaves, Duration maxRunDuration) {
        Instant deadline = Instant.now().plus(maxRunDuration);

        JsonNode root = sarinahGetModulAdaptor.getCatalogueCart();

        if (root == null || !root.isArray() || root.isEmpty()) {
            log.info("Catalogue sync: response kosong, tidak ada yang disync");
            return new SyncRunResult(0, 0);
        }

        int savedCount = 0;
        int scanned = 0;

        outer:
        for (JsonNode templateNode : root) {
            Long templateId = templateNode.path("template_id").isNumber()
                    ? templateNode.get("template_id").asLong()
                    : null;
            String templateName = templateNode.path("template_name").asText(null);
            String category = templateNode.path("category").asText(null);
            String brand = templateNode.path("brand").asText(null);
            String owner = templateNode.path("owner_id").asText(null);

            JsonNode variants = templateNode.get("variants");
            if (variants == null || !variants.isArray()) continue;

            for (JsonNode variantNode : variants) {
                scanned++;

                // time budget stop
                if (Instant.now().isAfter(deadline)) break outer;

                // stop kalau sudah banyak write
                if (savedCount >= maxChangedSaves) break outer;

                try {
                    boolean saved = upsertTxService.upsertVariantNodeTx(
                            templateId,
                            templateName,
                            category,
                            brand,
                            owner,
                            variantNode
                    );
                    if (saved) savedCount++;
                } catch (Exception e) {
                    log.warn("Gagal memproses variant node (skip). odooId={}, err={}",
                            variantNode.path("id").asText("?"), e.toString());
                }
            }
        }

        return new SyncRunResult(savedCount, scanned);
    }

    // =============================
    // Simple result DTO
    // =============================
    @Data
    @AllArgsConstructor
    public static class SyncRunResult {
        private int savedChanged;
        private int scannedVariants;
    }

    // =====================================================================================
    // TX BEAN: dipisah supaya @Transactional jalan (proxy), dan fetch attributes/stocks aman
    // =====================================================================================
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public static class CatalogueUpsertTxService {

        private final CatalogueOdooProductRepository catalogueProductRepository;

        /**
         * Upsert per variant dalam TRANSAKSI sendiri.
         * Ini kunci supaya tidak bikin transaksi raksasa, mengurangi lock & bloat.
         */
        @Transactional
        public boolean upsertVariantNodeTx(
                Long templateId,
                String templateName,
                String category,
                String brand,
                String owner,
                JsonNode node
        ) {
            Long odooId = node.path("id").isNumber() ? node.get("id").asLong() : null;
            if (odooId == null) return false;

            // IMPORTANT: pakai EntityGraph biar attributes/stocks sudah ke-load -> no LazyInitializationException
            CatalogOdooProduct product = catalogueProductRepository
                    .findGraphByOdooProductId(odooId)
                    .orElseGet(CatalogOdooProduct::new);

// init collection kedua DI DALAM TX (hindari lazy error)
            if (product.getStocks() != null) product.getStocks().size();
            else product.setStocks(new ArrayList<>());

            boolean changed = false;

            changed |= fillHeaderIfChanged(product, templateId, templateName, category, brand, owner, odooId, node);
            changed |= syncAttributesIfChanged(product, node.path("attributes"));
            changed |= syncStocksIfChanged(product, node.get("stock_by_location"));

            if (changed) {
                product.setSyncedAt(Instant.now()); // syncedAt hanya berubah kalau memang ada perubahan
                catalogueProductRepository.save(product);
                return true;
            }

            return false;
        }

        /**
         * Header: set field hanya kalau beda.
         */
        private boolean fillHeaderIfChanged(
                CatalogOdooProduct product,
                Long templateId,
                String templateName,
                String category,
                String brand,
                String owner,
                Long odooId,
                JsonNode node
        ) {
            boolean changed = false;

            String sku = node.path("default_code").asText(null);
            String name = node.path("name").asText(null);
            String barcode = node.path("barcode").asText(null);
            BigDecimal listPrice = asBigDecimal(node.get("list_price"));

            // handle image: bisa false / true / base64 string
            JsonNode imageNode = node.get("image");
            boolean hasImagePayload = false;
            String imageBase64Payload = null;

            if (imageNode != null && !imageNode.isNull()) {
                if (imageNode.isBoolean()) {
                    hasImagePayload = imageNode.asBoolean(false);
                } else if (imageNode.isTextual()) {
                    String img = imageNode.asText();
                    if (img != null && !img.isBlank() && !"false".equalsIgnoreCase(img)) {
                        hasImagePayload = true;
                        imageBase64Payload = img;
                    }
                }
            }

            // odoo id
            if (!Objects.equals(product.getOdooProductId(), odooId)) {
                product.setOdooProductId(odooId);
                changed = true;
            }

            // basic fields
            changed |= setIfDiffString(product.getSku(), sku, product::setSku);
            changed |= setIfDiffString(product.getName(), name, product::setName);
            changed |= setIfDiffString(product.getBarcode(), barcode, product::setBarcode);
            changed |= setIfDiffBigDecimal(product.getListPrice(), listPrice, product::setListPrice);

            // hasImage (nullable safe)
            Boolean oldHasImage = product.getHasImage(); // bisa null
            changed |= setIfDiffBoolNullable(oldHasImage, hasImagePayload, product::setHasImage);

            // imageBase64: HANYA set kalau beda
            if (hasImagePayload && imageBase64Payload != null) {
                if (!Objects.equals(product.getImageBase64(), imageBase64Payload)) {
                    product.setImageBase64(imageBase64Payload);
                    changed = true;
                }
            } else {
                // Default: jangan overwrite jadi null.
                // Kalau kamu ingin hapus image saat payload false:
                /*
                if (product.getImageBase64() != null) {
                    product.setImageBase64(null);
                    changed = true;
                }
                */
            }

            // meta template
            changed |= setIfDiffLong(product.getTemplateId(), templateId, product::setTemplateId);
            changed |= setIfDiffString(product.getTemplateName(), templateName, product::setTemplateName);
            changed |= setIfDiffString(product.getCategory(), category, product::setCategory);
            changed |= setIfDiffString(product.getBrand(), brand, product::setBrand);
            changed |= setIfDiffString(product.getOwner(), owner, product::setOwner);

            return changed;
        }

        /**
         * Attributes: bandingkan snapshot dulu. Kalau sama, no-op.
         */
        private boolean syncAttributesIfChanged(CatalogOdooProduct product, JsonNode attrsNode) {
            List<String> newSnapshot = new ArrayList<>();

            if (attrsNode != null && attrsNode.isArray()) {
                for (JsonNode attrNode : attrsNode) {
                    String attrName = attrNode.path("attribute").asText(null);
                    String attrValue = attrNode.path("value").asText(null);
                    String display = attrNode.path("display_name").asText(null);

                    if ((display == null || display.isBlank()) && attrName != null && attrValue != null) {
                        display = attrName + ": " + attrValue;
                    }

                    if (attrName == null && attrValue == null && display == null) continue;

                    newSnapshot.add(String.valueOf(attrName) + "|" + String.valueOf(attrValue) + "|" + String.valueOf(display));
                }
            }

            Collections.sort(newSnapshot);

            List<String> oldSnapshot = new ArrayList<>();
            if (product.getAttributes() != null) {
                for (CatalogOdooProductAttribute a : product.getAttributes()) {
                    oldSnapshot.add(String.valueOf(a.getAttributeName()) + "|" + String.valueOf(a.getValue()) + "|" + String.valueOf(a.getDisplayName()));
                }
            }
            Collections.sort(oldSnapshot);

            if (oldSnapshot.equals(newSnapshot)) {
                return false;
            }

            product.clearAttributes();

            if (!newSnapshot.isEmpty() && attrsNode != null && attrsNode.isArray()) {
                for (JsonNode attrNode : attrsNode) {
                    String attrName = attrNode.path("attribute").asText(null);
                    String attrValue = attrNode.path("value").asText(null);
                    String display = attrNode.path("display_name").asText(null);

                    if ((display == null || display.isBlank()) && attrName != null && attrValue != null) {
                        display = attrName + ": " + attrValue;
                    }
                    if (attrName == null && attrValue == null && display == null) continue;

                    CatalogOdooProductAttribute attrEntity = new CatalogOdooProductAttribute();
                    attrEntity.setProduct(product);
                    attrEntity.setAttributeName(attrName);
                    attrEntity.setValue(attrValue);
                    attrEntity.setDisplayName(display);

                    product.addAttribute(attrEntity);
                }
            }

            return true;
        }

        /**
         * Stocks: update hanya kalau beda, hapus hanya kalau beda.
         */
        private boolean syncStocksIfChanged(CatalogOdooProduct product, JsonNode stockByLocation) {
            boolean changed = false;

            if (product.getStocks() == null) {
                product.setStocks(new ArrayList<>());
            }

            Map<String, CatalogueOdooProductStock> existingIndex = product.getStocks().stream()
                    .collect(Collectors.toMap(
                            s -> stockKey(s.getLocationId(), s.getPricelistName()),
                            Function.identity(),
                            (a, b) -> a
                    ));

            Set<String> seenKeys = new HashSet<>();

            if (stockByLocation != null && stockByLocation.isObject()) {
                Iterator<String> fieldNames = stockByLocation.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    JsonNode locNode = stockByLocation.get(field);
                    if (locNode == null || locNode.isNull()) continue;

                    Long locationId = locNode.path("id").asLong();
                    String locationName = locNode.path("location").asText(null);
                    String pricelistName = locNode.path("pricelist_name").asText(null);
                    BigDecimal quantity = asBigDecimal(locNode.get("quantity"));
                    String uom = locNode.path("uom").asText(null);
                    BigDecimal price = asBigDecimal(locNode.get("price"));

                    String key = stockKey(locationId, pricelistName);
                    seenKeys.add(key);

                    CatalogueOdooProductStock stock = existingIndex.get(key);
                    if (stock == null) {
                        stock = new CatalogueOdooProductStock();
                        stock.setProduct(product);
                        product.getStocks().add(stock);
                        changed = true; // insert row baru
                    }

                    if (!Objects.equals(stock.getLocationId(), locationId)) {
                        stock.setLocationId(locationId);
                        changed = true;
                    }
                    changed |= setIfDiffString(stock.getLocationName(), locationName, stock::setLocationName);
                    changed |= setIfDiffString(stock.getPricelistName(), pricelistName, stock::setPricelistName);
                    changed |= setIfDiffBigDecimal(stock.getQuantity(), quantity, stock::setQuantity);
                    changed |= setIfDiffString(stock.getUom(), uom, stock::setUom);
                    changed |= setIfDiffBigDecimal(stock.getPrice(), price, stock::setPrice);
                }
            }

            int before = product.getStocks().size();
            product.getStocks().removeIf(s ->
                    !seenKeys.contains(stockKey(s.getLocationId(), s.getPricelistName()))
            );
            if (product.getStocks().size() != before) {
                changed = true;
            }

            return changed;
        }

        private String stockKey(Long locationId, String pricelistName) {
            return (locationId != null ? locationId : 0L) + "::" + (pricelistName != null ? pricelistName : "");
        }

        private BigDecimal asBigDecimal(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isNumber()) return node.decimalValue();
            if (node.isTextual()) {
                String text = node.asText();
                if (text == null || text.isBlank()) return null;
                try {
                    return new BigDecimal(text);
                } catch (NumberFormatException e) {
                    log.warn("Gagal parse BigDecimal dari teks: {}", text);
                    return null;
                }
            }
            return null;
        }

        // ======= Helpers: set only if different =======
        private boolean setIfDiffString(String oldVal, String newVal, java.util.function.Consumer<String> setter) {
            if (!Objects.equals(oldVal, newVal)) {
                setter.accept(newVal);
                return true;
            }
            return false;
        }

        private boolean setIfDiffLong(Long oldVal, Long newVal, java.util.function.Consumer<Long> setter) {
            if (!Objects.equals(oldVal, newVal)) {
                setter.accept(newVal);
                return true;
            }
            return false;
        }

        private boolean setIfDiffBigDecimal(BigDecimal oldVal, BigDecimal newVal, java.util.function.Consumer<BigDecimal> setter) {
            if (oldVal == null && newVal == null) return false;
            if (oldVal == null || newVal == null) {
                setter.accept(newVal);
                return true;
            }
            if (oldVal.compareTo(newVal) != 0) {
                setter.accept(newVal);
                return true;
            }
            return false;
        }

        private boolean setIfDiffBoolNullable(Boolean oldVal, boolean newVal, java.util.function.Consumer<Boolean> setter) {
            boolean old = oldVal != null && oldVal; // null dianggap false
            if (old != newVal) {
                setter.accept(newVal);
                return true;
            }
            return false;
        }
    }

// =====================================================================================
// Repository Interface (EntityGraph untuk preload relasi agar tidak LazyInitializationException)
// =====================================================================================

}