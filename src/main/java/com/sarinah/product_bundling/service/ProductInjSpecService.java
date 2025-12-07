package com.sarinah.product_bundling.service;

import com.sarinah.product_bundling.model.entity.*;
import com.sarinah.product_bundling.model.request.InjSpecRowUpdateRequest;
import com.sarinah.product_bundling.model.response.*;
import com.sarinah.product_bundling.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductInjSpecService {
    private final CatalogueProductRepository catalogueProductRepository;
    private final CatalogueProductStockRepository catalogueProductStockRepository;
    private final CatalogueOdooProductRepository catalogueOdooProductRepository;
    private final CatalogueOdooProductStockRepository catalogueOdooProductStockRepository;
    private final ProductBundlingRepository productBundlingRepository;
    private final InventoryInjRepository inventoryInjRepository;


    public ProductInjSpecResponse getSpecForProduct(Long odooProductId) {

        CatalogueProduct product = catalogueProductRepository.findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        List<CatalogueProductStock> stockRows =
                catalogueProductStockRepository.findByProductId(product.getId());

        List<InjSpecRowResponse> rows = new ArrayList<>();

        for (CatalogueProductStock stock : stockRows) {

            ProductBundling cfg = productBundlingRepository
                    .findFirstByOdooProductIdAndLocationId(odooProductId, stock.getLocationId())
                    .orElse(null);

            // ========== STOCK ==========

            // stok Odoo saat ini
            BigDecimal qtyOdoo = stock.getQuantity() != null
                    ? stock.getQuantity()
                    : BigDecimal.ZERO;

            // ⛔️ TIDAK pakai persen lagi.
            // ✅ stockPctToInj kita pakai sebagai "limitStockConfig" (qty), bukan persen.
            BigDecimal limitStock = BigDecimal.ZERO;
            if (cfg != null && cfg.getStockPctToInj() != null) {
                limitStock = cfg.getStockPctToInj();
            }
            if (limitStock.compareTo(BigDecimal.ZERO) < 0) {
                limitStock = BigDecimal.ZERO;
            }

            // sellStock = max(qtyOdoo - limitStock, 0)
            BigDecimal sellStock = qtyOdoo.subtract(limitStock);
            if (sellStock.compareTo(BigDecimal.ZERO) < 0) {
                sellStock = BigDecimal.ZERO;
            }
            sellStock = sellStock.setScale(0, RoundingMode.DOWN);

            // Kalau masih mau info persen ke INJ, hitung sebagai INFO SAJA (derived)
            BigDecimal stockPctToInj = BigDecimal.ZERO;
            if (qtyOdoo.compareTo(BigDecimal.ZERO) > 0 && sellStock.compareTo(BigDecimal.ZERO) > 0) {
                stockPctToInj = sellStock
                        .multiply(BigDecimal.valueOf(100))
                        .divide(qtyOdoo, 2, RoundingMode.HALF_UP);
            }

            // ========== PRICING ==========
            String pricingMode = (cfg != null && cfg.getPricingMode() != null)
                    ? cfg.getPricingMode()
                    : "NONE";

            BigDecimal addedPct = cfg != null ? nvl(cfg.getAddedValuePct()) : BigDecimal.ZERO;
            BigDecimal marginPct = cfg != null ? nvl(cfg.getMarginInjPct()) : BigDecimal.ZERO;

            BigDecimal sellPrice = computeSellPrice(
                    stock.getPrice(),
                    pricingMode,
                    addedPct,
                    marginPct
            );

            // ========== BANGUN DTO ==========
            InjSpecRowResponse row = new InjSpecRowResponse();
            row.setLocationId(stock.getLocationId());
            row.setLocationName(stock.getLocationName());
            row.setPricelistName(stock.getPricelistName());

            row.setQuantityOdoo(qtyOdoo.intValue());

            // INFO only (kalau mau ditampilkan di UI atau sekedar disimpan)
            row.setStockPctToInj(stockPctToInj);

            // LIMIT YANG DI-DEFINE DI UI (config, disimpan di kolom stock_pct_to_inj)
            row.setLimitStock(limitStock);

            // SELL STOCK = stok yang boleh ke InJourney
            row.setStock(sellStock);

            row.setBasePrice(stock.getPrice());
            row.setPricingMode(pricingMode);
            row.setAddedValuePct(addedPct);
            row.setMarginInjPct(marginPct);
            row.setSellPrice(sellPrice);

            row.setActive(cfg == null || Boolean.TRUE.equals(cfg.getActive()));

            rows.add(row);
        }

        ProductInjSpecResponse resp = new ProductInjSpecResponse();
        resp.setOdooProductId(product.getOdooProductId());
        resp.setBarcode(product.getBarcode());
        resp.setSku(product.getSku());
        resp.setProductName(product.getName());
        resp.setImageBase64(product.getImageBase64());
        resp.setRows(rows);

        return resp;
    }


    public ProductOdooInjSpecResponse getSpecOdooForProduct(Long odooProductId) {
        CatalogOdooProduct anchor = catalogueOdooProductRepository.findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        Long templateId = anchor.getTemplateId();

        // ========== 2) AMBIL SEMUA VARIAN DALAM TEMPLATE INI ==========

        List<CatalogOdooProduct> allVariants =
                catalogueOdooProductRepository.findByTemplateId(templateId);

        List<VariantOdooInjSpecResponse> variantDtos = new ArrayList<>();

        // ========== 3) LOOP PER VARIAN ==========

        for (CatalogOdooProduct product : allVariants) {

            // --- ambil stok per lokasi untuk varian ini ---
            List<CatalogueOdooProductStock> stockRows =
                    catalogueOdooProductStockRepository.findByProductId(product.getId());

            List<InjSpecOdooRowResponse> rows = new ArrayList<>();

            for (CatalogueOdooProductStock stock : stockRows) {

                // config per varian + lokasi (pakai odooProductId varian ini)
                ProductBundling cfg = productBundlingRepository
                        .findFirstByOdooProductIdAndLocationId(product.getOdooProductId(), stock.getLocationId())
                        .orElse(null);

                // ========== STOCK ==========

                BigDecimal qtyOdoo = stock.getQuantity() != null
                        ? stock.getQuantity()
                        : BigDecimal.ZERO;

                BigDecimal limitStock = BigDecimal.ZERO;
                if (cfg != null && cfg.getStockPctToInj() != null) {
                    limitStock = cfg.getStockPctToInj();   // qty limit, bukan persen
                }
                if (limitStock.compareTo(BigDecimal.ZERO) < 0) {
                    limitStock = BigDecimal.ZERO;
                }

                BigDecimal sellStock = qtyOdoo.subtract(limitStock);
                if (sellStock.compareTo(BigDecimal.ZERO) < 0) {
                    sellStock = BigDecimal.ZERO;
                }
                sellStock = sellStock.setScale(0, RoundingMode.DOWN);

                BigDecimal stockPctToInj = BigDecimal.ZERO;
                if (qtyOdoo.compareTo(BigDecimal.ZERO) > 0 && sellStock.compareTo(BigDecimal.ZERO) > 0) {
                    stockPctToInj = sellStock
                            .multiply(BigDecimal.valueOf(100))
                            .divide(qtyOdoo, 2, RoundingMode.HALF_UP);
                }

                // ========== PRICING ==========

                String pricingMode = (cfg != null && cfg.getPricingMode() != null)
                        ? cfg.getPricingMode()
                        : "NONE";

                BigDecimal addedPct = cfg != null ? nvl(cfg.getAddedValuePct()) : BigDecimal.ZERO;
                BigDecimal marginPct = cfg != null ? nvl(cfg.getMarginInjPct()) : BigDecimal.ZERO;

                BigDecimal sellPrice = computeSellPrice(
                        stock.getPrice(),
                        pricingMode,
                        addedPct,
                        marginPct
                );

                // ========== BANGUN DTO ROW ==========

                InjSpecOdooRowResponse row = new InjSpecOdooRowResponse();
                row.setLocationId(stock.getLocationId());
                row.setLocationName(stock.getLocationName());
                row.setPricelistName(stock.getPricelistName());

                row.setQuantityOdoo(qtyOdoo.intValue());
                row.setLimitStock(limitStock);
                row.setStock(sellStock);
                row.setStockPctToInj(stockPctToInj);

                row.setBasePrice(stock.getPrice());
                row.setPricingMode(pricingMode);
                row.setAddedValuePct(addedPct);
                row.setMarginInjPct(marginPct);
                row.setSellPrice(sellPrice);

                row.setActive(cfg == null || Boolean.TRUE.equals(cfg.getActive()));

                rows.add(row);
            }

            // ========== BANGUN VARIANT DTO UNTUK VARIAN INI ==========

            VariantOdooInjSpecResponse variant = new VariantOdooInjSpecResponse();
            variant.setVariantId(product.getOdooProductId());
            variant.setName(product.getName());
            variant.setBarcode(product.getBarcode());
            variant.setSku(product.getSku());
            variant.setListPrice(product.getListPrice());
            variant.setImage(product.getHasImage());
            variant.setImageBase64(product.getImageBase64());

            // attributes per varian
            List<AttributeResponse> attrs = new ArrayList<>();
            if (product.getAttributes() != null) {
                attrs = product.getAttributes().stream()
                        .map(a -> {
                            AttributeResponse dto = new AttributeResponse();
                            dto.setAttribute(a.getAttributeName());
                            dto.setValue(a.getValue());
                            dto.setDisplayName(a.getDisplayName());
                            return dto;
                        })
                        .toList();
            }
            variant.setAttributes(attrs);

            // rows per lokasi untuk varian ini
            variant.setRows(rows);

            variantDtos.add(variant);
        }

        // ========== 4) BANGUN ROOT RESPONSE (TEMPLATE + VARIANTS[]) ==========

        ProductOdooInjSpecResponse resp = new ProductOdooInjSpecResponse();
        resp.setTemplateId(anchor.getTemplateId());
        resp.setTemplateName(anchor.getTemplateName());
        resp.setImage(anchor.getHasImage());
        resp.setProductType(anchor.getProductType());
        resp.setCategory(anchor.getCategory());
        resp.setBrand(anchor.getBrand());
        resp.setOwnerId(anchor.getOwner());

        // ⬇️ sekarang berisi semua varian (HITAM, BIRU, dst)
        resp.setVariants(variantDtos);

        return resp;


    }


    public List<ProductOdooInjSpecResponse> getAllSpec(String skuFilter) {

        List<CatalogOdooProduct> products;

        if (skuFilter != null && !skuFilter.isBlank()) {
            products = catalogueOdooProductRepository
                    .findBySkuContainingIgnoreCase(skuFilter);
        } else {
            products = catalogueOdooProductRepository.findAll();
        }

        List<ProductOdooInjSpecResponse> result = new ArrayList<>();
        for (CatalogOdooProduct p : products) {
            // pakai method yg sudah ada
            ProductOdooInjSpecResponse spec = getSpecOdooForProduct(p.getOdooProductId());
            result.add(spec);
        }
        return result;
    }


    public List<ProductInjSpecResponse> getAllOdooSpec(String skuFilter) {

        List<CatalogOdooProduct> products;

        if (skuFilter != null && !skuFilter.isBlank()) {
            products = catalogueOdooProductRepository
                    .findBySkuContainingIgnoreCase(skuFilter);
        } else {
            products = catalogueOdooProductRepository.findAll();
        }

        List<ProductInjSpecResponse> result = new ArrayList<>();
        for (CatalogOdooProduct p : products) {
            // pakai method yg sudah ada
            ProductInjSpecResponse spec = getSpecForProduct(p.getOdooProductId());
            result.add(spec);
        }
        return result;
    }

    public Page<ProductInjListRowResponse> getProductInjList(String q,
                                                             Long locationId,
                                                             Pageable pageable) {

        // 1) ambil page produk dulu
        Page<CatalogueProduct> productPage;
        if (q == null || q.isBlank()) {
            productPage = catalogueProductRepository.findAll(pageable);
        } else {
            productPage = catalogueProductRepository
                    .findBySkuContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
        }

        // 2) mapping ke DTO list row
        List<ProductInjListRowResponse> rows = productPage
                .getContent()
                .stream()
                .map(product -> mapToListRow(product, locationId))
                .toList();

        // 3) bungkus lagi sebagai Page
        return new PageImpl<>(
                rows,
                pageable,
                productPage.getTotalElements()
        );
    }

    public Page<ProductOdooInjSpecResponse> getOdooProductInjList(String q,
                                                             Long locationId,
                                                             Pageable pageable) {

        // 1) ambil page produk dulu
        Page<CatalogOdooProduct> productPage;
        if (q == null || q.isBlank()) {
            productPage = catalogueOdooProductRepository.findAll(pageable);
        } else {
            productPage = catalogueOdooProductRepository
                    .findBySkuContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable);
        }

        // 2) mapping ke DTO list row
        List<ProductOdooInjSpecResponse> rows = productPage
                .getContent()
                .stream()
                .map(product -> mapToOdooListRow(product, locationId))
                .toList();

        // 3) bungkus lagi sebagai Page
        return new PageImpl<>(
                rows,
                pageable,
                productPage.getTotalElements()
        );
    }


    private ProductInjListRowResponse mapToListRow(CatalogueProduct product, Long locationId) {

        // stok per lokasi (boleh filter lokasi)
        List<CatalogueProductStock> stocks;
        if (locationId == null) {
            stocks = catalogueProductStockRepository.findByProductId(product.getId());
        } else {
            stocks = catalogueProductStockRepository.findByProductIdAndLocationId(product.getId(), locationId);
        }
        long locationCount = stocks.size();

        // config bundling (INJ) per lokasi
        List<ProductBundling> configs =
                productBundlingRepository.findByOdooProductId(product.getOdooProductId());

        long activeLocationCount = configs.stream()
                .filter(pb -> Boolean.TRUE.equals(pb.getActive()))
                .filter(pb -> locationId == null || locationId.equals(pb.getLocationId()))
                .count();

        // last sync dari inventory_inj
        Instant lastSyncedAt = inventoryInjRepository
                .findFirstByOdooProductIdOrderBySyncedAtDesc(product.getOdooProductId())
                .map(InventoryInj::getSyncedAt)
                .orElse(null);

        return ProductInjListRowResponse.builder()
                .odooProductId(product.getOdooProductId())
                .sku(product.getSku())
                .productName(product.getName())
                .locationCount(locationCount)
                .activeLocationCount(activeLocationCount)
                .lastSyncedAt(lastSyncedAt)
                .build();
    }

    private ProductOdooInjSpecResponse mapToOdooListRow(CatalogOdooProduct product, Long locationId) {


        List<CatalogueOdooProductStock> stocks =
                (locationId == null)
                        ? catalogueOdooProductStockRepository.findByProductId(product.getId())
                        : catalogueOdooProductStockRepository.findByProductIdAndLocationId(product.getId(), locationId);

        List<ProductBundling> configs =
                productBundlingRepository.findByOdooProductId(product.getOdooProductId());

        long locationCount = stocks.size();
        long activeLocationCount = configs.stream()
                .filter(pb -> Boolean.TRUE.equals(pb.getActive()))
                .filter(pb -> locationId == null || locationId.equals(pb.getLocationId()))
                .count();

        Instant lastSyncedAt = inventoryInjRepository
                .findFirstByOdooProductIdOrderBySyncedAtDesc(product.getOdooProductId())
                .map(InventoryInj::getSyncedAt)
                .orElse(null);

        ProductOdooInjSpecResponse resp = new ProductOdooInjSpecResponse();
        resp.setTemplateId(product.getOdooProductId());      // atau product.getTemplateId()
        resp.setTemplateName(product.getName());
        resp.setImage(product.getHasImage() != null && product.getHasImage());
        // resp.setCategory(...);
        // resp.setBrand(...);
        // resp.setOwnerId(...);

        resp.setLocationCount(locationCount);
        resp.setActiveLocationCount(activeLocationCount);
        resp.setLastSyncedAt(lastSyncedAt);

        // di sini kita isi 1 variant saja (kalau suatu saat mau multi variant tinggal tambah list)
        VariantOdooInjSpecResponse variant =
                mapVariantOdoo(product, stocks, configs);
        resp.setVariants(List.of(variant));

        return resp;

    }

    private VariantOdooInjSpecResponse mapVariantOdoo(
            CatalogOdooProduct product,
            List<CatalogueOdooProductStock> stocks,
            List<ProductBundling> configs
    ) {
        VariantOdooInjSpecResponse v = new VariantOdooInjSpecResponse();

        // --- field dasar varian ---
        v.setVariantId(product.getOdooProductId());   // id produk di Odoo
        v.setName(product.getName());
        v.setBarcode(product.getBarcode());
        v.setSku(product.getSku());
        v.setListPrice(product.getListPrice());
        v.setImage(product.getHasImage() != null && product.getHasImage());
        v.setImageBase64(product.getImageBase64());

        // --- attribute (size, warna, dsb) kalau entity-mu ada relasi attributes ---
        if (product.getAttributes() != null) {
            List<AttributeResponse> attrResponses = product.getAttributes().stream()
                    .map(a -> {
                        AttributeResponse ar = new AttributeResponse();
                        ar.setAttribute(a.getAttributeName());
                        ar.setValue(a.getValue());
                        ar.setDisplayName(a.getDisplayName());
                        return ar;
                    })
                    .toList();
            v.setAttributes(attrResponses);
        }

        // --- rows per lokasi (mirip InjSpecRowResponse yang lama) ---
        List<InjSpecOdooRowResponse> rowResponses = new ArrayList<>();

        for (CatalogueOdooProductStock stock : stocks) {

            // cari config INJ untuk lokasi tersebut (boleh pakai map biar lebih cepat)
            ProductBundling cfg = configs.stream()
                    .filter(pb -> Objects.equals(pb.getLocationId(), stock.getLocationId()))
                    .findFirst()
                    .orElse(null);

            BigDecimal qtyOdoo = stock.getQuantity() != null
                    ? stock.getQuantity()
                    : BigDecimal.ZERO;

            // di design baru: stockPctToInj = LIMIT dalam QTY, bukan %
            BigDecimal limitStock = BigDecimal.ZERO;
            if (cfg != null && cfg.getStockPctToInj() != null) {
                limitStock = cfg.getStockPctToInj();   // sudah QTY
            }

            // sell stock = max(qty - limit, 0)
            BigDecimal sellStock = qtyOdoo.subtract(limitStock);
            if (sellStock.signum() < 0) {
                sellStock = BigDecimal.ZERO;
            }



            BigDecimal addedPct  = cfg != null && cfg.getAddedValuePct() != null
                    ? cfg.getAddedValuePct()
                    : BigDecimal.ZERO;

            BigDecimal marginPct = cfg != null && cfg.getMarginInjPct() != null
                    ? cfg.getMarginInjPct()
                    : BigDecimal.ZERO;

            // hitung sell price sama seperti JS


            InjSpecOdooRowResponse row = new InjSpecOdooRowResponse();
            row.setLocationId(stock.getLocationId());
            row.setLocationName(stock.getLocationName());
            row.setPricelistName(stock.getPricelistName());

            row.setLimitStock(limitStock);
            row.setStock(sellStock);
            row.setStockPctToInj(limitStock);          // kirim qty limit ke backend

            row.setAddedValuePct(addedPct);
            row.setMarginInjPct(marginPct);

            row.setActive(cfg == null || Boolean.TRUE.equals(cfg.getActive()));

            rowResponses.add(row);
        }

        v.setRows(rowResponses);

        return v;
    }



    public List<LocationOptionResponse> getAllLocations() {
        // ambil semua stock, lalu distinct by locationId
        List<CatalogueProductStock> stocks = catalogueProductStockRepository.findAll();

        Map<Long, String> map = new LinkedHashMap<>();
        for (CatalogueProductStock s : stocks) {
            Long locId = s.getLocationId();
            String locName = s.getLocationName();
            if (locId != null && !map.containsKey(locId)) {
                map.put(locId, locName);
            }
        }

        // konversi ke list DTO, bisa di-sort kalau mau
        return map.entrySet().stream()
                .map(e -> LocationOptionResponse.builder()
                        .id(e.getKey())
                        .name(e.getValue())
                        .build())
                .sorted(Comparator.comparing(LocationOptionResponse::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public List<LocationOptionResponse> getOdooAllLocations() {
        // ambil semua stock, lalu distinct by locationId
        List<CatalogueOdooProductStock> stocks = catalogueOdooProductStockRepository.findAll();

        Map<Long, String> map = new LinkedHashMap<>();
        for (CatalogueOdooProductStock s : stocks) {
            Long locId = s.getLocationId();
            String locName = s.getLocationName();
            if (locId != null && !map.containsKey(locId)) {
                map.put(locId, locName);
            }
        }

        // konversi ke list DTO, bisa di-sort kalau mau
        return map.entrySet().stream()
                .map(e -> LocationOptionResponse.builder()
                        .id(e.getKey())
                        .name(e.getValue())
                        .build())
                .sorted(Comparator.comparing(LocationOptionResponse::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    // ==== PUT untuk simpan rule dari UI ====
    public void saveSpecForProduct(Long odooProductId, List<InjSpecRowUpdateRequest> rows) {
        CatalogOdooProduct product = catalogueOdooProductRepository
                .findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        // 🔹 Ambil semua config yg sudah ada untuk product ini (sekali query)
        List<ProductBundling> existingCfgs =
                productBundlingRepository.findByOdooProductId(odooProductId);

        Map<Long, ProductBundling> cfgByLocation = existingCfgs.stream()
                .filter(cfg -> cfg.getLocationId() != null)
                .collect(Collectors.toMap(
                        ProductBundling::getLocationId,
                        Function.identity()
                ));

        // untuk tracking lokasi yang dikirim dari UI
        Set<Long> incomingLocationIds = new HashSet<>();

        for (InjSpecRowUpdateRequest r : rows) {

            if (r.getLocationId() == null) {
                // kalau tidak ada locationId, skip saja
                continue;
            }

            incomingLocationIds.add(r.getLocationId());

            // 🔹 cari config existing untuk (product, locationId) ini
            ProductBundling cfg = cfgByLocation.get(r.getLocationId());
            if (cfg == null) {
                cfg = new ProductBundling();
            }

            cfg.setOdooProductId(odooProductId);
            cfg.setSku(product.getSku());
            cfg.setProductName(product.getName());
            cfg.setLocationId(r.getLocationId());

            // ========== PRICING MODE ==========
            String mode = Optional.ofNullable(r.getPricingMode())
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .orElse("NONE");
            cfg.setPricingMode(mode);

            cfg.setAddedValuePct(
                    Optional.ofNullable(r.getAddedValuePct()).orElse(BigDecimal.ZERO)
            );
            cfg.setMarginInjPct(
                    Optional.ofNullable(r.getMarginInjPct()).orElse(BigDecimal.ZERO)
            );

            // ========== LIMIT STOCK (pakai qty, bukan persen) ==========
            BigDecimal limitStock = Optional.ofNullable(r.getStockPctToInj())
                    .orElse(BigDecimal.ZERO);

            if (limitStock.compareTo(BigDecimal.ZERO) < 0) {
                limitStock = BigDecimal.ZERO;
            }

            // ⬇ kolom stock_pct_to_inj = LIMIT STOCK QTY
            cfg.setStockPctToInj(limitStock);

            cfg.setActive(Optional.ofNullable(r.getActive()).orElse(Boolean.TRUE));

            productBundlingRepository.save(cfg);

            // update map (kalau barusan new)
            cfgByLocation.put(cfg.getLocationId(), cfg);
        }

        // OPTIONAL: matikan config yg tidak lagi muncul di UI
        cfgByLocation.values().stream()
                .filter(cfg -> cfg.getLocationId() != null
                        && !incomingLocationIds.contains(cfg.getLocationId()))
                .forEach(cfg -> {
                    cfg.setActive(false);
                    productBundlingRepository.save(cfg);
                });
        }




    public void saveSpecForOdooProduct(Long odooProductId, List<InjSpecRowUpdateRequest> rows) {
        CatalogOdooProduct product = catalogueOdooProductRepository.findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        // 🔹 Ambil semua config existing untuk produk ini, mapping per locationId
        List<ProductBundling> existingList =
                productBundlingRepository.findByOdooProductId(odooProductId);

        Map<Long, ProductBundling> cfgMap = existingList.stream()
                .filter(cfg -> cfg.getLocationId() != null)
                .collect(Collectors.toMap(
                        ProductBundling::getLocationId,
                        Function.identity(),
                        (a, b) -> b // kalau duplikat locationId, pakai yang terakhir
                ));

        List<ProductBundling> toSave = new ArrayList<>();

        for (InjSpecRowUpdateRequest r : rows) {

            if (r.getLocationId() == null) {
                // kalau dari UI ada baris tanpa locationId, skip saja biar aman
                continue;
            }

            // 🔹 Ambil config existing utk lokasi ini, atau buat baru
            ProductBundling cfg = cfgMap.get(r.getLocationId());
            if (cfg == null) {
                cfg = new ProductBundling();
                cfg.setOdooProductId(odooProductId);
                cfg.setLocationId(r.getLocationId());
                cfgMap.put(r.getLocationId(), cfg);
            }

            // ========== INFO PRODUK (biar selalu update) ==========
            cfg.setSku(product.getSku());
            cfg.setProductName(product.getName());

            // ========== PRICING MODE ==========
            String mode = (r.getPricingMode() == null || r.getPricingMode().isBlank())
                    ? "NONE"
                    : r.getPricingMode();
            cfg.setPricingMode(mode);

            cfg.setAddedValuePct(
                    r.getAddedValuePct() != null ? r.getAddedValuePct() : BigDecimal.ZERO
            );
            cfg.setMarginInjPct(
                    r.getMarginInjPct() != null ? r.getMarginInjPct() : BigDecimal.ZERO
            );

            // ========== LIMIT STOCK (pakai field stockPctToInj sebagai LIMIT QTY) ==========
            BigDecimal limitStock = r.getStockPctToInj() != null
                    ? r.getStockPctToInj()
                    : BigDecimal.ZERO;

            if (limitStock.compareTo(BigDecimal.ZERO) < 0) {
                limitStock = BigDecimal.ZERO;
            }

            // ⬇ sekarang kolom stock_pct_to_inj = LIMIT STOCK (qty), BUKAN PERSEN
            cfg.setStockPctToInj(limitStock);

            // ========== ACTIVE ==========
            cfg.setActive(r.getActive() != null ? r.getActive() : Boolean.TRUE);

            toSave.add(cfg);
        }

        // 🔹 Simpan semua sekaligus (update / insert per lokasi)
        productBundlingRepository.saveAll(toSave);




    }

    // ========= helper =========

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal computeLimitStock(BigDecimal qty, BigDecimal stockPctToInj) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (stockPctToInj == null) {
            // kalau persen null → anggap 100%
            return qty.setScale(0, RoundingMode.FLOOR);
        }

        BigDecimal ratio = stockPctToInj
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        // floor ke bilangan bulat
        return qty.multiply(ratio).setScale(0, RoundingMode.FLOOR);
    }

    /**
     * pricingMode:
     * - "NONE"         → sell = base
     * - "ADDED_VALUE"  → sell = base + base * add/100
     * - "MARGIN"       → sell = base / (1 - margin/100)
     * Kalau add & margin dua2nya 0 → sell = base
     */
    private BigDecimal computeSellPrice(BigDecimal base,
                                        String mode,
                                        BigDecimal addPct,
                                        BigDecimal marginPct) {
        if (base == null) return BigDecimal.ZERO;

        BigDecimal add = addPct != null ? addPct : BigDecimal.ZERO;
        BigDecimal margin = marginPct != null ? marginPct : BigDecimal.ZERO;

        boolean noAdd = add.compareTo(BigDecimal.ZERO) == 0;
        boolean noMargin = margin.compareTo(BigDecimal.ZERO) == 0;

        if ("NONE".equalsIgnoreCase(mode)) {
            return base.setScale(0, RoundingMode.HALF_UP);
        }

        if (noAdd && noMargin) {
            return base.setScale(0, RoundingMode.HALF_UP);
        }

        if ("MARGIN".equalsIgnoreCase(mode) && !noMargin) {
            BigDecimal m = margin.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return base.divide(BigDecimal.ONE.subtract(m), 0, RoundingMode.HALF_UP);
        }

        if ("ADDED_VALUE".equalsIgnoreCase(mode) && !noAdd) {
            BigDecimal a = add.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return base.add(base.multiply(a)).setScale(0, RoundingMode.HALF_UP);
        }

        return base.setScale(0, RoundingMode.HALF_UP);
    }
}

