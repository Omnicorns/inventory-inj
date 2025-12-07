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
    private final ObjectMapper objectMapper;
    private final SarinahGetModulAdaptor sarinahGetModulAdaptor;
    private final CatalogueOdooProductRepository catalogueProductRepository;


    @Transactional
    public void execute() {
        JsonNode root = sarinahGetModulAdaptor.getCatalogueCart();

        if (root == null || !root.isArray() || root.size() == 0) {
            log.info("Catalogue sync: response kosong, tidak ada yang disync");
            return;
        }

        List<CatalogOdooProduct> toSave = new ArrayList<>();

        for (JsonNode templateNode : root) {
            Long templateId   = templateNode.path("template_id").isNumber()
                    ? templateNode.get("template_id").asLong()
                    : null;
            String templateName = templateNode.path("template_name").asText(null);
            String category     = templateNode.path("category").asText(null);
            String brand        = templateNode.path("brand").asText(null);
            String owner        = templateNode.path("owner_id").asText(null);
            String productType = templateNode.path("product_type").asText(null);

            JsonNode variants = templateNode.get("variants");
            if (variants == null || !variants.isArray()) {
                continue;
            }

            for (JsonNode variantNode : variants) {
                try {
                    CatalogOdooProduct product = upsertVariantNode(
                            templateId,
                            templateName,
                            category,
                            brand,
                            owner,
                            variantNode
                    );
                    if (product != null) {
                        toSave.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Gagal memproses variant node: {}", variantNode, e);
                }
            }
        }

        if (!toSave.isEmpty()) {
            catalogueProductRepository.saveAll(toSave);
            log.info("Catalogue sync: {} produk berhasil disimpan/diupdate", toSave.size());
        } else {
            log.info("Catalogue sync: tidak ada produk yang tersimpan");
        }
    }

    private CatalogOdooProduct upsertVariantNode(
            Long templateId,
            String templateName,
            String category,
            String brand,
            String owner,
            JsonNode node
    ) {
        Long odooId = node.path("id").isNumber() ? node.get("id").asLong() : null;
        if (odooId == null) {
            return null;
        }

        CatalogOdooProduct product = catalogueProductRepository
                .findByOdooProductId(odooId)
                .orElseGet(CatalogOdooProduct::new);

        fillHeader(product, templateId, templateName, category, brand, owner, odooId, node);
        syncStocks(product, node.get("stock_by_location"));

        return product;
    }

    private void fillHeader(
            CatalogOdooProduct product,
            Long templateId,
            String templateName,
            String category,
            String brand,
            String owner,
            Long odooId,
            JsonNode node
    ) {
        String sku     = node.path("default_code").asText(null);
        String name    = node.path("name").asText(null);
        String barcode = node.path("barcode").asText(null);

        BigDecimal listPrice = asBigDecimal(node.get("list_price"));

        // handle image: bisa false atau base64
        JsonNode imageNode = node.get("image");
        boolean hasImage = false;
        String imageBase64 = null;

        if (imageNode != null && !imageNode.isNull()) {
            if (imageNode.isBoolean()) {
                hasImage = imageNode.asBoolean(false);
            } else if (imageNode.isTextual()) {
                String img = imageNode.asText();
                if (img != null && !img.isBlank() && !"false".equalsIgnoreCase(img)) {
                    hasImage = true;
                    imageBase64 = img;
                }
            }
        }

        // baca attr "Warna / Jenis"
        List<CatalogOdooProductAttribute> attrEntities = new ArrayList<>();
        product.clearAttributes();

        JsonNode attrs = node.path("attributes");
        if (attrs != null && attrs.isArray()) {
            for (JsonNode attrNode : attrs) {
                String attrName  = attrNode.path("attribute").asText(null);
                String attrValue = attrNode.path("value").asText(null);
                String display   = attrNode.path("display_name").asText(null);

                // kalau display_name kosong tapi name & value ada → bikin sendiri
                if ((display == null || display.isBlank())
                        && attrName != null && attrValue != null) {
                    display = attrName + ": " + attrValue;
                }

                // skip kalau benar-benar kosong semua
                if (attrName == null && attrValue == null && display == null) {
                    continue;
                }

                // buat entity attribute child
                CatalogOdooProductAttribute attrEntity = new CatalogOdooProductAttribute();
                attrEntity.setProduct(product);          // 🔴 ini penting buat relasi
                attrEntity.setAttributeName(attrName);
                attrEntity.setValue(attrValue);
                attrEntity.setDisplayName(display);


                product.addAttribute(attrEntity);

                // sambil isi warnaJenis kalau ketemu "Warna / Jenis"

            }
        }

// se

        product.setOdooProductId(odooId);
        product.setSku(sku);
        product.setName(name);
        product.setBarcode(barcode);
        product.setListPrice(listPrice);
        product.setHasImage(hasImage);
        product.setImageBase64(imageBase64);

        product.setSyncedAt(Instant.now());

        // meta template
        product.setTemplateId(templateId);
        product.setTemplateName(templateName);
        product.setCategory(category);
        product.setBrand(brand);
        product.setOwner(owner);
        //product.setWarnaJenis(warnaJenis);
    }

    private void syncStocks(CatalogOdooProduct product, JsonNode stockByLocation) {
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
                if (locNode == null || locNode.isNull()) {
                    continue;
                }

                Long locationId      = locNode.path("id").asLong();
                String locationName  = locNode.path("location").asText(null);
                String pricelistName = locNode.path("pricelist_name").asText(null);
                BigDecimal quantity  = asBigDecimal(locNode.get("quantity"));
                String uom           = locNode.path("uom").asText(null);
                BigDecimal price     = asBigDecimal(locNode.get("price"));

                String key = stockKey(locationId, pricelistName);
                seenKeys.add(key);

                CatalogueOdooProductStock stock = existingIndex.get(key);
                if (stock == null) {
                    stock = new CatalogueOdooProductStock();
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

        // hapus stok yang tidak ada lagi di payload
        product.getStocks().removeIf(s ->
                !seenKeys.contains(stockKey(s.getLocationId(), s.getPricelistName()))
        );
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
                log.warn("Gagal parse BigDecimal dari teks: {}", text);
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
