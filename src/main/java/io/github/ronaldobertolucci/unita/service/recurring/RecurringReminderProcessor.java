package io.github.ronaldobertolucci.unita.service.recurring;
import io.github.ronaldobertolucci.unita.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
@Service @RequiredArgsConstructor @Slf4j
public class RecurringReminderProcessor {
 private final RecurringReminderQueue queue;
 private final EmailService emailService;
 @Value("${app.frontend.url}") private String frontendUrl;
 @Value("${app.recurring.panel-path:/recurring-bills}") private String panelPath;
 public void process(Long id) {
  var reserved=queue.reserve(id); if(reserved.isEmpty()) return;
  var reservation=reserved.get(); var payload=queue.prepare(reservation); if(payload.isEmpty()) return;
  boolean success=false;
  try { emailService.sendHtmlEmailSynchronously(payload.get().recipient(),"Vencimento previsto",render(payload.get())); success=true; }
  catch(RuntimeException ex) { log.warn("Recurring reminder SMTP failure id={}",id); }
  // Infrastructure failure here leaves a recoverable reservation, never a false SMTP failure.
  queue.complete(reservation,success);
 }
 public String render(RecurringReminderQueue.EmailPayload p) {
  URI origin=URI.create(frontendUrl);
  if(!validOrigin(origin) || !panelPath.startsWith("/") || panelPath.startsWith("//") || panelPath.contains("?") || panelPath.contains("#"))
   throw new IllegalArgumentException("Invalid recurring panel URL configuration");
  String link=frontendUrl.replaceAll("/+$","")+panelPath;
  return "<html><body><h2>Vencimento previsto</h2><p>"+HtmlUtils.htmlEscape(p.description())+"</p><p>Data prevista: "+p.dueDate()
   +"</p><p>Valor previsto: "+(p.expectedAmount()==null?"não informado":"R$ "+p.expectedAmount().toPlainString())
   +"</p><p>Este lembrete é uma previsão. O painel não acompanha pagamentos.</p><a href=\""+HtmlUtils.htmlEscape(link)+"\">Abrir painel de recorrentes</a></body></html>";
 }
 private static boolean validOrigin(URI u) { return ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost()!=null && u.getUserInfo()==null && u.getQuery()==null && u.getFragment()==null; }
}
