# Estados de acesso e confirmação de e-mail

## Modelo e contratos

`users.enabled` representa exclusivamente a habilitação administrativa. `users.email_verified` registra a confirmação do endereço de e-mail. O acesso exige ambos `true`. O DTO público mantém `enabled` e acrescenta `emailVerified`, seguindo a convenção camelCase existente. Cadastros públicos começam habilitados e não confirmados. A conta de bootstrap criada por `AdminInitializer` é provisionada por um operador confiável e começa confirmada; o inicializador não altera contas existentes.

Confirmar um token válido altera somente `email_verified` e consome o token na mesma transação. A confirmação usa atualização SQL específica, sem salvar um usuário carregado anteriormente com estado administrativo desatualizado. Habilitar/desabilitar altera somente `enabled`; `@DynamicUpdate` impede que essas alterações escrevam a confirmação carregada antes de uma transação concorrente. Habilitar um cadastro pendente exige ainda sua confirmação antes do acesso.

## Login e frontend

A senha é validada pelo `DaoAuthenticationProvider` antes de consultar o estado para produzir erros de conta. O provider mantém a comparação de senha e a proteção temporal para usuários inexistentes. Contas desabilitadas têm prioridade sobre falta de confirmação.

| Caso | HTTP | Contrato |
|---|---|---|
| Senha incorreta ou usuário/e-mail desconhecido | 401 | Erro genérico existente, `message: Invalid email or password`; sem código novo |
| Senha correta, habilitada e não confirmada | 403 | `EMAIL_NOT_VERIFIED` |
| Senha correta, desabilitada, confirmada ou não | 403 | `ACCOUNT_DISABLED` |
| Senha correta, habilitada e confirmada | 200 | JWT, metadados e usuário no corpo; refresh no cookie HttpOnly |
| Falha interna do provider/banco | 500 | Erro genérico de servidor, sem expor a causa |

Exemplos anonimizados:

```json
{"status":403,"code":"EMAIL_NOT_VERIFIED","message":"Email verification is required"}
```

```json
{"status":403,"code":"ACCOUNT_DISABLED","message":"Account is disabled"}
```

O erro de credenciais inválidas mantém os campos anteriores (o horário é ilustrativo):

```json
{"timestamp":"2026-10-11T12:00:00","status":401,"error":"Unauthorized","message":"Invalid email or password","path":"/api/auth/login","details":null}
```

Somente após receber `EMAIL_NOT_VERIFIED` no login o frontend pode orientar a confirmação e oferecer reenvio. `ACCOUNT_DISABLED` exige orientação para suporte/revisão administrativa; não deve ser convertido em `EMAIL_NOT_VERIFIED`, nem oferecer reenvio como solução. Senha incorreta e usuário inexistente recebem a mesma mensagem genérica. Nenhuma rejeição de login cria JWT, sessão de refresh, cookie ou sessão HTTP. Erros 403 de estado incluem `Cache-Control: no-store`.

Login válido, refresh, logout e proteção CSRF mantêm o contrato de [auth-session.md](auth-session.md). A emissão de sessão relê o usuário após bloquear sua linha, impedindo que um principal autenticado antes de uma desabilitação crie uma sessão. Refresh exige os dois campos e mantém `401 SESSION_EXPIRED` e limpeza do cookie para sessões rejeitadas. O filtro também rejeita JWTs existentes de contas sem acesso, mantendo `401 INVALID_TOKEN`. Falhas internas permanecem 5xx e não devem gerar tentativa automática de refresh.

## Confirmação e reenvio

`GET /api/auth/verify-email?token=...` mantém sucesso 200 sem corpo e as validações de token inexistente, consumido ou expirado. Confirmar uma conta desabilitada registra a confirmação, mas mantém a desabilitação.

`POST /api/auth/resend-verification`, com corpo `{"email":"user@example.com"}`, mantém 200 sem corpo para conta inexistente, confirmada, desabilitada ou habilitada pendente. Só o último caso gera token e mensagem. Não se envia mensagem nem se retorna erro específico para os demais. Formato inválido continua 400. O envio de confirmação agora aguarda o servidor SMTP: falhas reais retornam 5xx e revertem a criação do token. Recuperação de senha mantém seu envio assíncrono. Aceitação pelo SMTP não garante entrega na caixa de entrada.

## Migração e registros legados

`V21__separate_email_verification.sql` segue a estratégia incremental do Flyway do projeto. Acrescenta um booleano obrigatório com padrão `false` e **não altera `enabled`**:

- Contas antigas habilitadas recebem `email_verified=true` para preservar o acesso anteriormente permitido pelo único campo `enabled`, incluindo administradores provisionados. Isso é compatibilidade com o estado de acesso legado, não prova independente de verificação do endereço.
- Um token com `used=true` é evidência de confirmação, inclusive quando a conta está desabilitada. Sua expiração posterior não invalida a evidência de consumo.
- Contas desabilitadas sem token consumido ficam `email_verified=false`. Ausência de token e tokens pendentes/expirados não são evidência para confirmar ou habilitar.

**Registros legados com `enabled=false` são ambíguos:** podem representar cadastro pendente ou desabilitação administrativa. Permanecem bloqueados, inclusive para reenvio. Antes de habilitar, um administrador deve revisar individualmente o histórico disponível e confirmar que não existe bloqueio administrativo. Não infira permissão de acesso pela ausência/expiração de token. Quando a revisão autorizar o acesso, use a operação administrativa de habilitação; ela preserva a confirmação. Se a conta continuar não confirmada, o usuário pode então solicitar reenvio e confirmar. Sem evidência suficiente, mantenha o bloqueio. Não há habilitação em massa.

Para localizar candidatos à revisão:

```sql
SELECT id, enabled, email_verified FROM users WHERE enabled = false;
```

Use backup e a migração incremental habitual na implantação. V21 é uma migração para frente, compatível com as demais migrações versionadas do projeto, sem undo automático. Uma reversão precisa restaurar o backup com a versão anterior da aplicação ou ser planejada como nova migração; remover a coluna isoladamente perde a distinção e o backend atualizado depende dela.

## Verificação

Os testes cobrem cadastro, matriz de estados e senha, prioridade de desabilitação, ausência de emissão em rejeições, login válido, bloqueio de refresh/JWT, reenvio indistinguível, validação de formato, falhas reais, consumo de confirmação e preservação de desabilitação. Dois testes usam transações concorrentes com uma entidade já carregada para verificar as duas ordens de atualização entre confirmação e ação administrativa. A migração é exercitada desde V20 com dados legados habilitados/desabilitados e tokens ausentes/pendentes/expirados/consumidos, verificando padrão para novos registros e reaplicação do Flyway.

Validação executada em 11/10/2026 com Java 25 em containers temporários:

- Suíte completa: 1.073 testes contabilizados, 1.053 aprovados, zero falhas/erros e 20 desabilitados por dependerem da variável de ambiente de PostgreSQL.
- Execução adicional com PostgreSQL 16: 21 aprovados, zero falhas/erros; 19 cenários do fluxo em PostgreSQL, um cenário de migração legada em PostgreSQL e a repetição do cenário de migração em H2. Inclui os dois cenários concorrentes de confirmação/ação administrativa no banco real.
- Flyway V21 aplicado e validado tanto sobre instalação nova quanto sobre V20 com contas e tokens legados; nenhuma conta desabilitada foi habilitada automaticamente.
- A execução completa inicialmente identificou uma dependência ausente nos testes MVC isolados. O provider foi separado em `CredentialsConfiguration`; a execução completa final passou.
- `git diff --check` aprovado. Containers e rede de testes removidos; o banco da aplicação não foi usado.

Comandos reproduzíveis em um ambiente com Java 25 e Maven (o projeto também fornece `./mvnw`):

```bash
mvn -Dspring.profiles.active=test test
AUTH_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/auth_test \
  mvn -Dspring.profiles.active=test \
  -Dtest=PostgreSQLAuthFlowIntegrationTest,EmailVerificationMigrationTest test
```

O banco PostgreSQL deve ser exclusivo de testes, com usuário/senha `auth_test`: os testes de fluxo apagam sessões de refresh. O teste de migração usa um schema temporário separado. Nesta execução, Maven usou o cache local em modo offline; logs estão em `/tmp/unita-account-state-full-tests.log` e `/tmp/unita-account-state-pg-tests.log`.

Limitações: dados legados desabilitados continuam exigindo decisão administrativa individual; a aceitação do e-mail pelo SMTP e os testes de backend não comprovam entrega na caixa de entrada nem o comportamento do frontend consumidor.
