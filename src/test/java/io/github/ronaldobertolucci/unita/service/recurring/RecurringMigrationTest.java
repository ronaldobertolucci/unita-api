package io.github.ronaldobertolucci.unita.service.recurring;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="spring.datasource.url",matches="jdbc:postgresql:.*")
class RecurringMigrationTest {
 @Test void upgradesVersion21WithoutChangingExistingFinancialRecords() throws Exception {
  String url=System.getProperty("spring.datasource.url");
  String username=System.getProperty("spring.datasource.username","sa"),password=System.getProperty("spring.datasource.password","");
  String schema="us07_upgrade_"+UUID.randomUUID().toString().replace("-","");
  try(var conn=DriverManager.getConnection(url,username,password);var stmt=conn.createStatement()) {
   var old=Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema).target("21").load(); old.migrate();
   conn.setSchema(schema);
   stmt.executeUpdate("INSERT INTO users (id,first_name,last_name,email,password,date_of_birth,enabled,email_verified) VALUES (90001,'Test','Upgrade','upgrade@example.invalid','encoded',DATE '1990-01-01',true,true)");
   stmt.executeUpdate("INSERT INTO pockets (id,dtype,user_id) VALUES (90001,'Cash',90001)");
   stmt.executeUpdate("INSERT INTO cash (pocket_id) VALUES (90001)");
   stmt.executeUpdate("INSERT INTO transactions (id,pocket_id,amount,direction,transaction_date,description,category_id) VALUES (90001,90001,123.45,'INCOME',DATE '2026-10-13','Preserved fixture',41)");
   var upgrade=Flyway.configure().dataSource(url,username,password).schemas(schema).defaultSchema(schema).target("22").load();
   assertEquals(1,upgrade.migrate().migrationsExecuted); upgrade.validate();
   try(var result=stmt.executeQuery("SELECT amount,description FROM transactions WHERE id=90001")) {
    assertTrue(result.next()); assertEquals(0,new java.math.BigDecimal("123.45").compareTo(result.getBigDecimal(1))); assertEquals("Preserved fixture",result.getString(2));
   }
   try(var result=stmt.executeQuery("SELECT count(*) FROM recurring_bills")) { assertTrue(result.next());assertEquals(0,result.getInt(1)); }
   assertEquals(0,upgrade.migrate().migrationsExecuted);
  }
 }
}
