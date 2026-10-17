package dio.budgeting;

import dio.budgeting.application.InvalidTransactionException;
import dio.budgeting.application.PersistTransactionUseCase;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistTransactionUseCaseTest {
    private InMemoryTransactionRepository repository;
    private PersistTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        useCase = new PersistTransactionUseCase(repository);
    }

    @Test
    void shouldPersistValidTransaction() {
        var output = useCase.execute(new PersistTransactionInput("Mercado", 8500, Category.GROCERIES));

        assertThat(output.description()).isEqualTo("Mercado");
        assertThat(output.value()).isEqualTo(85.0);
        assertThat(repository.transactions).hasSize(1);
    }

    @Test
    void shouldRejectBlankDescription() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("  ", 1000, Category.GROCERIES)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A descrição da transação é obrigatória");
        assertThat(repository.transactions).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Mercado", 0, Category.GROCERIES)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("O valor da transação deve ser maior que zero");
        assertThat(repository.transactions).isEmpty();
    }

    @Test
    void shouldRejectMissingCategory() {
        assertThatThrownBy(() -> useCase.execute(new PersistTransactionInput("Mercado", 1000, null)))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessage("A categoria da transação é obrigatória");
        assertThat(repository.transactions).isEmpty();
    }

    private static class InMemoryTransactionRepository implements TransactionRepository {
        private final List<Transaction> transactions = new ArrayList<>();

        @Override
        public Transaction save(Transaction transaction) {
            transactions.add(transaction);
            return transaction;
        }

        @Override
        public List<Transaction> findAllByCategory(Category category) {
            return transactions.stream().filter(t -> t.getCategory() == category).toList();
        }
    }
}
