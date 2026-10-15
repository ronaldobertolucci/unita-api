package io.github.ronaldobertolucci.unita.dto.legal;

import io.github.ronaldobertolucci.unita.validation.Cnpj;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Private institution of the authenticated user. CNPJ is normalized on input; no check-digit validation.")
public record LegalEntityCreateDto(
        @NotBlank @Pattern(regexp = Cnpj.INPUT_PATTERN, message = "must contain 12 letters/digits and 2 final digits, optionally with the complete CNPJ mask")
        @Schema(description = "Numeric or alphanumeric CNPJ. Accepts canonical text or XX.XXX.XXX/XXXX-XX, including lowercase. Stored uppercase without mask, preserving leading zeros.", example = "00.000.000/E08G-12", maxLength = 18)
        String cnpj,
        @NotBlank @Size(max = 255) String corporateName,
        @Size(max = 255) String tradeName,
        @Size(max = 50) String stateRegistration
) {
    public LegalEntityCreateDto {
        cnpj = Cnpj.normalize(cnpj);
    }
}
