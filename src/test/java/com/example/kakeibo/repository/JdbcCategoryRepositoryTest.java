package com.example.kakeibo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.infra.DatabaseManager;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link JdbcCategoryRepository} の結合テスト(実際のH2データベースを使う)。
 *
 * <p>【学習ポイント】本体アプリはファイルモード(./data/kakeibo にファイルが残る)で
 * H2を使いますが、テストでは in-memory モード(メモリ上だけのDB)を使って分離しています。
 * こうすることで、(1) テストが本体の実データを壊さない、(2) テスト同士が互いに
 * 干渉しない、(3) 実行後の後始末が不要(JVM終了とともに消える)、という利点があります。</p>
 */
class JdbcCategoryRepositoryTest {

    /** テスト専用の in-memory DB。本体のファイルモードDBとは完全に別物。 */
    private static final String TEST_JDBC_URL =
            "jdbc:h2:mem:category-repo-test;DB_CLOSE_DELAY=-1";

    private DatabaseManager databaseManager;
    private JdbcCategoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(TEST_JDBC_URL);
        // 前のテストのデータが残らないよう、毎回すべて削除してテーブルを作り直す。
        // data.sql(サンプルデータ)はあえて投入せず、各テストが必要なデータを
        // 自分で用意する。こうするとテストコードだけを読めば前提条件が分かる。
        try (Connection conn = databaseManager.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP ALL OBJECTS");
        }
        try (Connection conn = databaseManager.getConnection();
             Reader reader = new InputStreamReader(
                     getClass().getResourceAsStream("/schema.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(conn, reader);
        }
        repository = new JdbcCategoryRepository(databaseManager);
    }

    @Test
    @DisplayName("findAll: 全カテゴリを表示順で返す")
    void findAllReturnsCategoriesInDisplayOrder() {
        repository.insert(new Category(null, "娯楽", TransactionType.EXPENSE, 3));
        repository.insert(new Category(null, "給与", TransactionType.INCOME, 1));
        repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 2));

        List<Category> all = repository.findAll();

        assertEquals(3, all.size());
        assertEquals("給与", all.get(0).getName());
        assertEquals("食費", all.get(1).getName());
        assertEquals("娯楽", all.get(2).getName());
    }

    @Test
    @DisplayName("findByType: 指定した収支区分のカテゴリだけを返す")
    void findByTypeReturnsOnlyMatchingCategories() {
        repository.insert(new Category(null, "給与", TransactionType.INCOME, 1));
        repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 2));
        repository.insert(new Category(null, "賞与", TransactionType.INCOME, 3));

        List<Category> incomes = repository.findByType(TransactionType.INCOME);

        assertEquals(2, incomes.size());
        assertEquals("給与", incomes.get(0).getName());
        assertEquals("賞与", incomes.get(1).getName());
    }

    @Test
    @DisplayName("insertとfindById: 登録したカテゴリをIDで取得できる")
    void insertAndFindByIdRoundTrip() {
        long id = repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 5));

        Optional<Category> found = repository.findById(id);

        assertTrue(found.isPresent());
        assertEquals("食費", found.get().getName());
        assertEquals(TransactionType.EXPENSE, found.get().getType());
        assertEquals(5, found.get().getDisplayOrder());
    }

    @Test
    @DisplayName("findById: 存在しないIDは空のOptionalを返す")
    void findByIdReturnsEmptyForMissingId() {
        assertTrue(repository.findById(999L).isEmpty());
    }

    @Test
    @DisplayName("update: カテゴリ名を変更できる")
    void updateChangesName() {
        long id = repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        repository.update(new Category(id, "食費・外食", TransactionType.EXPENSE, 1));

        assertEquals("食費・外食", repository.findById(id).orElseThrow().getName());
    }

    @Test
    @DisplayName("delete: カテゴリを削除できる")
    void deleteRemovesCategory() {
        long id = repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        repository.delete(id);

        assertTrue(repository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("delete: 取引から使用中のカテゴリを消そうとすると外部キー制約違反になる")
    void deleteThrowsWhenCategoryIsInUse() {
        // このテストはRepositoryが「DBの制約違反をDataAccessExceptionに変換する」ことの確認。
        // 使用中カテゴリの削除をユーザー操作として防ぐのはServiceの責務。
        long categoryId = repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));
        JdbcTransactionRepository transactionRepository = new JdbcTransactionRepository(databaseManager);
        transactionRepository.insert(new Transaction(
                java.time.LocalDate.of(2026, 6, 15), 1000, TransactionType.EXPENSE, categoryId, "テスト"));

        assertThrows(DataAccessException.class, () -> repository.delete(categoryId));
    }

    @Test
    @DisplayName("existsByName: 同名カテゴリの有無を判定できる")
    void existsByNameChecksDuplicates() {
        repository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        assertTrue(repository.existsByName("食費"));
        assertFalse(repository.existsByName("旅行"));
    }
}
