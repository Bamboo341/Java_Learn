package com.example.kakeibo.model;

import java.time.YearMonth;
import java.util.List;

/**
 * 月次集計の結果を表すDTO(データの入れ物)。
 *
 * <p>収入合計・支出合計・カテゴリ別支出の内訳を持ちます。
 * 収支(バランス)は保持せず、収入と支出から計算で求めます。</p>
 */
public class MonthlySummary {

    /** 集計対象の年月 */
    private final YearMonth yearMonth;

    /** 収入合計(円) */
    private final int incomeTotal;

    /** 支出合計(円) */
    private final int expenseTotal;

    /** カテゴリ別支出の内訳(金額の大きい順) */
    private final List<CategoryExpense> categoryExpenses;

    public MonthlySummary(YearMonth yearMonth, int incomeTotal, int expenseTotal,
                          List<CategoryExpense> categoryExpenses) {
        this.yearMonth = yearMonth;
        this.incomeTotal = incomeTotal;
        this.expenseTotal = expenseTotal;
        // 外から変更されないよう、変更不可のコピーとして保持する
        this.categoryExpenses = List.copyOf(categoryExpenses);
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public int getIncomeTotal() {
        return incomeTotal;
    }

    public int getExpenseTotal() {
        return expenseTotal;
    }

    /** 収支(収入合計 − 支出合計)。黒字ならプラス、赤字ならマイナス。 */
    public int getBalance() {
        return incomeTotal - expenseTotal;
    }

    public List<CategoryExpense> getCategoryExpenses() {
        return categoryExpenses;
    }
}
