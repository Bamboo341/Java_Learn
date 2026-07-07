-- ============================================================
-- テーブル定義(初回起動時に DatabaseManager が自動実行します)
-- ============================================================

-- カテゴリマスタ
CREATE TABLE categories (
    id            IDENTITY PRIMARY KEY,              -- 自動採番のID
    name          VARCHAR(50) NOT NULL UNIQUE,       -- カテゴリ名(重複禁止)
    type          VARCHAR(10) NOT NULL,              -- 'INCOME'(収入) / 'EXPENSE'(支出)
    display_order INT NOT NULL DEFAULT 0             -- 一覧での表示順
);

-- 取引(収入・支出の記録)
CREATE TABLE transactions (
    id               IDENTITY PRIMARY KEY,           -- 自動採番のID
    transaction_date DATE NOT NULL,                  -- 取引日
    amount           INT NOT NULL,                   -- 金額(円)。常に正の整数(収支の別は type で表す)
    type             VARCHAR(10) NOT NULL,           -- 'INCOME'(収入) / 'EXPENSE'(支出)
    category_id      BIGINT NOT NULL REFERENCES categories(id),  -- カテゴリへの外部キー
    memo             VARCHAR(200),                   -- メモ(任意)
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- レコード作成日時
);
