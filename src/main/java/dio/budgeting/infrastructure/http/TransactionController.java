package dio.budgeting.infrastructure.http;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.InvalidAudioException;
import dio.budgeting.application.InvalidTransactionException;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.domain.Category;
import dio.budgeting.infrastructure.http.request.TransactionRequest;
import dio.budgeting.infrastructure.http.response.TransactionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    static final long DEFAULT_MAX_AUDIO_SIZE_BYTES = 10 * 1024 * 1024;

    private final PersistTransactionUseCase persistTransactionUseCase;
    private final ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;
    private final TextToSpeechModel textToSpeechModel;
    private final long maxAudioSizeBytes;

    @Autowired
    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 TranscriptionModel transcriptionModel,
                                 @Value("classpath:prompts/system-message.st") Resource systemPrompt,
                                 ChatClient.Builder chatClientBuilder,
                                 TextToSpeechModel textToSpeechModel,
                                 @Value("${app.audio.max-size-bytes:10485760}") long maxAudioSizeBytes) throws IOException {
        this.persistTransactionUseCase = persistTransactionUseCase;
        this.listTransactionsByCategoryUseCase = listTransactionsByCategoryUseCase;
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(Charset.defaultCharset()))
                .defaultTools(persistTransactionUseCase, listTransactionsByCategoryUseCase)
                .build();
        this.textToSpeechModel = textToSpeechModel;
        this.maxAudioSizeBytes = maxAudioSizeBytes;
    }

    public TransactionController(PersistTransactionUseCase persistTransactionUseCase,
                                 ListTransactionsByCategoryUseCase listTransactionsByCategoryUseCase,
                                 TranscriptionModel transcriptionModel,
                                 Resource systemPrompt,
                                 ChatClient.Builder chatClientBuilder,
                                 TextToSpeechModel textToSpeechModel) throws IOException {
        this(persistTransactionUseCase, listTransactionsByCategoryUseCase, transcriptionModel, systemPrompt,
                chatClientBuilder, textToSpeechModel, DEFAULT_MAX_AUDIO_SIZE_BYTES);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        validateTransactionRequest(request);
        var transaction = persistTransactionUseCase.execute(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> readTransactions(@PathVariable Category category) {
        return listTransactionsByCategoryUseCase.execute(category).stream().map(TransactionResponse::from).toList();
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    public ResponseEntity<Resource> transcribe(@RequestParam("file") MultipartFile file) {
        validateAudio(file);
        var userMessage = transcriptionModel.transcribe(file.getResource());
        var result = chatClient.prompt().user(userMessage).call().content();

        byte[] audio = textToSpeechModel.call(result);
        var resource = new ByteArrayResource(audio);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("audio.mp3")
                                .build()
                                .toString())
                .body(resource);
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAudioException("O arquivo de áudio é obrigatório e não pode estar vazio");
        }
        if (file.getSize() > maxAudioSizeBytes) {
            throw new InvalidAudioException("O arquivo de áudio excede o limite de 10 MB");
        }
        var contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            throw new InvalidAudioException("O arquivo deve ter um tipo MIME de áudio");
        }
    }

    private void validateTransactionRequest(TransactionRequest request) {
        if (request == null) {
            throw new InvalidTransactionException("Os dados da transação são obrigatórios");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new InvalidTransactionException("A descrição da transação é obrigatória");
        }
        if (request.amount() == null || request.amount() <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero");
        }
        if (request.category() == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória");
        }
    }
}
