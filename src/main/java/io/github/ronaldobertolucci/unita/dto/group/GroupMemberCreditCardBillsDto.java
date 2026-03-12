package io.github.ronaldobertolucci.unita.dto.group;

import java.util.List;

public record GroupMemberCreditCardBillsDto(
    String memberName,
    List<GroupCreditCardBillDto> bills
) {}