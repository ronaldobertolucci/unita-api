package io.github.ronaldobertolucci.unita.dto.recurring;
import java.util.List;
public record RecurringBillPageDto(List<RecurringBillDto> content,int page,int size,long totalElements,int totalPages) {}
