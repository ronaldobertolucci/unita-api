package io.github.ronaldobertolucci.unita.controller;
import io.github.ronaldobertolucci.unita.dto.recurring.*;
import io.github.ronaldobertolucci.unita.service.recurring.RecurringBillService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/recurring-bills") @RequiredArgsConstructor
@io.swagger.v3.oas.annotations.responses.ApiResponses({
 @ApiResponse(responseCode="400",description="Invalid fields, money precision, date or query"),
 @ApiResponse(responseCode="401",description="Authentication required"),
 @ApiResponse(responseCode="403",description="Account access blocked")
})
@Tag(name="Recurring bills",description="Predictions only; no payment or financial transactions")
public class RecurringBillController {
 private final RecurringBillService service;
 @PostMapping @Operation(summary="Create a personal recurring bill") @ApiResponse(responseCode="201",description="Created")
 public ResponseEntity<RecurringBillDto> create(@Valid @RequestBody RecurringBillCreateDto dto,Authentication auth) { return ResponseEntity.status(201).body(service.create(dto,auth)); }
 @GetMapping @Operation(summary="List personal recurring bills, including paused ones")
 public RecurringBillPageDto list(Authentication auth,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.list(auth,active,page,size); }
 @GetMapping("/upcoming") @Operation(summary="Project active occurrences for a calendar month",description="month=YYYY-MM; due date then description order. Expected amounts only.")
 public List<RecurringOccurrenceDto> upcoming(Authentication auth,@RequestParam String month) { return service.upcoming(auth,month); }
 @GetMapping("/{id}") @ApiResponse(responseCode="404",description="Unavailable personal resource")
 public RecurringBillDto detail(@PathVariable Long id,Authentication auth) { return service.detail(id,auth); }
 @PatchMapping("/{id}") @Operation(summary="Replace editable fields with version check",description="Complete editable representation required; null amount/notes clears them. No financial effects.")
 @ApiResponse(responseCode="409",description="Version conflict or frequency change after sending started")
 public RecurringBillDto update(@PathVariable Long id,@Valid @RequestBody RecurringBillUpdateDto dto,Authentication auth) { return service.update(id,dto,auth); }
}
