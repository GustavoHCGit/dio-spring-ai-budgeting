package dio.budgeting;

import dio.budgeting.application.ListTransactionsByCategoryUseCase;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.infrastructure.http.ApiExceptionHandler;
import dio.budgeting.infrastructure.http.TransactionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.Charset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class TransactionControllerTest {
    private PersistTransactionUseCase persistTransactionUseCase;
    private TranscriptionModel transcriptionModel;
    private ChatClient chatClient;
    private TextToSpeechModel textToSpeechModel;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        persistTransactionUseCase = mock(PersistTransactionUseCase.class);
        var listTransactionsByCategoryUseCase = mock(ListTransactionsByCategoryUseCase.class);
        transcriptionModel = mock(TranscriptionModel.class);
        chatClient = mock(ChatClient.class);
        textToSpeechModel = mock(TextToSpeechModel.class);
        var systemPrompt = mock(Resource.class);
        when(systemPrompt.getContentAsString(any(Charset.class))).thenReturn("system");
        var chatClientBuilder = mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        var controller = new TransactionController(
                persistTransactionUseCase,
                listTransactionsByCategoryUseCase,
                transcriptionModel,
                systemPrompt,
                chatClientBuilder,
                textToSpeechModel,
                4);
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnProblemDetailForInvalidTransaction() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":" ","category":"GROCERIES","amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Transação inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("A descrição da transação é obrigatória"));

        verifyNoInteractions(persistTransactionUseCase);
    }

    @Test
    void shouldReturnProblemDetailForMalformedTransactionJson() throws Exception {
        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Mercado\",\"category\":\"UNKNOWN\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(persistTransactionUseCase);
    }

    @Test
    void shouldRejectEmptyAudioWithoutCallingExternalModels() throws Exception {
        var file = new MockMultipartFile("file", "empty.wav", "audio/wav", new byte[0]);

        mockMvc.perform(multipart("/transactions/ai").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Áudio inválido"))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(transcriptionModel, chatClient, textToSpeechModel);
    }

    @Test
    void shouldRejectNonAudioFileWithoutCallingExternalModels() throws Exception {
        var file = new MockMultipartFile("file", "input.txt", "text/plain", "no".getBytes());

        mockMvc.perform(multipart("/transactions/ai").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Áudio inválido"))
                .andExpect(jsonPath("$.detail").value("O arquivo deve ter um tipo MIME de áudio"));

        verifyNoInteractions(transcriptionModel, chatClient, textToSpeechModel);
    }

    @Test
    void shouldRejectAudioAboveConfiguredLimitWithoutCallingExternalModels() throws Exception {
        var file = new MockMultipartFile("file", "large.wav", "audio/wav", new byte[]{1, 2, 3, 4, 5});

        mockMvc.perform(multipart("/transactions/ai").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Áudio inválido"))
                .andExpect(jsonPath("$.detail").value("O arquivo de áudio excede o limite de 10 MB"));

        verifyNoInteractions(transcriptionModel, chatClient, textToSpeechModel);
    }

    @Test
    void shouldProcessAudioWithMockedModels() throws Exception {
        var prompt = mock(ChatClient.ChatClientRequestSpec.class);
        var response = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(prompt);
        when(prompt.user(anyString())).thenReturn(prompt);
        when(prompt.call()).thenReturn(response);
        when(response.content()).thenReturn("Resposta sintetizada");
        when(transcriptionModel.transcribe(any(Resource.class))).thenReturn("gastei 10 reais");
        when(textToSpeechModel.call("Resposta sintetizada")).thenReturn(new byte[]{1, 2});
        var file = new MockMultipartFile("file", "input.wav", "audio/wav", new byte[]{1, 2});

        mockMvc.perform(multipart("/transactions/ai").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("audio.mp3")))
                .andExpect(content().bytes(new byte[]{1, 2}));

        verify(transcriptionModel).transcribe(any(Resource.class));
        verify(textToSpeechModel).call("Resposta sintetizada");
    }

    @Test
    void shouldSerializeMoneyAsDecimalWithoutFloatingPoint() throws Exception {
        var transaction = new Transaction("Mercado", 8500, Category.GROCERIES);
        when(persistTransactionUseCase.execute(any())).thenReturn(TransactionOutput.from(transaction));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Mercado\",\"category\":\"GROCERIES\",\"amount\":8500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(85.00));
    }
}
