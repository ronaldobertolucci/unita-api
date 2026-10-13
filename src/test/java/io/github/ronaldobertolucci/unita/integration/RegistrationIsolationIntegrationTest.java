package io.github.ronaldobertolucci.unita.integration;

import io.github.ronaldobertolucci.unita.dto.card.CreditCardCreateDto;
import io.github.ronaldobertolucci.unita.dto.pocket.*;
import io.github.ronaldobertolucci.unita.model.employer.*;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.card.CreditCardService;
import io.github.ronaldobertolucci.unita.service.pocket.PocketService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationIsolationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired LegalEntityRepository entities;
    @Autowired EmployerRepository employers;
    @Autowired PocketRepository pockets;
    @Autowired CreditCardRepository cards;
    @Autowired CreditCardBillRepository bills;
    @Autowired BankAccountTypeRepository bankTypes;
    @Autowired BenefitTypeRepository benefitTypes;
    @Autowired CardBrandRepository brands;
    @Autowired PocketService pocketService;
    @Autowired CreditCardService cardService;
    private User owner;
    private LegalEntity mine, theirs;
    private Employer myEmployer, theirEmployer;
    private long bankType, benefitType, brand;

    private User createUser() {
        return users.save(User.builder().firstName("Isolation").lastName("Test")
                .email(UUID.randomUUID() + "@example.com").password("encoded")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).enabled(true).emailVerified(true)
                .roles(Set.of(roles.findByName("USER").orElseThrow())).build());
    }

    @BeforeEach
    void setup() {
        owner = createUser();
        User other = createUser();
        Group group = Group.builder().name("Shared group").responsibleUser(owner).build();
        em.persist(group);
        em.persist(GroupMembership.builder().group(group).user(owner).build());
        em.persist(GroupMembership.builder().group(group).user(other).build());
        mine = entities.save(LegalEntity.builder().user(owner).cnpj("11111111000111").corporateName("Own entity").build());
        theirs = entities.save(LegalEntity.builder().user(other).cnpj("22222222000122").corporateName("Private other entity").build());
        myEmployer = employers.save(IndividualEmployer.builder().user(owner).cpf("11111111111").name("Own employer").build());
        theirEmployer = employers.save(LegalEntityEmployer.builder().user(other).legalEntity(theirs).build());
        bankType = bankTypes.findAll().getFirst().getId();
        benefitType = benefitTypes.findAll().getFirst().getId();
        brand = brands.findAll().getFirst().getId();
        em.flush();
        em.clear();
    }

    private String route(String kind) {
        return switch (kind) {
            case "bank" -> "/api/pockets/bank-accounts";
            case "benefit" -> "/api/pockets/benefit-accounts";
            case "fgts" -> "/api/pockets/fgts";
            default -> "/api/credit-cards";
        };
    }
    private String body(String kind, long id) {
        return switch (kind) {
            case "bank" -> "{\"legalEntityId\":" + id + ",\"number\":\"12345-6\",\"agency\":\"0001\",\"bankAccountTypeId\":" + bankType + "}";
            case "benefit" -> "{\"legalEntityId\":" + id + ",\"benefitTypeId\":" + benefitType + "}";
            case "fgts" -> "{\"employerId\":" + id + ",\"admissionDate\":\"2026-10-13\",\"dismissalDate\":null}";
            default -> "{\"legalEntityId\":" + id + ",\"lastFourDigits\":\"1234\",\"cardBrandId\":" + brand + ",\"creditLimit\":5000,\"closingDay\":10,\"dueDay\":20}";
        };
    }
    private long registration(String kind, boolean own) {
        return kind.equals("fgts") ? (own ? myEmployer : theirEmployer).getId() : (own ? mine : theirs).getId();
    }
    private void unchanged(long pocketCount, long cardCount, long billCount) {
        em.flush(); em.clear();
        assertEquals(pocketCount, pockets.count());
        assertEquals(cardCount, cards.count());
        assertEquals(billCount, bills.count());
    }

    @ParameterizedTest
    @CsvSource({"bank", "benefit", "fgts", "card"})
    void apiRejectsOtherOwnerAndMissingIdWithIdenticalSafe404(String kind) throws Exception {
        long pc = pockets.count(), cc = cards.count(), bc = bills.count();
        for (long id : new long[]{registration(kind, false), Long.MAX_VALUE}) {
            var response = mvc.perform(post(route(kind)).contextPath("/api").with(user(owner))
                            .contentType("application/json").content(body(kind, id)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.path").value(route(kind)))
                    .andExpect(jsonPath("$.message").value(kind.equals("fgts") ? "Employer not found" : "Legal entity not found"))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("Private other entity"));
            assertFalse(response.contains(theirs.getCnpj()));
            assertFalse(response.contains(theirs.getUser().getEmail()));
            unchanged(pc, cc, bc);
        }
    }

    @ParameterizedTest
    @CsvSource({"bank", "benefit", "fgts", "card"})
    void ownRegistrationAndGlobalTypesRemainValidAndUserIdCannotChooseOwner(String kind) throws Exception {
        long pc = pockets.count(), cc = cards.count(), bc = bills.count();
        String json = body(kind, registration(kind, true));
        json = json.substring(0, json.length() - 1) + ",\"userId\":" + theirs.getUser().getId() + "}";
        var response = mvc.perform(post(route(kind)).contextPath("/api").with(user(owner))
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(kind.equals("fgts") ? "$.employerName" : "$.legalEntityCorporateName")
                        .value(kind.equals("fgts") ? "Own employer" : "Own entity"))
                .andReturn().getResponse().getContentAsString();
        long id = new tools.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        em.flush(); em.clear();
        if (kind.equals("card")) {
            assertEquals(owner.getId(), cards.findById(id).orElseThrow().getUser().getId());
            assertEquals(cc + 1, cards.count()); assertEquals(bc + 1, bills.count()); assertEquals(pc, pockets.count());
        } else {
            assertEquals(owner.getId(), pockets.findById(id).orElseThrow().getUser().getId());
            assertEquals(pc + 1, pockets.count()); assertEquals(cc, cards.count()); assertEquals(bc, bills.count());
        }
    }

    @ParameterizedTest
    @CsvSource({"bank", "benefit", "fgts", "card"})
    void directServicesAlsoRejectForeignRegistration(String kind) {
        var auth = new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities());
        long id = registration(kind, false), pc = pockets.count(), cc = cards.count(), bc = bills.count();
        assertThrows(EntityNotFoundException.class, () -> {
            switch (kind) {
                case "bank" -> pocketService.createBankAccount(new BankAccountCreateDto(id, "123", "1", bankType), auth);
                case "benefit" -> pocketService.createBenefitAccount(new BenefitAccountCreateDto(id, benefitType), auth);
                case "fgts" -> pocketService.createFgtsEmployerAccount(new FgtsEmployerAccountCreateDto(id, LocalDate.now(), null), auth);
                default -> cardService.createCreditCard(new CreditCardCreateDto(id, "1234", brand, new BigDecimal("5000"), 10, 20), auth);
            }
        });
        // Repository reads remain possible inside this rollback-only test transaction.
        unchanged(pc, cc, bc);
    }

    @ParameterizedTest
    @CsvSource({"bank", "benefit", "fgts", "card"})
    void unauthenticatedCreationRemainsUnauthorized(String kind) throws Exception {
        long pc = pockets.count(), cc = cards.count(), bc = bills.count();
        mvc.perform(post(route(kind)).contextPath("/api").contentType("application/json")
                        .content(body(kind, registration(kind, true))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        unchanged(pc, cc, bc);
    }

    @Test
    void ownLegalEntityEmployerAlsoWorksForFgts() throws Exception {
        Employer employer = employers.save(LegalEntityEmployer.builder().user(owner).legalEntity(mine).build());
        mvc.perform(post(route("fgts")).contextPath("/api").with(user(owner)).contentType("application/json")
                        .content(body("fgts", employer.getId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.employerName").value("Own entity"));
    }

    @Test
    void openApiDocumentsUnavailablePrivateRegistrations() throws Exception {
        var result = mvc.perform(get("/api/v3/api-docs").contextPath("/api")).andExpect(status().isOk());
        for (String route : new String[]{"/pockets/bank-accounts", "/pockets/benefit-accounts", "/pockets/fgts", "/credit-cards"}) {
            result.andExpect(jsonPath("$.paths['" + route + "'].post.responses['404']").exists())
                    .andExpect(jsonPath("$.paths['" + route + "'].post.description").value(
                            "Requires " + (route.endsWith("fgts") ? "an employer" : "a legal entity")
                                    + " owned by the authenticated user; group membership does not share private registrations."));
        }
    }

    @Test
    void diagnosticReportsAllFourLegacyLinksWithoutChangingThem() throws Exception {
        // Simulate records created before US-01 without involving the corrected services.
        em.createNativeQuery("INSERT INTO pockets(dtype,user_id) VALUES ('BANK_ACCOUNT', :owner)").setParameter("owner", owner.getId()).executeUpdate();
        long bank = ((Number) em.createNativeQuery("SELECT MAX(id) FROM pockets").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO bank_accounts(pocket_id,legal_entity_id,number,agency,bank_account_type_id) VALUES (:id,:le,'123','1',:type)")
                .setParameter("id", bank).setParameter("le", theirs.getId()).setParameter("type", bankType).executeUpdate();
        em.createNativeQuery("INSERT INTO pockets(dtype,user_id) VALUES ('BENEFIT_ACCOUNT', :owner)").setParameter("owner", owner.getId()).executeUpdate();
        long benefit = ((Number) em.createNativeQuery("SELECT MAX(id) FROM pockets").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO benefit_accounts(pocket_id,legal_entity_id,benefit_type_id) VALUES (:id,:le,:type)")
                .setParameter("id", benefit).setParameter("le", theirs.getId()).setParameter("type", benefitType).executeUpdate();
        em.createNativeQuery("INSERT INTO pockets(dtype,user_id) VALUES ('FGTS_EMPLOYER_ACCOUNT', :owner)").setParameter("owner", owner.getId()).executeUpdate();
        long fgts = ((Number) em.createNativeQuery("SELECT MAX(id) FROM pockets").getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO fgts_employer_accounts(pocket_id,employer_id,admission_date) VALUES (:id,:employer,'2026-10-13')")
                .setParameter("id", fgts).setParameter("employer", theirEmployer.getId()).executeUpdate();
        em.createNativeQuery("INSERT INTO credit_cards(user_id,legal_entity_id,last_four_digits,card_brand_id,credit_limit,closing_day,due_day) VALUES (:owner,:le,'1234',:brand,5000,10,20)")
                .setParameter("owner", owner.getId()).setParameter("le", theirs.getId()).setParameter("brand", brand).executeUpdate();
        // Include the indirect legacy path through a PJ employer and one healthy control.
        employers.save(LegalEntityEmployer.builder().user(owner).legalEntity(theirs).build());
        pocketService.createBenefitAccount(new BenefitAccountCreateDto(mine.getId(), benefitType),
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        em.flush();
        long pc = pockets.count(), cc = cards.count(), bc = bills.count();
        String sql = Files.readString(Path.of("docs/sql/US-01-diagnostico-associacoes.sql"));
        var rows = em.createNativeQuery(sql).getResultList();
        assertEquals(5, rows.size());
        assertEquals(Set.of("BANK_ACCOUNT", "BENEFIT_ACCOUNT", "FGTS_EMPLOYER_ACCOUNT", "CREDIT_CARD", "LEGAL_ENTITY_EMPLOYER"),
                rows.stream().map(row -> ((Object[]) row)[0]).collect(java.util.stream.Collectors.toSet()));
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            assertEquals(owner.getId(), ((Number) values[2]).longValue());
            assertEquals(theirs.getUser().getId(), ((Number) values[4]).longValue());
        }
        assertEquals(rows.size(), em.createNativeQuery(sql).getResultList().size());
        unchanged(pc, cc, bc);
    }
}
