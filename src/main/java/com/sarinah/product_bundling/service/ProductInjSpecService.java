package com.sarinah.product_bundling.service;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import com.sarinah.product_bundling.model.entity.CatalogueProductStock;
import com.sarinah.product_bundling.model.entity.InventoryInj;
import com.sarinah.product_bundling.model.entity.ProductBundling;
import com.sarinah.product_bundling.model.request.InjSpecRowUpdateRequest;
import com.sarinah.product_bundling.model.response.InjSpecRowResponse;
import com.sarinah.product_bundling.model.response.LocationOptionResponse;
import com.sarinah.product_bundling.model.response.ProductInjListRowResponse;
import com.sarinah.product_bundling.model.response.ProductInjSpecResponse;
import com.sarinah.product_bundling.repository.CatalogueProductRepository;
import com.sarinah.product_bundling.repository.CatalogueProductStockRepository;
import com.sarinah.product_bundling.repository.InventoryInjRepository;
import com.sarinah.product_bundling.repository.ProductBundlingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductInjSpecService {
    private final CatalogueProductRepository catalogueProductRepository;
    private final CatalogueProductStockRepository catalogueProductStockRepository;
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

    public List<ProductInjSpecResponse> getAllSpec(String skuFilter) {

        List<CatalogueProduct> products;

        if (skuFilter != null && !skuFilter.isBlank()) {
            products = catalogueProductRepository
                    .findBySkuContainingIgnoreCase(skuFilter);
        } else {
            products = catalogueProductRepository.findAll();
        }

        List<ProductInjSpecResponse> result = new ArrayList<>();
        for (CatalogueProduct p : products) {
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

    // ==== PUT untuk simpan rule dari UI ====
    public void saveSpecForProduct(Long odooProductId, List<InjSpecRowUpdateRequest> rows) {
        CatalogueProduct product = catalogueProductRepository.findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        for (InjSpecRowUpdateRequest r : rows) {

            ProductBundling cfg = productBundlingRepository
                    .findFirstByOdooProductIdAndLocationId(odooProductId, r.getLocationId())
                    .orElseGet(ProductBundling::new);

            cfg.setOdooProductId(odooProductId);
            cfg.setSku(product.getSku());
            cfg.setProductName(product.getName());
            cfg.setLocationId(r.getLocationId());
            // kalau mau simpan pricelistName juga:
            // CatalogueProductStock s = catalogueProductStockRepository
            //      .findFirstByProductIdAndLocationId(product.getId(), r.getLocationId());
            // cfg.setPricelistName(s.getPricelistName());

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

            // ========== LIMIT STOCK (PAKAI FIELD stockPctToInj SEBAGAI LIMIT QTY) ==========
            BigDecimal limitStock = r.getStockPctToInj() != null
                    ? r.getStockPctToInj()
                    : BigDecimal.ZERO;

            if (limitStock.compareTo(BigDecimal.ZERO) < 0) {
                limitStock = BigDecimal.ZERO;
            }

            // ⬇ sekarang kolom stock_pct_to_inj = LIMIT STOCK, BUKAN PERSEN
            cfg.setStockPctToInj(limitStock);

            cfg.setActive(r.getActive() != null ? r.getActive() : Boolean.TRUE);

            productBundlingRepository.save(cfg);
        }



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

