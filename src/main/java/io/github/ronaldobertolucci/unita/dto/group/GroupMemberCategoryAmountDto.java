package io.github.ronaldobertolucci.unita.dto.group;

import java.util.List;

public record GroupMemberCategoryAmountDto(
    String memberName,
    List<CategoryAmountDto> categories
) {}