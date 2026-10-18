# API de orçamento — Spring Boot e Spring AI

API Java 21/Spring Boot para registrar transações, consultar por categoria e processar áudio com modelos Spring AI/OpenAI. O banco de produção/desenvolvimento local é MySQL; os testes unitários não usam banco, Docker, OpenAI ou rede externa.

## Escopo implementado

- `POST /transactions`: valida descrição, categoria e valor em centavos antes de persistir.
- `GET /transactions/{category}`: lista transações da categoria (`GROCERIES`, `PHARMA` ou `AUTO`).
- `POST /transactions/ai`: recebe um arquivo multipart de áudio, transcreve, interpreta a mensagem, usa as ferramentas da aplicação e gera MP3.
- Respostas de entradas inválidas usam `ProblemDetail` com HTTP 400.
- Valores monetários nas respostas são `BigDecimal`, com duas casas decimais; por exemplo, `8500` centavos são serializados como `85.00`.
- O upload de áudio aceita MIME iniciado por `audio/`, rejeita arquivo vazio e limita o tamanho a 10 MB. A validação usa o MIME informado pelo multipart; ela não substitui uma inspeção antivírus do conteúdo.

## Pré-requisitos

- JDK 21;
- Gradle Wrapper (`./gradlew`);
- Docker e Docker Compose apenas para o caminho MySQL/integração;
- `OPENAI_API_KEY` somente para os testes e o endpoint que realmente chamam OpenAI.

Nunca coloque chaves ou senhas reais no Git. O `.env` está no `.gitignore`.

## Testes offline (verificados)

O teste padrão exclui a tag `integration`. Assim, o build limpo não carrega o contexto Spring com MySQL nem chama OpenAI.

```bash
./gradlew clean test --offline
```

Também é possível executar o build local sem a flag de cache offline:

```bash
./gradlew clean build
```

Os testes unitários atuais cobrem o caso de uso e o controller com MockMvc: `ProblemDetail`, JSON inválido, tipo/tamanho/arquivo vazio no áudio, fluxo de áudio com modelos mockados e serialização monetária. O fluxo de áudio do teste não faz chamadas externas.

## Docker/MySQL (não verificado neste ambiente)

O Docker não estava disponível no ambiente de auditoria (`docker: command not found`), portanto os comandos desta seção não foram executados aqui.

1. Crie o arquivo local de variáveis:

   ```bash
   cp .env.example .env
   # edite .env e troque os dois placeholders por senhas locais
   ```

2. Suba o MySQL:

   ```bash
   docker compose --env-file .env up -d
   docker compose --env-file .env ps
   ```

   O serviço usa o banco `transaction`, usuário `app` e publica o MySQL local na porta `3307`. As credenciais do compose vêm de `MYSQL_ROOT_PASSWORD` e `MYSQL_PASSWORD`; o arquivo `.env` não deve ser commitado.

3. Inicie a aplicação:

   ```bash
   ./gradlew bootRun
   ```

   A inicialização da aplicação com Docker/MySQL e o teste de persistência ponta a ponta permanecem **não verificados** nesta auditoria.

## Testes de integração (não verificados)

`BudgetingApplicationTests` e os testes Spring AI são marcados com `integration` e não rodam em `test`. Para tentar o contexto com serviços preparados, configure explicitamente o ambiente local e execute:

```bash
export RUN_INTEGRATION_TESTS=true
./gradlew integrationTest
```

O contexto precisa de um MySQL acessível e de suas configurações de conexão. Os testes OpenAI continuam desabilitados sem `OPENAI_API_KEY`. A integração não foi executada nesta auditoria porque Docker/MySQL não estavam disponíveis.

## OpenAI e custo (não verificado e potencialmente cobrado)

O endpoint de áudio e estas classes fazem chamadas externas quando habilitados:

- `OpenAiChatClientIT`;
- `OpenAiChatModelIT`;
- `OpenAiSpeechModelIT`;
- `OpenAiTranscriptionModelIT`;
- `ToolCallingIT`.

Todos os testes acima exigem `OPENAI_API_KEY` e são `integration`. O build padrão não fornece essa variável e não faz chamadas pagas. Não foi configurada nem usada chave OpenAI nesta auditoria. Se optar por executá-los, confirme limites e cobrança da sua conta antes:

```bash
export OPENAI_API_KEY="sua-chave-local"
export RUN_INTEGRATION_TESTS=true
./gradlew integrationTest
```

Esse comando não foi executado aqui.

## Exemplos de contrato

Criar transação (o valor é informado em centavos):

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{"description":"Compras do mercado","category":"GROCERIES","amount":8500}'
```

Exemplo de entrada inválida:

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{"description":"   ","category":"GROCERIES","amount":0}'
```

A resposta é HTTP 400 com `Content-Type: application/problem+json`, título `Transação inválida` e detalhe informando a primeira validação encontrada. As respostas HTTP reais desses exemplos dependem de a aplicação e o MySQL estarem executando; não foram capturadas nesta auditoria.

Processar áudio (requer OpenAI e pode gerar custo):

```bash
curl -i -X POST http://localhost:8080/transactions/ai \
  -F 'file=@caminho/para/audio.m4a' \
  --output resposta.mp3
```

## Estrutura

```text
src/main/java/dio/budgeting/
├── application/
├── domain/
├── infrastructure/http/
└── infrastructure/persistence/
```

O Gradle separa os caminhos por tag: `test` é offline por padrão; `integrationTest` executa apenas testes marcados como `integration` e depende dos serviços/configurações descritos acima.
