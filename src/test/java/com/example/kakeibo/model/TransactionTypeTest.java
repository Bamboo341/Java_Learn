package com.example.kakeibo.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TransactionType} のテスト。
 */
class TransactionTypeTest {

    @Test
    @DisplayName("DBの文字列から対応する列挙値に変換できる")
    void fromDbValueReturnsMatchingType() {
        assertEquals(TransactionType.INCOME, TransactionType.fromDbValue("INCOME"));
        assertEquals(TransactionType.EXPENSE, TransactionType.fromDbValue("EXPENSE"));
    }

    @Test
    @DisplayName("未知の文字列を変換しようとすると例外になる")
    void fromDbValueThrowsForUnknownValue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TransactionType.fromDbValue("UNKNOWN"));
        assertTrue(e.getMessage().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("DB保存用の文字列は列挙値の名前と一致する")
    void toDbValueReturnsEnumName() {
        assertEquals("INCOME", TransactionType.INCOME.toDbValue());
        assertEquals("EXPENSE", TransactionType.EXPENSE.toDbValue());
    }

    @Test
    @DisplayName("画面表示用の日本語ラベルを返す")
    void getLabelReturnsJapaneseLabel() {
        assertEquals("収入", TransactionType.INCOME.getLabel());
        assertEquals("支出", TransactionType.EXPENSE.getLabel());
    }
}
