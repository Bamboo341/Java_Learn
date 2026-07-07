package com.example.kakeibo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1件の取引(収入または支出)を表すモデル。
 */
public class Transaction {

    /** ID。データベースが採番するため、未保存の状態では null。 */
    private final Long id;

    /** 取引日 */
    private final LocalDate date;

    // [設計意図] 金額は int(円)で持つ。double は 0.1 のような小数を2進数で正確に
    // 表現できず、計算を繰り返すと誤差が出る(例: 0.1 + 0.2 == 0.3 は false)。
    // 日本円に小数はないので、整数の「円」で扱うのが最も単純で安全。
    // なお、より大きな金額や外貨を扱う場合は BigDecimal の利用を検討する。
    /** 金額(円)。常に正の整数で、収入か支出かは type で表す。 */
    private final int amount;

    /** 収支区分(収入 / 支出) */
    private final TransactionType type;

    /** カテゴリのID */
    private final long categoryId;

    /** メモ(任意入力) */
    private final String memo;

    /** レコード作成日時。データベースが設定するため、未保存の状態では null。 */
    private final LocalDateTime createdAt;

    /** データベースから読み込んだ取引を生成する。 */
    public Transaction(Long id, LocalDate date, int amount, TransactionType type,
                       long categoryId, String memo, LocalDateTime createdAt) {
        this.id = id;
        this.date = date;
        this.amount = amount;
        this.type = type;
        this.categoryId = categoryId;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    /** 新規登録用の取引(IDと作成日時が未確定)を生成する。 */
    public Transaction(LocalDate date, int amount, TransactionType type,
                       long categoryId, String memo) {
        this(null, date, amount, type, categoryId, memo, null);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
