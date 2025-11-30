package com.sarinah.product_bundling.service;

import com.sarinah.product_bundling.model.entity.CatalogueProduct;
import com.sarinah.product_bundling.model.entity.CatalogueProductStock;
import com.sarinah.product_bundling.model.entity.InventoryInj;
import com.sarinah.product_bundling.model.entity.ProductBundling;
import com.sarinah.product_bundling.repository.CatalogueProductRepository;
import com.sarinah.product_bundling.repository.CatalogueProductStockRepository;
import com.sarinah.product_bundling.repository.InventoryInjRepository;
import com.sarinah.product_bundling.repository.ProductBundlingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryInjService {

    private final CatalogueProductRepository catalogueProductRepository;
    private final CatalogueProductStockRepository catalogueProductStockRepository;
    private final ProductBundlingRepository productBundlingRepository;
    private final InventoryInjRepository inventoryInjRepository;

    public List<InventoryInj> syncProduct(Long odooProductId) {

        CatalogueProduct product = catalogueProductRepository.findByOdooProductId(odooProductId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + odooProductId));

        List<CatalogueProductStock> stockRows =
                catalogueProductStockRepository.findByProductId(product.getId());

        List<InventoryInj> result = new ArrayList<>();

        for (CatalogueProductStock stock : stockRows) {

            ProductBundling cfg = productBundlingRepository
                    .findFirstByOdooProductIdAndLocationId(odooProductId, stock.getLocationId())
                    .orElse(null);

            if (cfg != null && Boolean.FALSE.equals(cfg.getActive())) {
                continue;
            }

            BigDecimal qtyOdoo  = stock.getQuantity();                          // BD
            BigDecimal stockPct = cfg != null && cfg.getStockPctToInj() != null
                    ? cfg.getStockPctToInj()
                    : BigDecimal.valueOf(100);

            BigDecimal limitStock = computeLimitStock(qtyOdoo, stockPct);      // BD

            String pricingMode = (cfg != null && cfg.getPricingMode() != null)
                    ? cfg.getPricingMode()
                    : "NONE";

            BigDecimal addedPct  = cfg != null ? nvl(cfg.getAddedValuePct()) : BigDecimal.ZERO;
            BigDecimal marginPct = cfg != null ? nvl(cfg.getMarginInjPct()) : BigDecimal.ZERO;

            BigDecimal sellPrice = computeSellPrice(
                    stock.getPrice(),          // basePrice BigDecimal
                    pricingMode,
                    addedPct,
                    marginPct
            );

            InventoryInj inv = inventoryInjRepository
                    .findFirstByOdooProductIdAndLocationId(odooProductId, stock.getLocationId())
                    .orElseGet(InventoryInj::new);

            inv.setOdooProductId(odooProductId);
            inv.setSku(product.getSku());
            inv.setProductName(product.getName());
            inv.setLocationId(stock.getLocationId());
            inv.setLocationName(stock.getLocationName());
            inv.setPricelistName(stock.getPricelistName());

            inv.setBasePrice(stock.getPrice());
            inv.setPricingMode(pricingMode);
            inv.setAddedValuePct(addedPct);
            inv.setMarginInjPct(marginPct);
            inv.setStockPctToInj(stockPct);

            inv.setQtyOdoo(qtyOdoo);           // BigDecimal
            inv.setLimitStock(limitStock);     // BigDecimal
            inv.setSyncedAt(Instant.now());    // atau OffsetDateTime.now()

            inventoryInjRepository.save(inv);
            result.add(inv);
        }

        return result;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private BigDecimal computeLimitStock(BigDecimal qty, BigDecimal stockPctToInj) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = (stockPctToInj != null) ? stockPctToInj : BigDecimal.valueOf(100);
        BigDecimal ratio = pct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return qty.multiply(ratio).setScale(0, RoundingMode.FLOOR);
    }

    private BigDecimal computeSellPrice(BigDecimal base,
                                        String mode,
                                        BigDecimal addPct,
                                        BigDecimal marginPct) {
        if (base == null) return BigDecimal.ZERO;

        BigDecimal add = addPct != null ? addPct : BigDecimal.ZERO;
        BigDecimal margin = marginPct != null ? marginPct : BigDecimal.ZERO;

        boolean noAdd = add.compareTo(BigDecimal.ZERO) == 0;
        boolean noMargin = margin.compareTo(BigDecimal.ZERO) == 0;

        if ("NONE".equalsIgnoreCase(mode) || (noAdd && noMargin)) {
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