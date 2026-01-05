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

@Service
@RequiredArgsConstructor
@Slf4j
public class PostSaveOdooCatalogueProductService {
    private final ObjectMapper objectMapper; // biarin kalau memang dipakai di tempat lain
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;
    private final CatalogueOdooProductRepository catalogueProductRepository;

    /**
     * Goal: cegah DB bengkak tanpa rombak besar:
     * - Jangan update/save kalau tidak ada perubahan
     * - syncedAt hanya berubah kalau ada perubahan
     * - imageBase64 hanya di-set kalau berubah
     * - stocks dan attributes hanya diubah kalau beda (minimal)
     */
    @Transactional
    public void execute() {
        JsonNode root = sarinahGetModulAdaptor.getCatalogueCart();

        if (root == null || !root.isArray() || root.size() == 0) {
            log.info("Catalogue sync: response kosong, tidak ada yang disync");
            return;
        }

        int savedCount = 0;

        for (JsonNode templateNode : root) {
            Long templateId = templateNode.path("template_id").isNumber()
                    ? templateNode.get("template_id").asLong()
                    : null;
            String templateName = templateNode.path("template_name").asText(null);
            String category = templateNode.path("category").asText(null);
            String brand = templateNode.path("brand").asText(null);
            String owner = templateNode.path("owner_id").asText(null);
            String productType = templateNode.path("product_type").asText(null); // kalau dipakai di entity nanti

            JsonNode variants = templateNode.get("variants");
            if (variants == null || !variants.isArray()) continue;

            for (JsonNode variantNode : variants) {
                try {
                    boolean saved = upsertVariantNode(
                            templateId,
                            templateName,
                            category,
                            brand,
                            owner,
                            variantNode
                    );
                    if (saved) savedCount++;
                } catch (Exception e) {
                    log.warn("Gagal memproses variant node: {}", variantNode, e);
                }
            }
        }

        log.info("Catalogue sync: {} produk tersimpan/ter-update (hanya yang berubah)", savedCount);
    }

    /**
     * @return true kalau ada perubahan dan dilakukan save
     */
    private boolean upsertVariantNode(
            Long templateId,
            String templateName,
            String category,
            String brand,
            String owner,
            JsonNode node
    ) {
        Long odooId = node.path("id").isNumber() ? node.get("id").asLong() : null;
        if (odooId == null) return false;

        CatalogOdooProduct product = catalogueProductRepository
                .findByOdooProductId(odooId)
                .orElseGet(CatalogOdooProduct::new);

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

        // hasImage
        Boolean oldHasImage = product.getHasImage(); // bisa null
        changed |= setIfDiffBoolNullable(oldHasImage, hasImagePayload, product::setHasImage);


        // imageBase64: HANYA set kalau beda (biar tidak update besar tiap menit)
        // Jika payload tidak punya base64 (false), defaultnya kita BIARKAN base64 lama tetap ada.
        // Kalau kamu ingin hapus image saat payload false, lihat bagian "hapus" di bawah.
        if (hasImagePayload && imageBase64Payload != null) {
            if (!Objects.equals(product.getImageBase64(), imageBase64Payload)) {
                product.setImageBase64(imageBase64Payload);
                changed = true;
            }
        } else {
            // Default: jangan overwrite jadi null, supaya tidak bolak-balik update.
            // Kalau memang Odoo selalu kirim false untuk yang tidak punya image dan kamu ingin bersih:
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
     * Attributes: agar tidak "clear + insert" tiap menit (itu bikin bloat juga),
     * kita bandingkan snapshot sederhana dulu. Jika sama, tidak ubah apa-apa.
     */
    private boolean syncAttributesIfChanged(CatalogOdooProduct product, JsonNode attrsNode) {
        // build snapshot baru dari payload: urutkan supaya stabil
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

                // snapshot string
                newSnapshot.add(
                        String.valueOf(attrName) + "|" + String.valueOf(attrValue) + "|" + String.valueOf(display)
                );
            }
        }

        Collections.sort(newSnapshot);

        // snapshot existing dari entity: juga urutkan
        List<String> oldSnapshot = new ArrayList<>();
        if (product.getAttributes() != null) {
            for (CatalogOdooProductAttribute a : product.getAttributes()) {
                oldSnapshot.add(
                        String.valueOf(a.getAttributeName()) + "|" + String.valueOf(a.getValue()) + "|" + String.valueOf(a.getDisplayName())
                );
            }
        }
        Collections.sort(oldSnapshot);

        // kalau sama persis -> tidak ada perubahan
        if (oldSnapshot.equals(newSnapshot)) {
            return false;
        }

        // beda -> baru rebuild (ini perubahan minimal tapi tidak setiap menit)
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

    private boolean setIfDiffBool(boolean oldVal, boolean newVal, java.util.function.Consumer<Boolean> setter) {
        if (oldVal != newVal) {
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
