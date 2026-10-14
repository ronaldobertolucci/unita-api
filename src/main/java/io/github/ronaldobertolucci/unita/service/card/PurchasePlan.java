package io.github.ronaldobertolucci.unita.service.card;

import io.github.ronaldobertolucci.unita.dto.card.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Validates direct service calls too, before any plan persistence or bill resolution. */
final class PurchasePlan {
    private PurchasePlan() {}

    static List<PurchaseInstallmentPlanDto> validate(CreditCardPurchaseCreateDto dto) {
        if (dto == null) throw new IllegalArgumentException("purchase: required");
        if (dto.description() == null || dto.description().isBlank() || dto.description().length() > 255)
            throw new IllegalArgumentException("description: required, maximum 255 characters");
        money(dto.totalValue(), "totalValue");
        if (dto.purchaseDate() == null) throw new IllegalArgumentException("purchaseDate: required");
        if (dto.installmentsCount() == null || dto.installmentsCount() < 1 || dto.installmentsCount() > 120)
            throw new IllegalArgumentException("installmentsCount: must be between 1 and 120");
        if (dto.categoryId() == null || dto.categoryId() <= 0) throw new IllegalArgumentException("categoryId: required positive ID");
        int count = dto.installmentsCount();
        try { dto.purchaseDate().plusMonths(count - 1); }
        catch (DateTimeException ex) { throw new IllegalArgumentException("purchaseDate: installment dates out of range"); }
        if (dto.installmentMode() == null || dto.installmentMode() == InstallmentPlanMode.AUTOMATIC) {
            if (dto.installments() != null) throw new IllegalArgumentException("installments: must be omitted in AUTOMATIC mode");
            BigDecimal base = dto.totalValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
            if (base.signum() == 0) throw new IllegalArgumentException("installmentsCount: would generate a zero installment");
            List<PurchaseInstallmentPlanDto> plan = new ArrayList<>();
            for (int number = 1; number <= count; number++) {
                BigDecimal amount = number == count ? dto.totalValue().subtract(base.multiply(BigDecimal.valueOf(count - 1))) : base;
                plan.add(new PurchaseInstallmentPlanDto(number, amount, dto.categoryId()));
            }
            return List.copyOf(plan);
        }
        if (dto.installments() == null || dto.installments().size() != count)
            throw new IllegalArgumentException("installments: count must equal installmentsCount");
        if (dto.installments().stream().anyMatch(i -> i == null || i.installmentNumber() == null))
            throw new IllegalArgumentException("installments: every entry requires installmentNumber");
        var sorted = dto.installments().stream().sorted(Comparator.comparing(PurchaseInstallmentPlanDto::installmentNumber)).toList();
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < count; index++) {
            var entry = sorted.get(index);
            if (entry.installmentNumber() != index + 1)
                throw new IllegalArgumentException("installments: numbers must be sequential from 1 to installmentsCount without duplicates");
            money(entry.amount(), "installments[" + index + "].amount");
            if (entry.categoryId() != null && entry.categoryId() <= 0)
                throw new IllegalArgumentException("installments[" + index + "].categoryId: must be positive");
            sum = sum.add(entry.amount());
        }
        if (sum.compareTo(dto.totalValue()) != 0) throw new IllegalArgumentException("installments: sum must equal totalValue");
        return sorted;
    }

    private static void money(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0 || value.scale() > 2 || value.compareTo(new BigDecimal("9999999999999.99")) > 0)
            throw new IllegalArgumentException(field + ": must be positive with at most 13 integer digits and 2 decimal places");
    }
}
