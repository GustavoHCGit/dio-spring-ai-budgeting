package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class PersistTransactionUseCase {
    private final TransactionRepository transactionRepository;

    public PersistTransactionUseCase(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "persist-transaction", description = "Persiste uma nova transação financeira")
    public TransactionOutput execute(PersistTransactionInput input) {
        validate(input);
        var transaction = transactionRepository.save(
                new Transaction(input.description(), input.amount(), input.category()));

        return TransactionOutput.from(transaction);
    }

    private void validate(PersistTransactionInput input) {
        if (input == null) {
            throw new InvalidTransactionException("Os dados da transação são obrigatórios");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw new InvalidTransactionException("A descrição da transação é obrigatória");
        }
        if (input.amount() <= 0) {
            throw new InvalidTransactionException("O valor da transação deve ser maior que zero");
        }
        if (input.category() == null) {
            throw new InvalidTransactionException("A categoria da transação é obrigatória");
        }
    }
}
