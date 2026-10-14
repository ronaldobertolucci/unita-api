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

    @io.swagger.v3.oas.annotations.Operation(summary = "Transferir com vínculo explícito", description = "Cria os dois lançamentos e o vínculo em uma transação; Serializa saldo e gravação sobre as contas em ordem de ID; não oferece cancelamento nem idempotência de requisição.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Concurrency Timeout: espera por bloqueio excedida; consultar estado antes de repetir escrita")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrency Conflict: transação desfeita; atualizar dados")
    @PostMapping
    public ResponseEntity<TransferDto> transfer(
            @RequestBody @Valid TransferCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.transfer(dto, authentication));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Sacar FGTS com vínculo explícito", description = "Cria os dois lançamentos e o vínculo FGTS_WITHDRAWAL atomicamente; exclusão individual é bloqueada; validação do saldo e gravação são serializadas.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Concurrency Timeout: espera por bloqueio excedida; consultar estado antes de repetir escrita")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrency Conflict: transação desfeita; atualizar dados")
    @PostMapping("/fgts/withdrawal")
    public ResponseEntity<TransferDto> fgtsWithdrawal(
            @RequestBody @Valid TransferCreateDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.fgtsWithdrawal(dto, authentication));
    }
}