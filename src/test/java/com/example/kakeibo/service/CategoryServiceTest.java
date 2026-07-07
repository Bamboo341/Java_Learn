package com.example.kakeibo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link CategoryService} のテスト。
 *
 * <p>Repositoryにはテスト用のインメモリ実装を注入し、DBなしで
 * バリデーションと業務ルールだけを検証します。</p>
 */
class CategoryServiceTest {

    private InMemoryCategoryRepository categoryRepository;
    private InMemoryTransactionRepository transactionRepository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        categoryRepository = new InMemoryCategoryRepository();
        transactionRepository = new InMemoryTransactionRepository();
        service = new CategoryService(categoryRepository, transactionRepository);
    }

    @Test
    @DisplayName("addCategory: 正しい入力なら追加され、表示順は最後尾+1になる")
    void addCategoryStoresWithNextDisplayOrder() throws Exception {
        categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 5));

        long id = service.addCategory("旅行", TransactionType.EXPENSE);

        Category added = categoryRepository.findById(id).orElseThrow();
        assertEquals("旅行", added.getName());
        assertEquals(TransactionType.EXPENSE, added.getType());
        assertEquals(6, added.getDisplayOrder());
    }

    @Test
    @DisplayName("addCategory: 名前の前後の空白は取り除いて登録する")
    void addCategoryTrimsName() throws Exception {
        long id = service.addCategory("  旅行  ", TransactionType.EXPENSE);

        assertEquals("旅行", categoryRepository.findById(id).orElseThrow().getName());
    }

    @Test
    @DisplayName("addCategory: 名前が未入力(null・空白のみ)ならエラー")
    void addCategoryRejectsBlankName() {
        assertThrows(ValidationException.class,
                () -> service.addCategory(null, TransactionType.EXPENSE));
        assertThrows(ValidationException.class,
                () -> service.addCategory("   ", TransactionType.EXPENSE));
    }

    @Test
    @DisplayName("addCategory: 名前は50文字ちょうどまで許可し、51文字はエラー")
    void addCategoryChecksNameLength() throws Exception {
        service.addCategory("あ".repeat(50), TransactionType.EXPENSE);  // 50文字はOK

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.addCategory("い".repeat(51), TransactionType.EXPENSE));
        assertTrue(e.getMessage().contains("50文字"));
    }

    @Test
    @DisplayName("addCategory: 収支区分がnullならエラー")
    void addCategoryRejectsNullType() {
        assertThrows(ValidationException.class, () -> service.addCategory("旅行", null));
    }

    @Test
    @DisplayName("addCategory: 同名のカテゴリが既にあればエラー")
    void addCategoryRejectsDuplicateName() {
        categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.addCategory("食費", TransactionType.EXPENSE));
        assertTrue(e.getMessage().contains("既に存在"));
    }

    @Test
    @DisplayName("renameCategory: 名前を変更でき、区分と表示順は保たれる")
    void renameCategoryKeepsTypeAndOrder() throws Exception {
        long id = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 3));

        service.renameCategory(id, "食費・外食");

        Category renamed = categoryRepository.findById(id).orElseThrow();
        assertEquals("食費・外食", renamed.getName());
        assertEquals(TransactionType.EXPENSE, renamed.getType());
        assertEquals(3, renamed.getDisplayOrder());
    }

    @Test
    @DisplayName("renameCategory: 同じ名前のままの保存はエラーにしない")
    void renameCategoryAllowsUnchangedName() throws Exception {
        long id = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        service.renameCategory(id, "食費");  // 例外にならないこと

        assertEquals("食費", categoryRepository.findById(id).orElseThrow().getName());
    }

    @Test
    @DisplayName("renameCategory: 他のカテゴリと同名になる変更はエラー")
    void renameCategoryRejectsDuplicateName() {
        categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));
        long id = categoryRepository.insert(new Category(null, "娯楽", TransactionType.EXPENSE, 2));

        assertThrows(ValidationException.class, () -> service.renameCategory(id, "食費"));
    }

    @Test
    @DisplayName("renameCategory: 名前が未入力ならエラー")
    void renameCategoryRejectsBlankName() {
        long id = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));

        assertThrows(ValidationException.class, () -> service.renameCategory(id, ""));
    }

    @Test
    @DisplayName("renameCategory: 存在しないカテゴリの変更はエラー")
    void renameCategoryRejectsMissingCategory() {
        assertThrows(ValidationException.class, () -> service.renameCategory(999L, "旅行"));
    }

    @Test
    @DisplayName("deleteCategory: 未使用のカテゴリは削除できる")
    void deleteCategoryRemovesUnusedCategory() throws Exception {
        long id = categoryRepository.insert(new Category(null, "旅行", TransactionType.EXPENSE, 1));

        service.deleteCategory(id);

        assertTrue(categoryRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("deleteCategory: 取引で使用中のカテゴリは削除できない")
    void deleteCategoryRejectsCategoryInUse() {
        long id = categoryRepository.insert(new Category(null, "食費", TransactionType.EXPENSE, 1));
        transactionRepository.insert(new Transaction(LocalDate.of(2026, 6, 15), 3000,
                TransactionType.EXPENSE, id, "スーパー"));

        ValidationException e = assertThrows(ValidationException.class,
                () -> service.deleteCategory(id));
        assertTrue(e.getMessage().contains("使用中"));
        // カテゴリが消えていないことも確認する
        assertTrue(categoryRepository.findById(id).isPresent());
    }
}
