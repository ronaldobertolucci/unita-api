# Unita API

API REST para controle financeiro familiar. Gerencia contas, movimentações, cartões, investimentos e compartilhamento de informações em grupos, além de lembretes de contas recorrentes por e-mail.

Este repositório contém o backend. A interface é uma aplicação separada. A API registra informações financeiras; não executa pagamentos ou transferências em bancos e corretoras.

## Tecnologias

- Java 25 e Spring Boot 4.0.2.
- Spring Security, JWT e sessões de refresh persistidas.
- Spring Data JPA e PostgreSQL 16.
- Flyway para versionamento do banco.
- SMTP para confirmação de e-mail, recuperação de senha e lembretes.
- Docker Compose e Nginx para execução dos serviços.
- JUnit, Mockito e H2 para testes, com cenários adicionais em PostgreSQL.

## Funcionalidades

Resumo dos recursos expostos pela OpenAPI, gerada pelo Springdoc a partir dos controllers e DTOs. As rotas abaixo são relativas à base `/api`; os contratos completos, campos, parâmetros e respostas podem ser consultados no [Swagger UI](#documentação-openapi).

| Módulo | Funcionalidades resumidas | Rotas principais |
|---|---|---|
| Usuários e autenticação | Cadastro, confirmação e reenvio de e-mail, login, renovação de sessão, logout, consulta do usuário atual e recuperação de senha | `/auth`, `/password` |
| Instituições e empregadores | Cadastro particular de pessoas jurídicas/instituições e empregadores pessoa física ou jurídica; consulta, edição e exclusão conforme vínculos existentes | `/legal-entities`, `/employers/individual`, `/employers/legal-entity` |
| Categorias | Consulta de categorias disponíveis e gerenciamento das categorias pessoais de movimentações | `/categories` |
| Contas e movimentações | Contas bancárias, benefícios, FGTS e dinheiro em espécie; registro e consulta de entradas/saídas, exclusão de lançamentos permitidos e consulta de saldo | `/pockets` |
| Transferências | Registro atômico de débito e crédito entre contas elegíveis e saque de FGTS | `/transfers`, `/transfers/fgts/withdrawal` |
| Cartões e faturas | Cadastro de cartões, compras à vista ou parceladas, consulta e alteração do plano completo, consulta de parcelas, fechamento/reabertura de faturas, registro de pagamento e créditos de estorno | `/credit-cards`, `/credit-cards/{id}/purchases`, `/credit-cards/{id}/bills` |
| Investimentos | Cadastro de renda fixa e previdência, atualização de posição, histórico, aportes, retirada de rendimentos, estimativa de imposto e resgate integral | `/assets`, `/assets/{id}/transactions`, `/assets/{id}/tax-suggestion` |
| Grupos e convites | Criação de grupos, membros, transferência de responsabilidade, saída/exclusão, envio e resposta a convites e controle de permissões de compartilhamento | `/groups`, `/invitations`, `/groups/{id}/share` |
| Painéis pessoais e de grupos | Visão geral, resumos financeiros e mensais, exposição por emissor, indexador e liquidez e lucro líquido de investimentos resgatados | `/dashboard`, `/groups/{groupId}/dashboard` |
| Contas recorrentes | Cadastro e edição com controle de versão, listagem paginada, pausa/reativação, projeção mensal de vencimentos e lembretes individuais por e-mail | `/recurring-bills`, `/recurring-bills/upcoming?month=YYYY-MM` |
| Notificações | Fluxo de eventos do servidor para o cliente por Server-Sent Events (SSE) | `/notifications/stream` |
| Administração | Consulta de tipos de conta bancária, tipos de benefício e bandeiras; criação, edição e exclusão de categorias globais, com autoridade `ADMIN` | `/admin` |

Os cadastros particulares são vinculados ao usuário autenticado. Participar de um grupo não permite utilizar pessoas jurídicas ou empregadores particulares de outro usuário na criação de contas e cartões.

### Regras financeiras principais

- Novas transferências vinculam suas duas movimentações. Os lançamentos vinculados a transferências ou pagamentos de fatura não podem ser excluídos isoladamente, nem apagados por exclusão de uma conta que os contenha.
- A criação de uma compra salva todas as parcelas na mesma transação. O parcelamento pode ser automático ou personalizado, com 1 a 120 parcelas, e a soma precisa coincidir com o total da compra. A compra à vista tem uma parcela.
- Apenas faturas `OPEN`, sem referência de pagamento, permitem alteração de composição. Faturas `CLOSED` exigem reabertura explícita; faturas `PAID` não podem ser reabertas. O cadastro individual de parcelas foi retirado.
- Estornos são créditos em uma fatura aberta escolhida explicitamente. Podem referenciar a compra original; nesse caso, a soma dos estornos vinculados não pode superar o valor da compra, e a compra referenciada não pode ser corrigida ou excluída.
- O registro de pagamento exige fatura fechada e total positivo. Total zero ou saldo credor permanece na fatura, sem débito ou transporte automático de crédito.
- Pagamentos, transferências e alterações de contas/cartões usam proteção de concorrência no banco. A API não oferece chave de idempotência para cadastros; repetir uma requisição pode criar outro registro.
- Investimentos continuam com resgate integral, sem venda parcial ou recompra. O bruto do resgate deve ser positivo, com imposto não negativo e menor ou igual ao bruto.

As proteções de transferências e faturas não reconstroem automaticamente vínculos e inconsistências de registros antigos. O módulo de investimentos mantém o contrato legado descrito na OpenAPI, com uma posição por ativo e resgate integral.

## Executar com Docker Compose

### Pré-requisitos

- Docker com Docker Compose.
- Um servidor SMTP acessível pela aplicação.

O build da aplicação ocorre no Docker; Java e Maven não precisam estar instalados no host para esse modo de execução.

### 1. Obter o projeto

```bash
git clone https://github.com/ronaldobertolucci/unita-api.git
cd unita-api
cp .env.example .env
```

### 2. Configurar o ambiente

Edite `.env` e substitua os valores de exemplo e placeholders. A aplicação usa o perfil `prod` no Compose.

Exemplo de configuração, com valores ilustrativos:

```env
# PostgreSQL: usar o nome do serviço como host dentro do Compose.
DATASOURCE_URL=jdbc:postgresql://db:5432/unita
DATASOURCE_USERNAME=unita
DATASOURCE_PASSWORD=substitua-a-senha-do-banco
POSTGRES_DB=unita
POSTGRES_USER=unita
POSTGRES_PASSWORD=substitua-a-senha-do-banco

# Assinatura dos tokens: definir um segredo próprio.
API_SECURITY_TOKEN_PASSWORD=substitua-por-um-segredo-aleatorio

# Administrador inicial: habilitar somente quando desejar criar essa conta.
ADMIN_INIT_ENABLED=false
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=substitua-a-senha-do-admin
ADMIN_FIRSTNAME=Admin
ADMIN_LASTNAME=User

# Frontend e e-mail.
CORS_ALLOWED_ORIGINS=http://localhost:4200
APP_FRONTEND_URL=http://localhost:4200
APP_NAME=Unita
APP_EMAIL_FROM=noreply@example.com
APP_EMAIL_VERIFICATION_EXPIRY_HOURS=24
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=usuario@example.com
MAIL_PASSWORD=substitua-a-senha-smtp
MAIL_AUTH=true
MAIL_STARTTLS=true
PASSWORD_RESET_TOKEN_EXPIRY_HOURS=24
PASSWORD_RESET_CLEANUP_CRON=0 0 2 * * ?

# Sessões: padrão para HTTP em rede local.
AUTH_COOKIE_SECURE=false
AUTH_COOKIE_SAME_SITE=Lax
AUTH_REFRESH_EXPIRATION_DAYS=7
AUTH_REFRESH_CLEANUP_CRON=0 30 2 * * ?

# Lembretes de recorrentes.
RECURRING_SCHEDULE_CRON=0 */5 * * * *
RECURRING_PROCESS_CRON=30 * * * * *
RECURRING_BATCH_SIZE=100
RECURRING_MAX_ATTEMPTS=5
RECURRING_RESERVATION_SECONDS=300
RECURRING_PANEL_PATH=/recurring-bills
```

`DATASOURCE_USERNAME`/`DATASOURCE_PASSWORD` devem corresponder a `POSTGRES_USER`/`POSTGRES_PASSWORD`; o banco da URL deve corresponder a `POSTGRES_DB`. Dentro do Compose, `localhost` aponta para o próprio contêiner, portanto use `db` na URL do PostgreSQL e um endereço SMTP acessível a partir do contêiner.

`ADMIN_INIT_ENABLED=true` cria o administrador se o e-mail ainda não existir, já habilitado e com e-mail confirmado. A configuração não altera a senha de um usuário existente. Após criar a conta inicial, é possível desabilitar essa inicialização. Mesmo com inicialização desligada, preencher as variáveis `ADMIN_*` referenciadas pelo perfil.

As origens CORS devem ser explícitas, separadas por vírgula e sem caminhos ou curingas. `APP_FRONTEND_URL` deve apontar para a origem real HTTP(S) do frontend; os links de confirmação, recuperação de senha e recorrentes dependem dessa configuração.

### 3. Subir os serviços

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

| Serviço | Função | Acesso |
|---|---|---|
| `db` / `unita-db` | PostgreSQL com volume persistente | Porta 5432 interna |
| `app` / `unita-app` | API no perfil `prod` | Porta 8080 interna |
| `nginx` / `unita-nginx` | Proxy para a API | Porta 80 publicada |

A base HTTP é **`http://localhost/api`**. Não há página de frontend nesse endereço; as rotas estão sob esse prefixo. O Flyway aplica e valida as migrations automaticamente na inicialização, antes do uso dos serviços.

A configuração do proxy já está em `nginx/nginx.conf`. O access log fornecido omite parâmetros da URL e headers de autenticação.

### Logs, atualização e parada

```bash
# Logs da aplicação.
docker compose logs -f app

# Atualizar após mudanças no código, Compose ou .env.
docker compose up -d --build

# Parar serviços preservando o volume do PostgreSQL.
docker compose down
```

`docker compose down -v` também apaga o volume e os dados do banco. Use somente quando essa remoção for intencional. Alterar `POSTGRES_*` no `.env` não reconfigura usuários/bancos de um volume já inicializado.

## Autenticação e acesso

Usuários cadastrados pelo fluxo público precisam confirmar seu e-mail e estar habilitados para acessar a API. Confirmação de e-mail e habilitação administrativa são estados distintos.

O login retorna um JWT de acesso, válido por duas horas, e um cookie `unita_refresh` HttpOnly. A renovação rotaciona o refresh token dentro da validade absoluta da sessão, de sete dias por padrão. Logout e redefinição de senha revogam a renovação; JWTs já emitidos continuam válidos até expirar, sujeitos ao estado atual da conta.

| Operação | Rota |
|---|---|
| Cadastro | `POST /api/auth/register` |
| Confirmação de e-mail | `GET /api/auth/verify-email` |
| Reenvio de confirmação | `POST /api/auth/resend-verification` |
| Login / renovação / logout | `POST /api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` |
| Usuário autenticado | `GET /api/auth/me` |
| Recuperação / redefinição de senha | `POST /api/password/forgot`, `/api/password/reset` |

Recursos protegidos usam `Authorization: Bearer <token>`. Login, refresh e logout exigem `X-CSRF-Protection: 1`; o refresh utiliza o cookie de sessão. Recursos administrativos exigem autoridade `ADMIN`.

O Compose fornecido atende HTTP. Ao colocar a aplicação atrás de HTTPS, configurar `AUTH_COOKIE_SECURE=true`. Frontend e API devem usar hostname e protocolo compatíveis com a política do cookie. Para cookies entre sites diferentes, é necessário `SameSite=None` com `Secure=true`; essa combinação exige HTTPS.

## Recorrentes e tarefas agendadas

Recorrentes são lembretes, sem lançamento financeiro ou informação de pagamento. Permitem valor previsto opcional, periodicidade `MONTHLY`/`YEARLY`, primeiro vencimento, antecedência de 0 a 30 dias, pausa e preferência de e-mail. Os vencimentos usam a data original como âncora e ajustam dias inexistentes para o último dia do mês.

O agendador persiste um lembrete por ocorrência; o processador envia um e-mail por conta ao endereço atual e confirmado do usuário habilitado. A janela começa às 08h da data do lembrete e termina no dia do vencimento, no fuso `America/Sao_Paulo`. Não são enviados avisos de ocorrências vencidas.

| Variável | Padrão | Função |
|---|---|---|
| `RECURRING_SCHEDULE_CRON` | `0 */5 * * * *` | Calcular lembretes a cada cinco minutos |
| `RECURRING_PROCESS_CRON` | `30 * * * * *` | Processar a fila no segundo 30 de cada minuto |
| `RECURRING_BATCH_SIZE` | `100` | Tamanho do lote; de 1 a 100 |
| `RECURRING_MAX_ATTEMPTS` | `5` | Limite de tentativas; de 1 a 20 |
| `RECURRING_RESERVATION_SECONDS` | `300` | Validade da reserva de envio; de 60 a 3600 segundos |
| `RECURRING_PANEL_PATH` | `/recurring-bills` | Caminho do painel na origem `APP_FRONTEND_URL` |

Para desligar os lembretes, definir **ambos** os crons de recorrentes como `-` e recriar a aplicação com `docker compose up -d --build`. Configurar a origem e a rota do painel antes de habilitar os envios no ambiente.

A fila é persistente e permite retomar tentativas após falhas. O padrão usa esperas de 5, 10, 20 e 40 minutos, dentro da janela de envio. Aceitação pelo SMTP não confirma recebimento ou leitura; uma falha entre envio e confirmação no banco pode causar repetição.

Outras tarefas agendadas incluem fechamento de faturas vencidas, atualização de status de investimentos vencidos e limpeza de tokens/sessões expirados. O fuso padrão da aplicação é `America/Sao_Paulo`.

## Executar localmente

### Pré-requisitos e perfis

- **JDK 25**, com `JAVA_HOME` apontando para a instalação do JDK e `java` disponível no `PATH`.
- PostgreSQL 16 e um servidor SMTP acessível, para os fluxos que enviam e-mail.
- Acesso à internet no primeiro uso do Maven Wrapper para baixar Maven e dependências. Não é necessário instalar Maven separadamente.

| Perfil | Banco e serviços | Uso |
|---|---|---|
| `dev` | PostgreSQL e SMTP locais, com valores de desenvolvimento | Executar a API e consultar a OpenAPI |
| `test` | H2 em memória por padrão, migrations Flyway e configurações de teste | Testes automatizados; permite sobrescrever o datasource para PostgreSQL |
| `prod` | Variáveis de ambiente descritas em `.env.example` | Execução pelo Docker Compose ou ambiente de produção |

Selecione o perfil explicitamente. O arquivo `.env` é lido pelo Docker Compose; a execução direta com Maven/Java não o carrega automaticamente. Nesse caso, exporte as variáveis ou forneça propriedades Spring na execução. No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

### 1. Preparar PostgreSQL e SMTP

O perfil `dev` usa PostgreSQL em `localhost:5432/postgres`, usuário/senha `postgres`. Se não houver uma instância local, é possível criar uma com Docker:

```bash
docker run -d --name unita-dev-db \
  -e POSTGRES_DB=postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 127.0.0.1:5432:5432 \
  postgres:16

# Aguarde o banco informar que aceita conexões antes de iniciar a API.
docker exec unita-dev-db pg_isready -U postgres -d postgres
```

Se a porta 5432 estiver ocupada, utilize a instância existente ou publique outra porta e ajuste `SPRING_DATASOURCE_URL`. O serviço `db` do Compose não publica a porta no host por padrão, portanto não fica disponível para uma aplicação iniciada diretamente pelo Maven.

Para e-mails, o perfil `dev` espera SMTP em `localhost:1025`, sem autenticação nem STARTTLS. Disponibilize um servidor de desenvolvimento nesse endereço ou configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH` e `MAIL_STARTTLS` para o servidor utilizado. Os links enviados apontam para `http://localhost:4200` por padrão; para alterar a origem no perfil `dev`, use `APP_FRONTEND_URL`.

### 2. Iniciar a aplicação

```bash
java -version
./mvnw -version
./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
```

A base HTTP será **`http://localhost:8080/api`**. O Flyway prepara o schema na inicialização. Use `Ctrl+C` para encerrar a aplicação.

Esse perfil cria o administrador `admin@dev.com`, com senha `dev123456`, quando a inicialização está habilitada e o usuário ainda não existe. Essas credenciais são exclusivas de desenvolvimento. Para desabilitar a criação, defina `ADMIN_INIT_ENABLED=false`.

Exemplo de execução com outro banco e sem criação do administrador:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/unita_dev \
SPRING_DATASOURCE_USERNAME=unita_dev \
SPRING_DATASOURCE_PASSWORD=senha-local \
ADMIN_INIT_ENABLED=false \
./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
```

### Documentação OpenAPI

Com a aplicação em execução no perfil `dev`:

| Formato | Endereço |
|---|---|
| Swagger UI | [Interface interativa](http://localhost:8080/api/swagger-ui/index.html) |
| OpenAPI JSON | [Especificação JSON](http://localhost:8080/api/v3/api-docs) |
| OpenAPI YAML | [Especificação YAML](http://localhost:8080/api/v3/api-docs.yaml) |

A especificação é gerada a partir do código; não há um arquivo OpenAPI estático versionado. Para salvar o contrato da versão em execução:

```bash
curl --fail http://localhost:8080/api/v3/api-docs -o /tmp/unita-openapi.json
```

No perfil **`prod` usado pelo Compose, OpenAPI e Swagger UI estão desabilitados**. Para consumir recursos protegidos, obtenha o JWT em `/api/auth/login` e envie `Authorization: Bearer <token>`. Login, refresh e logout também exigem `X-CSRF-Protection: 1`, conforme a seção de autenticação.

## Testar o projeto

### Suíte padrão com H2

Os testes incluem cenários unitários, de controllers, repositórios, segurança, integração financeira e migrations. O perfil `test` usa H2 em memória no modo PostgreSQL e aplica as migrations pelo Flyway. Não é necessário iniciar a API, PostgreSQL ou um SMTP externo para a suíte padrão; os testes de e-mail usam mocks ou servidor de teste próprio.

```bash
# Executar a suíte padrão.
./mvnw -Dspring.profiles.active=test test

# Executar uma classe específica.
./mvnw -Dspring.profiles.active=test -Dtest=AuthFlowIntegrationTest test

# Validar e gerar o JAR em target/.
./mvnw -Dspring.profiles.active=test clean verify
```

Os relatórios de testes ficam em `target/surefire-reports/`. O perfil `test` desliga os jobs de recorrentes e as limpezas agendadas de refresh e recuperação de senha. Alguns cenários de concorrência e migrations são ignorados quando não há PostgreSQL configurado; uma execução bem-sucedida com H2 não valida esses cenários.

### Cenários com PostgreSQL

Use um banco **exclusivo e descartável de testes**: há cenários que apagam sessões e criam schemas/fixtures. Não use o banco da aplicação. Exemplo com as credenciais esperadas pelos testes de autenticação:

```bash
docker run -d --name unita-test-db \
  -e POSTGRES_DB=unita_test \
  -e POSTGRES_USER=auth_test \
  -e POSTGRES_PASSWORD=auth_test \
  -p 127.0.0.1:55432:5432 \
  postgres:16

# Prossiga quando o banco informar que aceita conexões.
docker exec unita-test-db pg_isready -U auth_test -d unita_test
```

Para autenticação e migração da confirmação de e-mail, `AUTH_TEST_POSTGRES_URL` habilita os cenários PostgreSQL. Essas classes usam usuário e senha `auth_test`:

```bash
AUTH_TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/unita_test \
./mvnw -Dspring.profiles.active=test \
  -Dtest=PostgreSQLAuthFlowIntegrationTest,EmailVerificationMigrationTest test
```

Para concorrência financeira, ciclo de faturas, parcelamentos, estornos e migrations financeiras/recorrentes, informe URL, credenciais, driver e dialeto, sobrescrevendo a configuração H2:

```bash
./mvnw -Dspring.profiles.active=test \
  -Dspring.datasource.url=jdbc:postgresql://localhost:55432/unita_test \
  -Dspring.datasource.username=auth_test \
  -Dspring.datasource.password=auth_test \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
  -Dtest=FinancialConcurrencyIntegrationTest,BillLifecycleIntegrationTest,PurchasePlanIntegrationTest,RefundIntegrationTest,FinancialOperationMigrationTest,RefundMigrationTest,RecurringMigrationTest \
  test
```

Os testes de baseline de investimentos e de migração das instituições usam propriedades próprias e schemas temporários:

```bash
./mvnw -Dspring.profiles.active=test \
  -Dinv001.postgres.url=jdbc:postgresql://localhost:55432/unita_test \
  -Dinv001.postgres.username=auth_test \
  -Dinv001.postgres.password=auth_test \
  -Dinv003.postgres.url=jdbc:postgresql://localhost:55432/unita_test \
  -Dinv003.postgres.username=auth_test \
  -Dinv003.postgres.password=auth_test \
  -Dtest=InvestmentBaselinePostgreSQLTest,InstitutionMigrationTest test
```

Ao terminar, remova o contêiner e seu volume anônimo de testes:

```bash
docker rm -f -v unita-test-db
```

### Gerar e executar o pacote

Depois da validação, o JAR pode ser executado diretamente com o JDK 25 e as dependências externas configuradas:

```bash
./mvnw -Dspring.profiles.active=test clean verify
java -jar target/unita-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Para gerar o pacote sem executar testes, use `./mvnw package -DskipTests`. O Dockerfile também desativa os testes durante o build; execute a validação pertinente antes de publicar uma nova imagem.

## Estrutura do projeto

```text
src/main/java/io/github/ronaldobertolucci/unita/
  controller/    Rotas HTTP
  dto/           Entradas e respostas
  model/         Entidades de domínio
  repository/    Persistência e consultas
  service/       Regras, e-mail e tarefas agendadas
  config/        Segurança, CORS e configuração
  exception/     Tratamento de erros
src/main/resources/
  application*.properties
  db/migration/  Migrations Flyway
src/test/java/   Testes automatizados
src/test/resources/sql/  Consultas usadas pelos testes de integridade
nginx/           Proxy HTTP
```

## Licença

[GNU General Public License v3](LICENSE).
