// dto/dashboard/GroupMonthlyDto.java
package io.github.ronaldobertolucci.unita.dto.dashboard;

import java.util.List;

public record GroupMonthlyDto(List<GroupMemberMonthlyDto> members) {}