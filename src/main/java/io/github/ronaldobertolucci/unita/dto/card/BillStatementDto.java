package io.github.ronaldobertolucci.unita.dto.card;

import java.util.List;

public record BillStatementDto(
    List<BillInstallmentDto> installments,
    List<CreditCardRefundDto> refunds
) {}