# Unita API

API REST para controle financeiro familiar, desenvolvida com Java 25 e Spring Boot 4.

## Tecnologias

- Java 25 + Spring Boot 4
- PostgreSQL 16
- Flyway (migrations)
- JWT (autenticação stateless)
- Docker + Docker Compose
- Nginx (reverse proxy)

## Pré-requisitos

- [Docker](https://docs.docker.com/get-docker/)
- [Docker Compose](https://docs.docker.com/compose/install/)

## Configuração

### 1. Clone o repositório

```bash
git clone https://github.com/ronaldobertolucci/unita-api.git
cd unita-api
```

### 2. Configure as variáveis de ambiente

```bash
cp .env.example .env
```

Edite o arquivo `.env` com suas configurações:

```env
# Database
DATASOURCE_URL=jdbc:postgresql://unita-db:5432/mydb
DATASOURCE_USERNAME=user
DATASOURCE_PASSWORD=password
POSTGRES_DB=mydb
POSTGRES_USER=user
POSTGRES_PASSWORD=password

# JWT
API_SECURITY_TOKEN_PASSWORD=your-secret-key-here

# Admin (usuário administrador criado na inicialização)
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=change-me
ADMIN_INIT_ENABLED=true
ADMIN_FIRSTNAME=Admin
ADMIN_LASTNAME=User

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:4200

# Email (para recuperação de senha)
APP_EMAIL_FROM=noreply@seudominio.com
APP_FRONTEND_URL=http://localhost:4200
APP_NAME=Unita
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=usuario@example.com
MAIL_PASSWORD=senha
MAIL_AUTH=true
MAIL_STARTTLS=true
PASSWORD_RESET_TOKEN_EXPIRY_HOURS=24
PASSWORD_RESET_CLEANUP_CRON=0 0 2 * * ?
```

> **Atenção:** para a conexão com o banco funcionar dentro do Docker Compose, use `unita-db` ou `db` como host no `DATASOURCE_URL` (nome do serviço), conforme o exemplo acima.

### 3. Configure o Nginx

Crie o arquivo de configuração do Nginx em `nginx/nginx.conf`:

```nginx
server {
    listen 80;
    server_name app;

    location / {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Execução

### Subir todos os serviços

```bash
docker compose up -d
```

Isso irá iniciar:
- `unita-db` — PostgreSQL na porta 5432 (interna)
- `unita-app` — aplicação Spring Boot na porta 8080 (interna)
- `unita-nginx` — Nginx na porta **80** (pública)

A aplicação estará disponível em `http://localhost/api`.

> Na primeira execução, o Docker irá compilar a imagem da aplicação (pode levar alguns minutos). As migrations do Flyway são aplicadas automaticamente na inicialização.

### Verificar logs

```bash
# Todos os serviços
docker compose logs -f

# Apenas a aplicação
docker compose logs -f app
```

### Parar os serviços

```bash
docker compose down
```

Para remover também o volume do banco de dados:

```bash
docker compose down -v
```

## Estrutura dos Serviços

| Serviço | Imagem | Porta exposta |
|---------|--------|---------------|
| `app` | Build local | — (interna 8080) |
| `db` | postgres:16 | — (interna 5432) |
| `nginx` | nginx:stable-alpine | **80** |

## Licença

[GNU General Public License v3](https://github.com/ronaldobertolucci/unita-api?tab=GPL-3.0-1-ov-file)