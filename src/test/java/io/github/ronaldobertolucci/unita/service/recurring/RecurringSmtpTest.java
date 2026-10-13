package io.github.ronaldobertolucci.unita.service.recurring;

import io.github.ronaldobertolucci.unita.service.email.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** A disposable loopback SMTP server; no real recipients or production configuration. */
class RecurringSmtpTest {
 static class Smtp implements AutoCloseable {
  final ServerSocket server;
  final ExecutorService executor=Executors.newSingleThreadExecutor();
  final Future<String> transcript;
  Smtp(boolean accept) throws IOException {
   server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
   transcript=executor.submit(()->{
    StringBuilder message=new StringBuilder();
    try(var socket=server.accept();var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var out=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8)) {
     socket.setSoTimeout(5000); out.print("220 test.local SMTP\r\n"); out.flush();
     boolean data=false;
     for(String line;(line=reader.readLine())!=null;) {
      if(data) { if(line.equals(".")) { data=false; out.print(accept?"250 accepted\r\n":"554 test rejection\r\n"); } else { message.append(line).append("\r\n"); continue; } }
      else if(line.startsWith("EHLO") || line.startsWith("HELO")) out.print("250 test.local\r\n");
      else if(line.startsWith("DATA")) { data=true; out.print("354 send data\r\n"); }
      else if(line.startsWith("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
      else out.print("250 ok\r\n");
      out.flush();
     }
    }
    return message.toString();
   });
  }
  public void close() throws IOException { server.close();executor.shutdownNow(); }
 }
 EmailService sender(int port) {
  var mail=new JavaMailSenderImpl(); mail.setHost("localhost");mail.setPort(port);
  var props=new Properties(); props.put("mail.smtp.connectiontimeout","3000"); props.put("mail.smtp.timeout","3000"); mail.setJavaMailProperties(props);
  var service=new EmailService(mail); ReflectionTestUtils.setField(service,"emailFrom","test@example.invalid"); return service;
 }
 @Test void synchronousSmtpAcceptanceProducesAnIndividualMessage() throws Exception {
  try(var smtp=new Smtp(true)) {
   sender(smtp.server.getLocalPort()).sendHtmlEmailSynchronously("recipient@example.invalid","Vencimento previsto","<html><body>Valor previsto: não informado</body></html>");
   var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(smtp.transcript.get(5,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8)));
   assertEquals("Vencimento previsto",message.getSubject()); assertEquals(1,message.getAllRecipients().length);
   assertEquals("recipient@example.invalid",message.getAllRecipients()[0].toString()); assertTrue(message.isMimeType("multipart/*"));
  }
 }
 @Test void smtpRejectionIsObservableSynchronously() throws Exception {
  try(var smtp=new Smtp(false)) {
   assertThrows(RuntimeException.class,()->sender(smtp.server.getLocalPort()).sendHtmlEmailSynchronously("recipient@example.invalid","Vencimento previsto","<p>Valor previsto</p>"));
   assertTrue(smtp.transcript.get(5,TimeUnit.SECONDS).contains("MIME-Version"));
  }
 }
}
