# Sessões de autenticação

Esta API não contém frontend. O backend implementa expiração explícita, refresh com rotação, logout e revogação após redefinição de senha. O comportamento de navegação, memória, abas e rascunhos abaixo é um contrato para o frontend consumidor.

## HTTP na rede local

O padrão é `AUTH_COOKIE_SECURE=false` e `AUTH_COOKIE_SAME_SITE=Lax`, inclusive no compose com profile `prod`, para permitir a implantação atual em HTTP. O nome do profile não determina o protocolo. Ao implantar HTTPS, configure `AUTH_COOKIE_SECURE=true`.

Frontend e API devem usar o mesmo hostname/IP e protocolo, por exemplo `http://192.168.1.10:4200` e `http://192.168.1.10/api`. Portas diferentes são origens diferentes, mas continuam no mesmo site. Outra opção é servir ambos pelo mesmo proxy/origem. Não misture `localhost`, nomes e IPs: cookies Lax não atendem fetch entre sites diferentes. Para sites distintos, será necessário HTTPS e `SameSite=None; Secure`, ou um proxy que reúna as origens.

Configure `CORS_ALLOWED_ORIGINS` com as origens exatas e confiáveis do frontend, separadas por vírgula, sem paths e sem curingas. Exemplo: `http://192.168.1.10:4200`. A aplicação rejeita configuração com `*`. CORS permite credenciais e o header `X-CSRF-Protection`. HTTP não cifra senhas e tokens em trânsito; este ajuste viabiliza a rede local, e HTTPS fornece a proteção de transporte.

## Endpoints

Todos os exemplos incluem o context path padrão `/api`. Login, refresh e logout exigem `X-CSRF-Protection: 1`. Chamadas cross-origin devem usar `credentials: "include"`. Chamadas sem esse header retornam `403 {"status":403,"code":"CSRF_PROTECTION_REQUIRED"}` antes da operação. A checagem usa o handler resolvido pelo Spring MVC, e caminhos rejeitados pelo firewall continuam rejeitados.

| Endpoint | Entrada | Sucesso |
|---|---|---|
| `POST /api/auth/login` | JSON `email`, `password` | `200`, JWT e cookie de refresh |
| `POST /api/auth/refresh` | Cookie, sem exigir JWT | `200`, novo JWT e cookie rotacionado |
| `POST /api/auth/logout` | Cookie opcional | `204`, sessão revogada e cookie apagado |
| `GET /api/auth/me` | `Authorization: Bearer <JWT>` | `200`, dados públicos do usuário |

Login e refresh preservam o formato existente do JSON:

```json
{"token":"<JWT>","type":"Bearer","expiresIn":7200,"user":{"id":1,"email":"usuario@example.com","firstName":"Nome","lastName":"Sobrenome","dateOfBirth":"1990-01-01","enabled":true,"roles":["USER"]}}
```

O refresh token nunca aparece no JSON. O navegador recebe `unita_refresh`, HttpOnly, sem Domain, SameSite configurável, Path `/api/auth` (derivado do context path), Max-Age limitado à validade absoluta. Apagamento usa o mesmo nome, path, HttpOnly, Secure e SameSite. Logout sem cookie ou com cookie desconhecido/malformado também retorna 204. Bearer expirado não impede login nem outras rotas públicas de autenticação ou senha.

```javascript
const api = "http://192.168.1.10/api";
const response = await fetch(`${api}/auth/login`, {
  method: "POST", credentials: "include",
  headers: { "Content-Type": "application/json", "X-CSRF-Protection": "1" },
  body: JSON.stringify({ email, password })
});
const session = await response.json(); // guardar session.token somente em memória

const refreshResponse = await fetch(`${api}/auth/refresh`, {
  method: "POST", credentials: "include",
  headers: { "X-CSRF-Protection": "1" }
});

await fetch(`${api}/auth/logout`, {
  method: "POST", credentials: "include",
  headers: { "X-CSRF-Protection": "1" }
});
```

Respostas bem-sucedidas de login/refresh/logout e erros do handler de refresh usam `Cache-Control: no-store`. Refresh ausente, desconhecido, malformado, expirado, revogado ou de conta desabilitada retorna `401 {"status":401,"code":"SESSION_EXPIRED"}`, `WWW-Authenticate: Bearer`, e apaga o cookie. Erros de infraestrutura permanecem erros de servidor, sem serem tratados como expiração nem provocar repetição automática.

## Recursos protegidos

| HTTP | `code` | Ação do frontend |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | Tentar recuperar sessão uma vez, se apropriado |
| 401 | `INVALID_TOKEN` | Tentar recuperar sessão uma vez, se apropriado |
| 401 | `SESSION_EXPIRED` | Renovar uma vez; 401 do refresh exige login |
| 403 | `ACCESS_DENIED` | Mostrar falta de permissão, sem refresh |

Os 401 de recursos protegidos incluem `WWW-Authenticate: Bearer`, exposto pelo CORS. Credenciais inválidas no login continuam retornando 401 com o formato de erro de login anterior. Contas desabilitadas ou sem confirmação não autenticam nem renovam. Os códigos de login `EMAIL_NOT_VERIFIED` e `ACCOUNT_DISABLED` e a migração dos dados legados estão em [auth-account-state.md](auth-account-state.md).

## Persistência, atomicidade e revogação

Migração Flyway `V20__refresh_sessions.sql`: tabelas `refresh_sessions` e `refresh_token_history`, UUID por sessão, FK para usuários e histórico com exclusão em cascata. Índices por usuário, expiração e sessão; hashes únicos. São gerados 32 bytes aleatórios via SecureRandom (256 bits), codificados em base64url sem padding, persistindo apenas SHA-256. Cada login tem uma sessão independente.

JWT mantém o padrão de 2 horas. Sessão de refresh mantém validade absoluta de 7 dias; renovar não estende esse prazo. Os hashes anteriores ficam até a limpeza da sessão expirada. A limpeza agendada remove sessões expiradas e seu histórico, preservando sessões válidas, inclusive o histórico de sessões revogadas ainda não expiradas.

Operações bloqueiam primeiro o usuário e depois a sessão com `SELECT ... FOR UPDATE`, compartilhando a transação Spring/JPA/JDBC. Redefinição de senha bloqueia o usuário e revoga todas as suas sessões na mesma transação da alteração da senha. Refresh usa `noRollbackFor=RefreshRejectedException.class`: reutilização conhecida revoga a sessão e confirma a alteração mesmo quando o endpoint retorna 401. Falhas inesperadas mantêm rollback normal.

Duas renovações usando o mesmo token são serializadas: uma pode obter sucesso e a outra detecta reutilização e revoga a sessão inteira, incluindo o token recém emitido. Logout revoga apenas a sessão identificada, mesmo quando o cookie contém um token antigo conhecido.

**Logout e redefinição de senha impedem novas renovações. JWTs já emitidos continuam válidos até expirar.** Não há blacklist nem vínculo do JWT à sessão. O filtro consulta o usuário atual e rejeita contas desabilitadas, não confirmadas ou excluídas.

## Configuração

| Propriedade / variável | Padrão |
|---|---|
| `api.security.token.expiration-hours` | 2 horas |
| `api.security.refresh.expiration-days` / `AUTH_REFRESH_EXPIRATION_DAYS` | 7 dias |
| `api.security.refresh.cookie.secure` / `AUTH_COOKIE_SECURE` | false para HTTP local |
| `api.security.refresh.cookie.same-site` / `AUTH_COOKIE_SAME_SITE` | Lax |
| `api.security.refresh.cleanup.cron` / `AUTH_REFRESH_CLEANUP_CRON` | `0 30 2 * * ?` |
| `server.servlet.context-path` | `/api` |
| `cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` (prod) | obrigatório, origens explícitas |

`SameSite=None` com Secure=false falha na inicialização. `Strict`, `Lax` e `None` são os valores aceitos. O agendamento segue o fuso padrão da aplicação (America/Sao_Paulo). Cron `-` desabilita a limpeza.

## Contrato a implementar no frontend

1. Manter JWT em memória e deixar o navegador administrar o refresh HttpOnly. Recuperar a sessão por refresh na inicialização/reload.
2. Coordenar uma única renovação em andamento, inclusive entre abas (por exemplo Web Locks com BroadcastChannel), compartilhando o JWT atualizado. Evitar que duas abas usem o mesmo cookie simultaneamente.
3. Após 401 de recurso protegido, tentar refresh uma vez; não interceptar automaticamente login/refresh/logout. Refresh 401 limpa a memória e leva ao login.
4. Não tratar 403, falhas de rede e 5xx como expiração. Não repetir refresh após resposta perdida: a rotação pode ter sido confirmada. Não repetir escritas sem garantia de que falharam antes da execução ou mecanismo de idempotência.
5. Guardar a rota interna e rascunho associados ao ID do usuário, restaurando apenas se o novo login pertence ao mesmo usuário. Validar rotas de retorno como rotas internas.

Nenhum desses comportamentos de interface foi implementado neste repositório, pois ele contém apenas backend. A validação de cookies no navegador real e da configuração concreta de rede depende da implantação do frontend consumidor.

## Validação

`AuthFlowIntegrationTest` exercita o fluxo completo com Spring Security, MockMvc, Flyway e H2; `PostgreSQLAuthFlowIntegrationTest` herda os mesmos cenários e só executa quando `AUTH_TEST_POSTGRES_URL` é definido. Use **banco exclusivo de testes**, pois a preparação apaga sessões de refresh. O teste PostgreSQL usa usuário/senha `auth_test` e driver/dialeto PostgreSQL.

```bash
./mvnw -Dspring.profiles.active=test test
AUTH_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/auth_test \
  ./mvnw -Dspring.profiles.active=test -Dtest=PostgreSQLAuthFlowIntegrationTest test
```

Os cenários incluem JWT realmente expirado, falta de permissão, login com Bearer expirado, atributos/path do cookie, rotação e validade absoluta, reutilização/revogação persistida, concorrência, contas desabilitadas, expiração/limpeza, logout e isolamento entre sessões, redefinição de senha, CORS/preflight e CSRF. Validação executada em 11/10/2026, com Java 25 em containers temporários:

- Suíte completa: 1.046 testes contabilizados, 1.037 aprovados, zero falhas/erros; os 9 testes específicos de PostgreSQL ficam desabilitados nessa execução padrão.
- Execução focada com PostgreSQL 16: 22 testes aprovados, zero falhas/erros, incluindo os 9 cenários herdados em PostgreSQL, 9 em H2 e 4 verificações unitárias de filtro/cookie.
- Flyway aplicado no PostgreSQL isolado, incluindo V20; confirmadas rotação concorrente, revogação após erro e revogação após redefinição de senha no banco real.
- Após retirar o log da senha do administrador: 6 testes do inicializador aprovados.
- Configuração fornecida do Nginx aprovada por `nginx -t`. Containers temporários de teste encerrados.

A inicialização do administrador não imprime mais sua senha. O access log do Nginx fornecido omite query strings e headers de autenticação, evitando registrar JWTs do SSE e tokens de verificação/redefinição. Outros proxies/coletores também devem omitir dados de autenticação; logs de erro de proxies e logging HTTP detalhado precisam de cuidado especial, pois podem incluir URLs completas.
