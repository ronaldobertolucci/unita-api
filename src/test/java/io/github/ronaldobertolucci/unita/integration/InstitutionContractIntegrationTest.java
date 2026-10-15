package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.investment.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InstitutionContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired LegalEntityRepository entities;
    @Autowired AssetRepository assets;
    private User owner, other;

    @BeforeEach void setup() {
        owner = newUser(); other = newUser();
    }
    private User newUser() {
        return users.save(User.builder().firstName("Institution").lastName("Test").email(UUID.randomUUID()+"@example.invalid")
                .password("encoded").dateOfBirth(LocalDate.of(1990,1,1)).enabled(true).emailVerified(true).build());
    }
    private String body(String cnpj) { return "{\"cnpj\":\""+cnpj+"\",\"corporateName\":\"Institution\",\"tradeName\":\"Name\",\"stateRegistration\":null}"; }
    private LegalEntity institution(User user, String cnpj) {
        return entities.save(LegalEntity.builder().user(user).cnpj(cnpj).corporateName("Historical").build());
    }

    @ParameterizedTest
    @ValueSource(strings={"00000000E08G12","00.000.000/e08g-12","00000000000100","00.000.000/0001-00"})
    void normalizesAndPreservesLeadingZerosForCreateAndPatch(String cnpj) throws Exception {
        String expected = cnpj.replace(".","").replace("/","").replace("-","").toUpperCase(java.util.Locale.ROOT);
        mvc.perform(post("/api/legal-entities").contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body(cnpj)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cnpj").value(expected)).andExpect(jsonPath("$.id").isNumber());
        var entity = entities.findAllByUserIdOrderByCorporateName(owner.getId()).getFirst();
        mvc.perform(patch("/api/legal-entities/"+entity.getId()).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body(cnpj)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cnpj").value(expected));
    }

    @ParameterizedTest
    @ValueSource(strings={"00000000E08G1A","00000000E08G1","00000000E08G123","00_000_000/E08G-12","00.000000/E08G-12"," 00000000E08G12","00000000É08G12"})
    void rejectsInvalidInputWithFieldErrors(String cnpj) throws Exception {
        mvc.perform(post("/api/legal-entities").contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body(cnpj)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cnpj"));
        assertTrue(entities.findAllByUserIdOrderByCorporateName(owner.getId()).isEmpty());
    }

    @Test void sameCnpjIsPrivateAndDuplicateWithinOwnerIs400IncludingHistoricalLowercase() throws Exception {
        var historical = institution(owner,"00000000e08g12");
        mvc.perform(post("/api/legal-entities").contextPath("/api").with(user(other)).contentType(MediaType.APPLICATION_JSON).content(body("00.000.000/E08G-12")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/legal-entities").contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body("00000000E08G12")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A legal entity with this CNPJ already exists in your registrations"));
        var another = institution(owner,"12345678000190");
        mvc.perform(patch("/api/legal-entities/"+another.getId()).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body("00000000E08G12")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/legal-entities/"+historical.getId()).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body("00000000E08G12")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cnpj").value("00000000E08G12"));
    }

    @Test void historicalInvalidTextRemainsReadableAndRequiresExplicitCorrectionOnEdit() throws Exception {
        var historical = institution(owner,"invalid");
        mvc.perform(get("/api/legal-entities/"+historical.getId()).contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cnpj").value("invalid"));
        mvc.perform(patch("/api/legal-entities/"+historical.getId()).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body("invalid")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/legal-entities").contextPath("/api").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cnpj").value("invalid"));
    }

    @Test void foreignIdsAre404ForReadPatchAndDeleteAndNeverListed() throws Exception {
        var foreign = institution(other,"00000000E08G12");
        String path="/api/legal-entities/"+foreign.getId();
        mvc.perform(get(path).contextPath("/api").with(user(owner))).andExpect(status().isNotFound());
        mvc.perform(patch(path).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(body("00000000E08G12"))).andExpect(status().isNotFound());
        mvc.perform(delete(path).contextPath("/api").with(user(owner))).andExpect(status().isNotFound());
        mvc.perform(get("/api/legal-entities").contextPath("/api").with(user(owner))).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void issuerAndCustodianAreProtectedWithoutDeletingAssetsOrHistory(boolean custodian) throws Exception {
        var issuer=institution(owner,"12345678000190"); var custody=institution(owner,"00000000E08G12");
        var asset=assets.save(Asset.builder().user(owner).legalEntity(issuer).custodianLegalEntity(custody).name("Protected").category(AssetCategory.PREVIDENCIA).status(AssetStatus.REDEEMED).build());
        em.createNativeQuery("INSERT INTO investment_transactions(asset_id,type,amount,transaction_date,notes) VALUES(:id,'BUY',100,DATE '2026-01-01','Preserve history')").setParameter("id",asset.getId()).executeUpdate();
        mvc.perform(delete("/api/legal-entities/"+(custodian?custody:issuer).getId()).contextPath("/api").with(user(owner)))
                .andExpect(status().isConflict());
        em.flush();em.clear();
        assertTrue(assets.existsById(asset.getId()));
        assertEquals(1L,((Number)em.createNativeQuery("SELECT count(*) FROM investment_transactions WHERE asset_id=:id").setParameter("id",asset.getId()).getSingleResult()).longValue());
    }

    @ParameterizedTest @ValueSource(strings={"fixed-income","pension","update"})
    void assetEndpointsRejectForeignIssuerAndCustodian(String operation) throws Exception {
        var mine=institution(owner,"12345678000190"); var foreign=institution(other,"00000000E08G12");
        for (boolean issuer : new boolean[]{true,false}) {
            long issuerId=(issuer?foreign:mine).getId(), custodyId=(issuer?mine:foreign).getId();
            String json="{\"name\":\"Investment\",\"legalEntityId\":"+issuerId+",\"custodianLegalEntityId\":"+custodyId+",\"pensionType\":\"VGBL\",\"taxRegime\":\"REGRESSIVO\",\"indexer\":\"CDI\",\"annualRate\":1.1,\"maturityDate\":\"2030-01-01\",\"taxFree\":false,\"liquidityType\":\"DIARIA\"}";
            if (operation.equals("update")) {
                var asset=assets.save(Asset.builder().user(owner).legalEntity(mine).name(UUID.randomUUID().toString()).category(AssetCategory.PREVIDENCIA).build());
                mvc.perform(put("/api/assets/"+asset.getId()).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
            } else mvc.perform(post("/api/assets/"+operation).contextPath("/api").with(user(owner)).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNotFound());
        }
    }

    @Test void openApiKeepsInstitutionContractAndDocumentedErrors() throws Exception {
        mvc.perform(get("/api/v3/api-docs").contextPath("/api")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/legal-entities'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/legal-entities/{id}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/legal-entities/{id}'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.LegalEntityCreateDto.properties.cnpj.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.LegalEntityCreateDto.properties.cnpj.description").value(org.hamcrest.Matchers.containsString("Stored uppercase")))
                .andExpect(jsonPath("$.components.schemas.LegalEntityDto.properties.id.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.LegalEntityCreateDto.properties.cnpj.pattern").value(org.hamcrest.Matchers.containsString("A-Za-z")))
                .andExpect(jsonPath("$.components.schemas.LegalEntityUpdateDto.properties.cnpj.pattern").value(org.hamcrest.Matchers.containsString("A-Za-z")))
                .andExpect(jsonPath("$.components.schemas.LegalEntityCreateDto.properties.cnpj.maxLength").value(18));
    }
}
