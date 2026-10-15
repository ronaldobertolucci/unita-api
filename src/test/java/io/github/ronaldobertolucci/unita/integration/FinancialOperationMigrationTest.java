package io.github.ronaldobertolucci.unita.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="spring.datasource.url",matches="jdbc:postgresql:.*")
class FinancialOperationMigrationTest {
    @Test void upgrades22PreservingLegacyRowsAndProtectsExplicitPayment() throws Exception {
        String url=System.getProperty("spring.datasource.url");
        String username=System.getProperty("spring.datasource.username"),password=System.getProperty("spring.datasource.password");
        String schema="us02_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var conn=DriverManager.getConnection(url,username,password);var stmt=conn.createStatement()) {
            Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema).target("22").load().migrate();
            conn.setSchema(schema);
            stmt.executeUpdate("INSERT INTO users (id,first_name,last_name,email,password,date_of_birth,enabled,email_verified) VALUES (90001,'Test','Upgrade','upgrade@example.invalid','encoded',DATE '1990-01-01',true,true)");
            stmt.executeUpdate("INSERT INTO pockets (id,dtype,user_id) VALUES (90001,'CASH',90001)");
            stmt.executeUpdate("INSERT INTO cash (pocket_id) VALUES (90001)");
            stmt.executeUpdate("INSERT INTO transactions (id,pocket_id,amount,direction,transaction_date,description,category_id) VALUES (90001,90001,80.00,'EXPENSE',DATE '2026-10-13','Legacy payment',2), (90002,90001,23.45,'INCOME',DATE '2026-10-13','Ambiguous historical transfer',3)");
            stmt.executeUpdate("INSERT INTO legal_entities (id,user_id,cnpj,corporate_name) VALUES (90001,90001,'11111111000111','Fixture')");
            stmt.executeUpdate("INSERT INTO credit_cards (id,user_id,legal_entity_id,last_four_digits,card_brand_id,credit_limit,closing_day,due_day) VALUES (90001,90001,90001,'1234',(SELECT min(id) FROM card_brands),500,10,20)");
            stmt.executeUpdate("INSERT INTO credit_card_bills (id,credit_card_id,period_start,closing_date,due_date,closing_day,due_day,status,payment_transaction_id) VALUES (90001,90001,DATE '2026-09-10',DATE '2026-10-10',DATE '2026-10-20',10,20,'PAID',90001)");
            var upgrade=Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema).target("24").load();
            assertEquals(2,upgrade.migrate().migrationsExecuted);upgrade.validate();assertEquals(0,upgrade.migrate().migrationsExecuted);
            try(var rows=stmt.executeQuery("SELECT amount,description FROM transactions ORDER BY id")) {
                assertTrue(rows.next());assertEquals(new BigDecimal("80.00"),rows.getBigDecimal(1));assertEquals("Legacy payment",rows.getString(2));
                assertTrue(rows.next());assertEquals(new BigDecimal("23.45"),rows.getBigDecimal(1));assertEquals("Ambiguous historical transfer",rows.getString(2));assertFalse(rows.next());
            }
            try(var rows=stmt.executeQuery("SELECT count(*) FROM transfer_operations")) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            var failure=assertThrows(SQLException.class,()->stmt.executeUpdate("DELETE FROM transactions WHERE id=90001"));assertEquals("23001",failure.getSQLState());
            try(var rows=stmt.executeQuery("SELECT status,payment_transaction_id FROM credit_card_bills WHERE id=90001")) {
                assertTrue(rows.next());assertEquals("PAID",rows.getString(1));assertEquals(90001,rows.getLong(2));
            }
        }
    }
}
