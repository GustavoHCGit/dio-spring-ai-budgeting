# API Inteligente de Orçamento — Spring Boot e Spring AI

Projeto final da trilha **Java Itaú com Inteligência Artificial**, baseado no desafio da DIO. A aplicação administra transações financeiras e permite processar comandos de voz usando Spring AI.

## O que o projeto faz

A API oferece dois fluxos principais:

- criação de transações via REST;
- consulta de transações por categoria;
- envio de áudio para transcrição, interpretação com IA, execução de uma função real da aplicação e geração de resposta em áudio.

Fluxo de IA:

```text
áudio -> TranscriptionModel -> ChatClient -> Tool Calling -> caso de uso -> TextToSpeechModel -> MP3
```

A aplicação mantém as responsabilidades separadas em `domain`, `application` e `infrastructure`.

## Melhoria implementada

Foi adicionada **validação centralizada de transações** no `PersistTransactionUseCase`. A regra é aplicada tanto ao endpoint REST quanto ao Tool Calling da IA.

A API rejeita:

- dados ausentes;
- descrição vazia ou apenas com espaços;
- valor menor ou igual a zero;
- categoria inexistente.

As entradas inválidas retornam HTTP `400` no formato `ProblemDetail`, com título e mensagem explicando o problema. A validação no caso de uso evita que uma chamada REST e uma chamada feita pela IA tenham regras diferentes.

## Tecnologias

- Java 21;
- Spring Boot 4;
- Spring AI 2.0.0-M4;
- Spring Web;
- Spring Data JPA;
- MySQL 9;
- Docker Compose;
- Gradle;
- JUnit 5 e AssertJ;
- OpenAI para chat, transcrição e síntese de voz.

## Pré-requisitos

- JDK 21;
- Docker e Docker Compose;
- uma chave de API compatível com OpenAI para os fluxos de IA.

A chave não deve ser colocada no código nem commitada no Git.

## Como executar

Entre nesta pasta:

```bash
cd dio-spring-ai-budgeting
```

Suba o banco:

```bash
docker compose up -d
```

Configure a chave apenas na sessão local do terminal:

```bash
export OPENAI_API_KEY="sua-chave-aqui"
```

Não coloque chaves reais em ficheiros versionados.

Execute os testes:

```bash
./gradlew test
```

Inicie a aplicação:

```bash
./gradlew bootRun
```

O MySQL é exposto localmente na porta `3307`. A aplicação usa as configurações do Spring Boot Docker Compose para iniciar a infraestrutura de desenvolvimento.

## Exemplos de uso

### Criar uma transação válida

```bash
curl -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "description": "Compras do mercado",
    "category": "GROCERIES",
    "amount": 8500
  }'
```

O campo `amount` é informado em centavos. No exemplo, `8500` representa R$ 85,00.

### Validar uma transação inválida

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{
    "description": "   ",
    "category": "GROCERIES",
    "amount": 0
  }'
```

A resposta esperada é HTTP `400`, com um problema semelhante a:

```json
{
  "type": "about:blank",
  "title": "Transação inválida",
  "status": 400,
  "detail": "A descrição da transação é obrigatória"
}
```

### Consultar por categoria

```bash
curl http://localhost:8080/transactions/GROCERIES
```

### Processar um comando de voz

```bash
curl -X POST http://localhost:8080/transactions/ai \
  -F 'file=@caminho/para/audio.m4a' \
  --output resposta.mp3
```

O áudio deve descrever uma operação financeira em português. A resposta é um ficheiro MP3 gerado a partir da resposta textual do assistente.

## Testes

Os testes unitários da melhoria estão em:

```text
src/test/java/dio/budgeting/PersistTransactionUseCaseTest.java
```

Eles cobrem:

- persistência de uma transação válida;
- descrição vazia;
- valor não positivo;
- categoria ausente;
- garantia de que entradas inválidas não chegam ao repositório.

Os testes de integração com OpenAI são executados apenas quando `OPENAI_API_KEY` está definida, para evitar chamadas externas acidentais.

## Estrutura principal

```text
src/main/java/dio/budgeting/
├── application/
│   ├── input/
│   ├── output/
│   ├── InvalidTransactionException.java
│   └── ...UseCase.java
├── domain/
├── infrastructure/http/
└── infrastructure/persistence/
```

## O que foi aprendido

- como integrar Spring AI com uma aplicação Spring Boot;
- como separar domínio, casos de uso e adaptadores;
- como usar `ChatClient` e Tool Calling;
- como transcrever áudio e gerar voz;
- como persistir dados com JPA e MySQL;
- como centralizar regras de validação no caso de uso;
- como transformar uma exceção de negócio numa resposta HTTP padronizada;
- como testar regras de negócio sem depender de banco ou de chamadas externas de IA.

## Observação sobre a entrega

Antes de enviar o projeto à DIO, substitua este README por uma versão revista por si, confirme que os comandos funcionam no seu computador e inclua as suas próprias evidências de execução, como prints, respostas HTTP ou logs de teste.
