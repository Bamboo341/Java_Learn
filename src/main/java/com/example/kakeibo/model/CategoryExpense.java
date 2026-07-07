package com.example.kakeibo.model;

/**
 * 月次集計の「カテゴリ別支出」1行分を表すDTO(データの入れ物)。
 *
 * <p>集計はSQLのGROUP BYで行うため、Transactionのような
 * 完全なモデルではなく、集計結果専用の軽い入れ物を用意しています。</p>
 */
public class CategoryExpense {

    /** カテゴリ名 */
    private final String categoryName;

    /** そのカテゴリの支出合計(円) */
    private final int amount;

    public CategoryExpense(String categoryName, int amount) {
        this.categoryName = categoryName;
        this.amount = amount;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getAmount() {
        return amount;
    }
}
