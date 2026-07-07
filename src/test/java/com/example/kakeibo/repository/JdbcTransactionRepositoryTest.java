package com.example.kakeibo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.kakeibo.infra.DatabaseManager;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link JdbcTransactionRepository} の結合テスト(実際のH2データベースを使う)。
 *
 * <p>【学習ポイント】本体アプリはファイルモード(./data/kakeibo にファイルが残る)で
 * H2を使いますが、テストでは in-memory モード(メモリ上だけのDB)を使って分離しています。
 * 分離する理由は {@link JdbcCategoryRepositoryTest} のコメントを参照してください。</p>
 */
class JdbcTransactionRepositoryTest {

    /** テスト専用の in-memory DB。本体のファイルモードDBとは完全に別物。 */
    private static final String TEST_JDBC_URL =
            "jdbc:h2:mem:transaction-repo-test;DB_CLOSE_DELAY=-1";

    private DatabaseManager databaseManager;
    private JdbcTransactionRepository repository;

    /** テスト用に登録したカテゴリのID */
    private long foodCategoryId;      // 食費(支出)
    private long hobbyCategoryId;     // 娯楽(支出)
    private long salaryCategoryId;    // 給与(収入)

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(TEST_JDBC_URL);
        // 前のテストのデータが残らないよう、毎回すべて削除してテーブルを作り直す
        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        try (Connection conn = databaseManager.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }
        repository = new JdbcTransactionRepository(databaseManager);

        // 取引はカテゴリへの外部キーを持つため、テスト用カテゴリを先に用意しておく
        JdbcCategoryRepository categoryRepository = new JdbcCategoryRepository(databaseManager);
        foodCategoryId = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));
        hobbyCategoryId = categoryRepository.insert(new Category(null, "娯楽", TransactionType.EXPENSE, 2));
        salaryCategoryId = categoryRepository.insert(new Category(null, "給与", TransactionType.INCOME, 3));
    }

    /** テスト用の取引を1件登録するヘルパー。 */
    private long insertTransaction(String date, int amount, TransactionType type,
                                   long categoryId, String memo) {
        return repository.insert(new Transaction(LocalDate.parse(date), amount, type, categoryId, memo));
    }

    @Test
    @DisplayName("insertとfindById: 登録した取引の全項目をIDで取得できる")
    void insertAndFindByIdRoundTrip() {
        long id = insertTransaction("2026-06-15", 3000, TransactionType.EXPENSE, foodCategoryId, "スーパー");

        Transaction found = repository.findById(id).orElseThrow();

        assertEquals(LocalDate.of(2026, 6, 15), found.getDate());
        assertEquals(3000, found.getAmount());
        assertEquals(TransactionType.EXPENSE, found.getType());
        assertEquals(foodCategoryId, found.getCategoryId());
        assertEquals("スーパー", found.getMemo());
        assertNotNull(found.getCreatedAt());  // created_atはDBが自動設定する
    }

    @Test
    @DisplayName("findById: 存在しないIDは空のOptionalを返す")
    void findByIdReturnsEmptyForMissingId() {
        assertTrue(repository.findById(999L).isEmpty());
    }

    @Test
    @DisplayName("findByConditions: 条件なしなら全件を日付の新しい順で返す")
    void findByConditionsWithoutFilterReturnsAllSortedByDateDesc() {
        insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "1件目");
        insertTransaction("2026-06-20", 2000, TransactionType.EXPENSE, foodCategoryId, "2件目");
        insertTransaction("2026-05-15", 3000, TransactionType.EXPENSE, foodCategoryId, "3件目");

        List<Transaction> results = repository.findByConditions(null, null, null);

        assertEquals(3, results.size());
        assertEquals(LocalDate.of(2026, 6, 20), results.get(0).getDate());
        assertEquals(LocalDate.of(2026, 6, 10), results.get(1).getDate());
        assertEquals(LocalDate.of(2026, 5, 15), results.get(2).getDate());
    }

    @Test
    @DisplayName("findByConditions: 年月で絞り込める")
    void findByConditionsFiltersByMonth() {
        insertTransaction("2026-05-20", 1000, TransactionType.EXPENSE, foodCategoryId, "5月");
        insertTransaction("2026-06-10", 2000, TransactionType.EXPENSE, foodCategoryId, "6月その1");
        insertTransaction("2026-06-20", 3000, TransactionType.EXPENSE, foodCategoryId, "6月その2");

        List<Transaction> results = repository.findByConditions(YearMonth.of(2026, 6), null, null);

        assertEquals(2, results.size());
        assertEquals("6月その2", results.get(0).getMemo());
        assertEquals("6月その1", results.get(1).getMemo());
    }

    @Test
    @DisplayName("findByConditions: カテゴリで絞り込める")
    void findByConditionsFiltersByCategory() {
        insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "食費");
        insertTransaction("2026-06-11", 2000, TransactionType.EXPENSE, hobbyCategoryId, "娯楽");

        List<Transaction> results = repository.findByConditions(null, hobbyCategoryId, null);

        assertEquals(1, results.size());
        assertEquals("娯楽", results.get(0).getMemo());
    }

    @Test
    @DisplayName("findByConditions: 収支区分で絞り込める")
    void findByConditionsFiltersByType() {
        insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "支出");
        insertTransaction("2026-06-25", 280000, TransactionType.INCOME, salaryCategoryId, "収入");

        List<Transaction> results = repository.findByConditions(null, null, TransactionType.INCOME);

        assertEquals(1, results.size());
        assertEquals("収入", results.get(0).getMemo());
    }

    @Test
    @DisplayName("findByConditions: 年月・カテゴリ・区分の組み合わせで絞り込める")
    void findByConditionsCombinesAllFilters() {
        insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "対象");
        insertTransaction("2026-05-10", 2000, TransactionType.EXPENSE, foodCategoryId, "別の月");
        insertTransaction("2026-06-11", 3000, TransactionType.EXPENSE, hobbyCategoryId, "別のカテゴリ");
        insertTransaction("2026-06-25", 280000, TransactionType.INCOME, salaryCategoryId, "別の区分");

        List<Transaction> results = repository.findByConditions(
                YearMonth.of(2026, 6), foodCategoryId, TransactionType.EXPENSE);

        assertEquals(1, results.size());
        assertEquals("対象", results.get(0).getMemo());
    }

    @Test
    @DisplayName("update: 取引の内容を変更できる")
    void updateChangesTransaction() {
        long id = insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "変更前");

        repository.update(new Transaction(id, LocalDate.of(2026, 6, 12), 1500,
                TransactionType.EXPENSE, hobbyCategoryId, "変更後", null));

        Transaction updated = repository.findById(id).orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 12), updated.getDate());
        assertEquals(1500, updated.getAmount());
        assertEquals(hobbyCategoryId, updated.getCategoryId());
        assertEquals("変更後", updated.getMemo());
    }

    @Test
    @DisplayName("delete: 取引を削除できる")
    void deleteRemovesTransaction() {
        long id = insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "削除対象");

        repository.delete(id);

        assertTrue(repository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("countByCategoryId: カテゴリを使っている取引の件数を返す")
    void countByCategoryIdCountsUsage() {
        insertTransaction("2026-06-10", 1000, TransactionType.EXPENSE, foodCategoryId, "1件目");
        insertTransaction("2026-06-11", 2000, TransactionType.EXPENSE, foodCategoryId, "2件目");

        assertEquals(2, repository.countByCategoryId(foodCategoryId));
        assertEquals(0, repository.countByCategoryId(hobbyCategoryId));
    }

    @Test
    @DisplayName("summarizeMonth: 収入合計・支出合計・カテゴリ別支出を集計する")
    void summarizeMonthComputesTotalsAndCategoryBreakdown() {
        insertTransaction("2026-06-25", 280000, TransactionType.INCOME, salaryCategoryId, "給与");
        insertTransaction("2026-06-10", 3000, TransactionType.EXPENSE, foodCategoryId, "食費その1");
        insertTransaction("2026-06-15", 2000, TransactionType.EXPENSE, foodCategoryId, "食費その2");
        insertTransaction("2026-06-20", 1500, TransactionType.EXPENSE, hobbyCategoryId, "娯楽");
        insertTransaction("2026-05-15", 9999, TransactionType.EXPENSE, foodCategoryId, "別の月(対象外)");

        MonthlySummary summary = repository.summarizeMonth(YearMonth.of(2026, 6));

        assertEquals(280000, summary.getIncomeTotal());
        assertEquals(6500, summary.getExpenseTotal());
        assertEquals(273500, summary.getBalance());
        // カテゴリ別支出は金額の大きい順
        assertEquals(2, summary.getCategoryExpenses().size());
        assertEquals("食費", summary.getCategoryExpenses().get(0).getCategoryName());
        assertEquals(5000, summary.getCategoryExpenses().get(0).getAmount());
        assertEquals("娯楽", summary.getCategoryExpenses().get(1).getCategoryName());
        assertEquals(1500, summary.getCategoryExpenses().get(1).getAmount());
    }

    @Test
    @DisplayName("summarizeMonth: 取引のない月はゼロ件の集計を返す")
    void summarizeMonthReturnsZerosForEmptyMonth() {
        insertTransaction("2026-06-15", 3000, TransactionType.EXPENSE, foodCategoryId, "6月のデータ");

        MonthlySummary summary = repository.summarizeMonth(YearMonth.of(2026, 4));

        assertEquals(0, summary.getIncomeTotal());
        assertEquals(0, summary.getExpenseTotal());
        assertEquals(0, summary.getBalance());
        assertTrue(summary.getCategoryExpenses().isEmpty());
    }

    @Test
    @DisplayName("findDistinctMonths: 取引が存在する年月を新しい順で返す")
    void findDistinctMonthsReturnsMonthsSortedDesc() {
        insertTransaction("2026-05-15", 1000, TransactionType.EXPENSE, foodCategoryId, "5月");
        insertTransaction("2026-06-10", 2000, TransactionType.EXPENSE, foodCategoryId, "6月その1");
        insertTransaction("2026-06-20", 3000, TransactionType.EXPENSE, foodCategoryId, "6月その2");
        insertTransaction("2026-07-05", 4000, TransactionType.EXPENSE, foodCategoryId, "7月");

        List<YearMonth> months = repository.findDistinctMonths();

        assertEquals(List.of(YearMonth.of(2026, 7), YearMonth.of(2026, 6), YearMonth.of(2026, 5)), months);
    }
}
