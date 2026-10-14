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

| Módulo | Funcionamento |
|---|---|
| Usuários e autenticação | Cadastro, confirmação de e-mail, login, renovação de sessão, logout e recuperação de senha |
| Contas e movimentações | Contas bancárias, benefícios, FGTS, dinheiro em espécie, entradas, saídas e consulta de saldo/extrato |
| Transferências | Registro atômico de débito e crédito entre contas elegíveis, incluindo saque de FGTS |
| Cartões | Compras com parcelamento completo, faturas, pagamento registrado em conta e créditos de estorno |
| Investimentos | Renda fixa e previdência, aportes, rendimentos, posição e resgate integral |
| Grupos | Convites, membros e compartilhamento de informações conforme permissões |
| Painéis | Resumos financeiros pessoais e de grupos |
| Recorrentes | Cadastro mensal/anual, projeção de vencimentos e lembretes individuais por e-mail |

Os cadastros particulares são vinculados ao usuário autenticado. Participar de um grupo não permite utilizar pessoas jurídicas ou empregadores particulares de outro usuário na criação de contas e cartões.

### Regras financeiras principais

- Novas transferências vinculam suas duas movimentações. Os lançamentos vinculados a transferências ou pagamentos de fatura não podem ser excluídos isoladamente, nem apagados por exclusão de uma conta que os contenha.
- A criação de uma compra salva todas as parcelas na mesma transação. O parcelamento pode ser automático ou personalizado, com 1 a 120 parcelas, e a soma precisa coincidir com o total da compra. A compra à vista tem uma parcela.
- Apenas faturas `OPEN`, sem referência de pagamento, permitem alteração de composição. Faturas `CLOSED` exigem reabertura explícita; faturas `PAID` não podem ser reabertas. O cadastro individual de parcelas foi retirado.
- Estornos são créditos em uma fatura aberta escolhida explicitamente. Podem referenciar a compra original; nesse caso, a soma dos estornos vinculados não pode superar o valor da compra, e a compra referenciada não pode ser corrigida ou excluída.
- O registro de pagamento exige fatura fechada e total positivo. Total zero ou saldo credor permanece na fatura, sem débito ou transporte automático de crédito.
- Pagamentos, transferências e alterações de contas/cartões usam proteção de concorrência no banco. A API não oferece chave de idempotência para cadastros; repetir uma requisição pode criar outro registro.
- Investimentos continuam com resgate integral, sem venda parcial ou recompra. O bruto do resgate deve ser positivo, com imposto não negativo e menor ou igual ao bruto.

As proteções novas não reconstroem automaticamente vínculos e inconsistências de registros antigos. Transferências históricas sem vínculo explícito e escritores de investimentos conservam suas regras anteriores.

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

## Executar localmente e testar

Para executar fora do Docker, instalar **JDK 25** e disponibilizar PostgreSQL e SMTP. O Maven Wrapper está incluído.

O perfil `dev` usa, por padrão, PostgreSQL em `localhost:5432/postgres`, usuário/senha `postgres`, e SMTP em `localhost:1025`, sem autenticação nem STARTTLS. Ajustar `application-dev.properties` ou fornecer propriedades/variáveis de ambiente equivalentes para o ambiente utilizado. Esse perfil inicializa o administrador `admin@dev.com` com senha de desenvolvimento `dev123456` quando a inicialização está habilitada.

```bash
./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
```

Nesse modo, a base é `http://localhost:8080/api`. Em `dev`, a OpenAPI está em `/api/v3/api-docs` e o Swagger UI em `/api/swagger-ui/index.html`. No perfil **`prod` usado pelo Compose, ambos estão desabilitados**.

```bash
# Testes com perfil test, H2 em memória e Flyway.
./mvnw -Dspring.profiles.active=test test

# Gerar o pacote da aplicação.
./mvnw package -DskipTests
```

O perfil de testes desliga os jobs de recorrentes. Alguns testes de concorrência e atualização de schema só executam quando configurados para PostgreSQL; H2 em modo PostgreSQL não comprova o comportamento do banco real. Para esses cenários, usar um banco exclusivo e descartável de testes, com URL, usuário, senha, driver e dialeto apropriados. Os testes de autenticação em PostgreSQL também utilizam `AUTH_TEST_POSTGRES_URL` e as credenciais de teste definidas na classe correspondente.

O Dockerfile gera o pacote com testes desativados. Execute a validação pertinente antes de publicar uma nova imagem.

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
