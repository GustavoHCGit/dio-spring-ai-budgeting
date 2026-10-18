package dio.budgeting.infrastructure.http;

import dio.budgeting.application.InvalidTransactionException;
import dio.budgeting.application.InvalidAudioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidTransactionException.class)
    public ProblemDetail handleInvalidTransaction(InvalidTransactionException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Transação inválida");
        return problem;
    }

    @ExceptionHandler(InvalidAudioException.class)
    public ProblemDetail handleInvalidAudio(InvalidAudioException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Áudio inválido");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("A requisição contém dados inválidos");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Requisição inválida");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableMessage() {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "O corpo da requisição contém dados inválidos");
        problem.setTitle("Requisição inválida");
        return problem;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingAudioPart() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "O arquivo de áudio é obrigatório");
        problem.setTitle("Áudio inválido");
        return problem;
    }
}
