package com.example.kakeibo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * {@link TransactionTableModel} のテスト。
 *
 * <p>【学習ポイント】UIレイヤのテストは「ロジック部分」だけを対象にしています。
 * MainFrameやダイアログのような画面そのもののテストは、表示・操作・タイミングが
 * 絡んで難しく、壊れやすいテストになりがちです。だからこそ、表示用のロジックを
 * TableModelのようなクラスに剥がしておき、画面を起動せずにテストできる形に
 * しておくことが大切です。</p>
 */
class TransactionTableModelTest {

    private TransactionTableModel model;

    /** テスト用のカテゴリ対応表(ID→名前) */
    private static final Map<Long, String> CATEGORY_NAMES = Map.of(
            1L, "食費",
            2L, "給与");

    @BeforeEach
    void setUp() {
        model = new TransactionTableModel();
    }

    /** テスト用の取引を作るヘルパー。 */
    private Transaction transaction(long id, String date, int amount,
                                    TransactionType type, long categoryId, String memo) {
        return new Transaction(id, LocalDate.parse(date), amount, type, categoryId, memo, null);
    }

    @Test
    @DisplayName("列は「日付・区分・カテゴリ・金額・メモ」の5つ")
    void columnsAreDefined() {
        assertEquals(5, model.getColumnCount());
        assertEquals("日付", model.getColumnName(0));
        assertEquals("区分", model.getColumnName(1));
        assertEquals("カテゴリ", model.getColumnName(2));
        assertEquals("金額", model.getColumnName(3));
        assertEquals("メモ", model.getColumnName(4));
    }

    @Test
    @DisplayName("setData: 行数が変わり、テーブルへ変更通知が飛ぶ")
    void setDataUpdatesRowsAndNotifiesListeners() {
        AtomicInteger notified = new AtomicInteger(0);
        model.addTableModelListener(e -> notified.incrementAndGet());

        model.setData(List.of(
                transaction(1, "2026-06-15", 3000, TransactionType.EXPENSE, 1L, "スーパー")),
                CATEGORY_NAMES);

        assertEquals(1, model.getRowCount());
        assertEquals(1, notified.get());  // fireTableDataChangedによる通知
    }

    @Test
    @DisplayName("getValueAt: 各列を表示用の文字列に整形する")
    void getValueAtFormatsRowForDisplay() {
        model.setData(List.of(
                transaction(1, "2026-06-15", 3000, TransactionType.EXPENSE, 1L, "スーパー")),
                CATEGORY_NAMES);

        assertEquals("2026-06-15", model.getValueAt(0, 0));
        assertEquals("支出", model.getValueAt(0, 1));
        assertEquals("食費", model.getValueAt(0, 2));
        assertEquals("3,000", model.getValueAt(0, 3));  // 3桁区切り
        assertEquals("スーパー", model.getValueAt(0, 4));
    }

    @Test
    @DisplayName("getValueAt: メモがnullなら空文字で表示する")
    void getValueAtShowsEmptyStringForNullMemo() {
        model.setData(List.of(
                transaction(1, "2026-06-15", 3000, TransactionType.EXPENSE, 1L, null)),
                CATEGORY_NAMES);

        assertEquals("", model.getValueAt(0, 4));
    }

    @Test
    @DisplayName("getValueAt: 対応表にないカテゴリIDは「(不明)」と表示する")
    void getValueAtShowsPlaceholderForUnknownCategory() {
        model.setData(List.of(
                transaction(1, "2026-06-15", 3000, TransactionType.EXPENSE, 999L, null)),
                CATEGORY_NAMES);

        assertEquals("(不明)", model.getValueAt(0, 2));
    }

    @Test
    @DisplayName("セルは直接編集できない")
    void cellsAreNotEditable() {
        model.setData(List.of(
                transaction(1, "2026-06-15", 3000, TransactionType.EXPENSE, 1L, null)),
                CATEGORY_NAMES);

        assertFalse(model.isCellEditable(0, 0));
        assertFalse(model.isCellEditable(0, 3));
    }

    @Test
    @DisplayName("getTransactionAt: 行番号から取引そのものを取得できる")
    void getTransactionAtReturnsUnderlyingTransaction() {
        Transaction first = transaction(1, "2026-06-20", 1000, TransactionType.EXPENSE, 1L, null);
        Transaction second = transaction(2, "2026-06-10", 2000, TransactionType.EXPENSE, 1L, null);
        model.setData(List.of(first, second), CATEGORY_NAMES);

        assertSame(first, model.getTransactionAt(0));
        assertSame(second, model.getTransactionAt(1));
    }

    @Test
    @DisplayName("sumAmount: 区分ごとの金額合計を計算する(ステータスバー用)")
    void sumAmountTotalsByType() {
        model.setData(List.of(
                transaction(1, "2026-06-10", 3000, TransactionType.EXPENSE, 1L, null),
                transaction(2, "2026-06-15", 2000, TransactionType.EXPENSE, 1L, null),
                transaction(3, "2026-06-25", 280000, TransactionType.INCOME, 2L, null)),
                CATEGORY_NAMES);

        assertEquals(5000, model.sumAmount(TransactionType.EXPENSE));
        assertEquals(280000, model.sumAmount(TransactionType.INCOME));
    }

    @Test
    @DisplayName("sumAmount: データがなければ0を返す")
    void sumAmountReturnsZeroWhenEmpty() {
        assertEquals(0, model.sumAmount(TransactionType.EXPENSE));
    }
}
