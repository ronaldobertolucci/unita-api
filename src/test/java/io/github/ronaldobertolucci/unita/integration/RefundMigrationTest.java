package io.github.ronaldobertolucci.unita.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RefundMigrationTest {
    @Test void upgrades23WithoutRewritingHistoricalRefundsAndProtectsNewReference() throws Exception {
        String url=System.getProperty("spring.datasource.url","jdbc:h2:mem:us06_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        String username=System.getProperty("spring.datasource.username","sa"),password=System.getProperty("spring.datasource.password","");
        String schema="us06_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var conn=DriverManager.getConnection(url,username,password);var stmt=conn.createStatement()) {
            // Keep the DDL connection alive: H2 2.4 CHECK expressions retain its session comparator.
            var dataSource=new org.springframework.jdbc.datasource.SingleConnectionDataSource(conn,true);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("23").load().migrate();
            conn.setSchema(schema);
            stmt.executeUpdate("INSERT INTO users (id,first_name,last_name,email,password,date_of_birth,enabled,email_verified) VALUES (90001,'Test','Upgrade','upgrade@example.invalid','encoded',DATE '1990-01-01',true,true)");
            stmt.executeUpdate("INSERT INTO pockets (id,dtype,user_id) VALUES (90001,'CASH',90001)");
            stmt.executeUpdate("INSERT INTO cash (pocket_id) VALUES (90001)");
            stmt.executeUpdate("INSERT INTO transactions (id,pocket_id,amount,direction,transaction_date,description,category_id) VALUES (90001,90001,100,'EXPENSE',DATE '2026-10-13','Legacy payment',2)");
            stmt.executeUpdate("INSERT INTO legal_entities (id,user_id,cnpj,corporate_name) VALUES (90001,90001,'11111111000111','Fixture')");
            stmt.executeUpdate("INSERT INTO credit_cards (id,user_id,legal_entity_id,last_four_digits,card_brand_id,credit_limit,closing_day,due_day) VALUES (90001,90001,90001,'1234',(SELECT min(id) FROM card_brands),500,10,20)");
            stmt.executeUpdate("INSERT INTO credit_card_bills (id,credit_card_id,period_start,closing_date,due_date,closing_day,due_day,status,payment_transaction_id) VALUES (90001,90001,DATE '2026-09-10',DATE '2026-10-10',DATE '2026-10-20',10,20,'PAID',90001)");
            stmt.executeUpdate("INSERT INTO credit_card_purchases (id,credit_card_id,description,total_value,purchase_date,installments_count) VALUES (90001,90001,'Original',100,DATE '2026-09-15',1)");
            stmt.executeUpdate("INSERT INTO credit_card_refunds (id,credit_card_bill_id,description,amount,refund_date,category_id) VALUES (90001,90001,'Legacy refund',50,DATE '2026-10-13',3)");
            var upgrade=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            assertEquals(1,upgrade.migrate().migrationsExecuted);upgrade.validate();assertEquals(0,upgrade.migrate().migrationsExecuted);
            try(var rows=stmt.executeQuery("SELECT amount,description,purchase_id FROM credit_card_refunds WHERE id=90001")) {
                assertTrue(rows.next());assertEquals(new BigDecimal("50.00"),rows.getBigDecimal(1));assertEquals("Legacy refund",rows.getString(2));assertNull(rows.getObject(3));
            }
            try(var rows=stmt.executeQuery("SELECT status,payment_transaction_id FROM credit_card_bills WHERE id=90001")) {
                assertTrue(rows.next());assertEquals("PAID",rows.getString(1));assertEquals(90001,rows.getLong(2));
            }
            try(var rows=stmt.executeQuery("SELECT amount FROM transactions WHERE id=90001")) {assertTrue(rows.next());assertEquals(new BigDecimal("100.00"),rows.getBigDecimal(1));}
            stmt.executeUpdate("UPDATE credit_card_refunds SET purchase_id=90001 WHERE id=90001");
            assertThrows(SQLException.class,()->stmt.executeUpdate("DELETE FROM credit_card_purchases WHERE id=90001"));
            assertThrows(SQLException.class,()->stmt.executeUpdate("UPDATE credit_card_refunds SET purchase_id=99999 WHERE id=90001"));
        }
    }
}
