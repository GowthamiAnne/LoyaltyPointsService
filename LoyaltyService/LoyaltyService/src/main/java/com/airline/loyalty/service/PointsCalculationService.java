package com.airline.loyalty.service;


import com.airline.loyalty.exception.ValidationException;
import com.airline.loyalty.model.*;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PointsCalculationService is responsible for calculating and managing
 * point-based values according to defined business rules.
 *
 */
public class PointsCalculationService {
    private static final Logger logger = LoggerFactory.getLogger(PointsCalculationService.class);
    
    private final FxServiceClient fxService;
    private final PromoServiceClient promoService;
    private final int maxPoints;
    private final int expiryWarningDays;
    private final String baseCurrency;

    public PointsCalculationService(FxServiceClient fxService, PromoServiceClient promoService, JsonObject config) {
        this.fxService = fxService;
        this.promoService = promoService;
        JsonObject businessConfig = config.getJsonObject("business");
        JsonObject currencyConfig = config.getJsonObject("currency");
        this.maxPoints = businessConfig.getInteger("maxPoints");
        this.expiryWarningDays = businessConfig.getInteger("expiryWarningDays");
        this.baseCurrency = currencyConfig.getString("base");
    }

    public Future<PointsCalculation> calculatePoints(PointsQuoteRequest request) {
        try {
            validateRequest(request);
        } catch (ValidationException e) {
            return Future.failedFuture(e);
        }

        return convertToBaseCurrency(request.getFareAmount(), request.getCurrency())
            .compose(convertedAmount -> {
                int basePoints = (int) Math.floor(convertedAmount);
                double fxRate = convertedAmount / request.getFareAmount();
                
                return calculateTierBonus(basePoints, request.getCustomerTier())
                    .compose(tierBonus -> calculatePromoBonus(basePoints, request.getPromoCode())
                        .map(promoResult -> {
                            int totalBeforeCap = basePoints + tierBonus + promoResult.bonus();
                            int finalTotal = Math.min(totalBeforeCap, maxPoints);
                            
                            List<String> warnings = new ArrayList<>(promoResult.warnings());
                            if (finalTotal < totalBeforeCap) {
                                warnings.add("POINTS_CAPPED_AT_MAX");
                                logger.info("Points capped: {} -> {}", totalBeforeCap, finalTotal);
                            }

                            return PointsCalculation.builder()
                                .basePoints(basePoints)
                                .tierBonus(tierBonus)
                                .promoBonus(promoResult.bonus())
                                .totalPoints(finalTotal)
                                .effectiveFxRate(Math.round(fxRate * 100.0) / 100.0)
                                .warnings(warnings)
                                .build();
                        })
                    );
            });
    }

    private void validateRequest(PointsQuoteRequest request) {
        if (request.getFareAmount() == null || request.getFareAmount() <= 0) {
            throw new ValidationException("Fare amount must be greater than zero");
        }
        
        if (request.getCurrency() == null || request.getCurrency().length() != 3) {
            throw new ValidationException("Invalid currency code");
        }

        try {
            CabinClass.valueOf(request.getCabinClass());
        } catch (Exception e) {
            throw new ValidationException("Invalid cabin class: " + request.getCabinClass());
        }

        try {
            CustomerTier.valueOf(request.getCustomerTier());
        } catch (Exception e) {
            throw new ValidationException("Invalid customer tier: " + request.getCustomerTier());
        }
    }

    private Future<Double> convertToBaseCurrency(double amount, String currency) {
        if (baseCurrency.equals(currency)) {
            return Future.succeededFuture(amount);
        }

        return fxService.getExchangeRate(currency, baseCurrency)
            .map(rate -> amount * rate);
    }

    private Future<Integer> calculateTierBonus(int basePoints, String tierName) {
        CustomerTier tier = CustomerTier.valueOf(tierName);
        int bonus = (int) Math.floor(basePoints * tier.getMultiplier());
        logger.debug("Tier bonus calculated: {} * {} = {}", basePoints, tier.getMultiplier(), bonus);
        return Future.succeededFuture(bonus);
    }

    private Future<PromoResult> calculatePromoBonus(int basePoints, String promoCode) {
        return promoService.getPromoDetails(promoCode)
            .map(promoOpt -> {
                if (promoOpt.isEmpty()) {
                    return new PromoResult(0, List.of());
                }

                PromoResponse promo = promoOpt.get();
                if (!promo.active()) {
                    logger.info("Promo {} is inactive", promoCode);
                    return new PromoResult(0, List.of("PROMO_INACTIVE"));
                }

                int bonus = (int) Math.floor(basePoints * promo.bonusMultiplier());
                List<String> warnings = new ArrayList<>();

                if (promo.expiryDate() != null) {
                    LocalDate expiryDate = LocalDate.parse(promo.expiryDate(), DateTimeFormatter.ISO_DATE);
                    long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
                    
                    if (daysUntilExpiry <= expiryWarningDays && daysUntilExpiry > 0) {
                        warnings.add("PROMO_EXPIRES_SOON");
                        logger.info("Promo {} expires in {} days", promoCode, daysUntilExpiry);
                    } else if (daysUntilExpiry <= 0) {
                        warnings.add("PROMO_EXPIRED");
                        logger.info("Promo {} has expired", promoCode);
                        return new PromoResult(0, warnings);
                    }
                }

                logger.debug("Promo bonus calculated: {} * {} = {}", basePoints, promo.bonusMultiplier(), bonus);
                return new PromoResult(bonus, warnings);
            });
    }

    private record PromoResult(int bonus, List<String> warnings) {}
}
