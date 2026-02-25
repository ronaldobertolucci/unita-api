package io.github.ronaldobertolucci.unita.controller;

import io.github.ronaldobertolucci.unita.dto.pocket.TransferCreateDto;
import io.github.ronaldobertolucci.unita.dto.pocket.TransferDto;
import io.github.ronaldobertolucci.unita.service.pocket.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferDto> transfer(
            @RequestBody @Valid TransferCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.transfer(dto, authentication));
    }
}