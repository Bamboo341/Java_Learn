package com.example.kakeibo.model;

/**
 * 取引のカテゴリ(給与・食費など)を表すモデル。
 *
 * <p>カテゴリは収入用・支出用のどちらかに属します(typeで区別)。</p>
 */
public class Category {

    /** ID。データベースが採番するため、未保存の状態では null。 */
    private final Long id;

    /** カテゴリ名(例: 食費) */
    private final String name;

    /** このカテゴリが収入用か支出用か */
    private final TransactionType type;

    /** 一覧での表示順(小さいほど先頭) */
    private final int displayOrder;

    public Category(Long id, String name, TransactionType type, int displayOrder) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TransactionType getType() {
        return type;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
