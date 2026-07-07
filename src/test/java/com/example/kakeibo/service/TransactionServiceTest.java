package com.example.kakeibo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link TransactionService} のテスト。
 *
 * <p>Repositoryにはテスト用のインメモリ実装を注入し、DBなしで
 * バリデーションと業務ルールだけを検証します。</p>
 */
class TransactionServiceTest {

    private InMemoryTransactionRepository transactionRepository;
    private InMemoryCategoryRepository categoryRepository;
    private TransactionService service;

    /** テスト用カテゴリのID */
    private long foodCategoryId;    // 食費(支出)
    private long salaryCategoryId;  // 給与(収入)

    @BeforeEach
    void setUp() {
        transactionRepository = new InMemoryTransactionRepository();
        categoryRepository = new InMemoryCategoryRepository();
        service = new TransactionService(transactionRepository, categoryRepository);

        foodCategoryId = categoryRepository.insert(
                new Category(null, "食費", TransactionType.EXPENSE, 1));
        salaryCategoryId = categoryRepository.insert(
                new Category(null, "給与", TransactionType.INCOME, 2));
    }

    /** 正しい内容の支出取引を作るヘルパー(テストごとに一部だけ変えて使う)。 */
    private Transaction validExpense() {
        return new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, foodCategoryId, "スーパー");
    }

    @Test
    @DisplayName("register: 正しい入力なら登録されIDが返る")
    void registerStoresValidTransaction() throws Exception {
        long id = service.register(validExpense());

        Transaction stored = transactionRepository.findById(id).orElseThrow();
        assertEquals(3000, stored.getAmount());
        assertEquals(foodCategoryId, stored.getCategoryId());
    }

    @Test
    @DisplayName("register: 日付がnullならエラー")
    void registerRejectsNullDate() {
        Transaction input = new Transaction(null, 3000,
                TransactionType.EXPENSE, foodCategoryId, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(input));
        assertTrue(e.getMessage().contains("日付"));
    }

    @Test
    @DisplayName("register: 収支区分がnullならエラー")
    void registerRejectsNullType() {
        Transaction input = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                null, foodCategoryId, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(input));
        assertTrue(e.getMessage().contains("収支区分"));
    }

    @Test
    @DisplayName("register: 金額が0円以下ならエラー")
    void registerRejectsNonPositiveAmount() {
        Transaction zero = new Transaction(LocalDate.of(2026, 6, 15), 0,
                TransactionType.EXPENSE, foodCategoryId, null);
        Transaction negative = new Transaction(LocalDate.of(2026, 6, 15), -500,
                TransactionType.EXPENSE, foodCategoryId, null);

        assertThrows(ValidationException.class, () -> service.register(zero));
        assertThrows(ValidationException.class, () -> service.register(negative));
    }

    @Test
    @DisplayName("register: 存在しないカテゴリならエラー")
    void registerRejectsUnknownCategory() {
        Transaction input = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, 999L, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(input));
        assertTrue(e.getMessage().contains("カテゴリ"));
    }

    @Test
    @DisplayName("register: カテゴリの区分と取引の区分が食い違っていたらエラー")
    void registerRejectsCategoryTypeMismatch() {
        // 「給与」カテゴリ(収入用)を支出に使おうとするケース
        Transaction input = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, salaryCategoryId, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(input));
        assertTrue(e.getMessage().contains("給与"));
    }

    @Test
    @DisplayName("register: メモは200文字ちょうどまで許可し、201文字はエラー")
    void registerChecksMemoLength() throws Exception {
        Transaction just = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, foodCategoryId, "あ".repeat(200));
        Transaction tooLong = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, foodCategoryId, "あ".repeat(201));

        service.register(just);  // 200文字は例外にならない
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.register(tooLong));
        assertTrue(e.getMessage().contains("200文字"));
    }

    @Test
    @DisplayName("register: メモは未入力(null)でもよい")
    void registerAllowsNullMemo() throws Exception {
        Transaction input = new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, foodCategoryId, null);

        long id = service.register(input);

        assertTrue(transactionRepository.findById(id).isPresent());
    }

    @Test
    @DisplayName("update: 正しい入力なら内容が更新される")
    void updateChangesStoredTransaction() throws Exception {
        long id = service.register(validExpense());

        service.update(new Transaction(id, LocalDate.of(2026, 6, 16), 5000,
                TransactionType.EXPENSE, foodCategoryId, "外食", null));

        Transaction updated = transactionRepository.findById(id).orElseThrow();
        assertEquals(5000, updated.getAmount());
        assertEquals("外食", updated.getMemo());
    }

    @Test
    @DisplayName("update: 存在しない取引の更新はエラー")
    void updateRejectsMissingTransaction() {
        Transaction input = new Transaction(999L, LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, foodCategoryId, null, null);

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.update(input));
        assertTrue(e.getMessage().contains("見つかりません"));
    }

    @Test
    @DisplayName("update: 更新でもバリデーションが働く")
    void updateAppliesValidation() throws Exception {
        long id = service.register(validExpense());
        Transaction invalid = new Transaction(id, LocalDate.of(2026, 6, 15), -100,
                TransactionType.EXPENSE, foodCategoryId, null, null);

        assertThrows(ValidationException.class, () -> service.update(invalid));
    }

    @Test
    @DisplayName("update: IDのない取引を渡すのはプログラムミスとして扱う")
    void updateRejectsTransactionWithoutId() {
        assertThrows(IllegalArgumentException.class, () -> service.update(validExpense()));
    }

    @Test
    @DisplayName("delete: 取引を削除できる")
    void deleteRemovesTransaction() throws Exception {
        long id = service.register(validExpense());

        service.delete(id);

        assertTrue(transactionRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("search: 条件を指定して検索できる(Repositoryへの委譲)")
    void searchDelegatesToRepository() throws Exception {
        service.register(validExpense());
        service.register(new Transaction(LocalDate.of(2026, 6, 25), 280000,
                TransactionType.INCOME, salaryCategoryId, "給与"));

        List<Transaction> incomes = service.search(YearMonth.of(2026, 6), null,
                TransactionType.INCOME);

        assertEquals(1, incomes.size());
        assertEquals(280000, incomes.get(0).getAmount());
    }
}
