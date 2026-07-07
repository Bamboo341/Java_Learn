-- ============================================================
-- 初期データ(初回起動時に DatabaseManager が自動実行します)
-- カテゴリマスタ + 直近3ヶ月分のサンプル取引
-- ============================================================

-- カテゴリマスタ
INSERT INTO categories (name, type, display_order) VALUES ('給与',   'INCOME',  1);
INSERT INTO categories (name, type, display_order) VALUES ('賞与',   'INCOME',  2);
INSERT INTO categories (name, type, display_order) VALUES ('食費',   'EXPENSE', 3);
INSERT INTO categories (name, type, display_order) VALUES ('日用品', 'EXPENSE', 4);
INSERT INTO categories (name, type, display_order) VALUES ('交通費', 'EXPENSE', 5);
INSERT INTO categories (name, type, display_order) VALUES ('住居費', 'EXPENSE', 6);
INSERT INTO categories (name, type, display_order) VALUES ('娯楽',   'EXPENSE', 7);
INSERT INTO categories (name, type, display_order) VALUES ('その他', 'EXPENSE', 8);

-- サンプル取引
-- カテゴリIDは採番に依存しないよう、名前から引き当てる書き方にしています

-- ---------- 2026年5月 ----------
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-01',  82000, 'EXPENSE', (SELECT id FROM categories WHERE name = '住居費'), '5月分家賃');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-01',   3200, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-02',   1900, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   '映画');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-04',   2800, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-06',  10000, 'EXPENSE', (SELECT id FROM categories WHERE name = '交通費'), '通勤定期(1ヶ月)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-07',   1400, 'EXPENSE', (SELECT id FROM categories WHERE name = '日用品'), 'ドラッグストア');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-09',   3500, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-10',   4200, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   '友人と飲み会');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-12',    980, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'コンビニ');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-14',   3100, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-15',   5000, 'EXPENSE', (SELECT id FROM categories WHERE name = 'その他'), 'プレゼント代');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-17',   2600, 'EXPENSE', (SELECT id FROM categories WHERE name = '日用品'), '洗剤・シャンプー');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-18',   2400, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-20',   1100, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   '書籍');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-22',   3300, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-25', 280000, 'INCOME',  (SELECT id FROM categories WHERE name = '給与'),   '5月分給与');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-26',   4800, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   '外食(焼肉)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-28',   2900, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
-- 月末日のデータ(月次集計の境界確認にも使える)
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-31',   1200, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'コンビニ');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-05-31',   3000, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   'ゲーム');

-- ---------- 2026年6月 ----------
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-01',  82000, 'EXPENSE', (SELECT id FROM categories WHERE name = '住居費'), '6月分家賃');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-01',   2700, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-03',   1500, 'EXPENSE', (SELECT id FROM categories WHERE name = '交通費'), '電車(休日おでかけ)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-05',   3400, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-06',  10000, 'EXPENSE', (SELECT id FROM categories WHERE name = '交通費'), '通勤定期(1ヶ月)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-08',   1800, 'EXPENSE', (SELECT id FROM categories WHERE name = '日用品'), 'ドラッグストア');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-10', 400000, 'INCOME',  (SELECT id FROM categories WHERE name = '賞与'),   '夏季賞与');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-10',   5600, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   '外食(お祝い)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-12',   3000, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-14',   8500, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   'ライブチケット');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-15',   3200, 'EXPENSE', (SELECT id FROM categories WHERE name = '日用品'), 'タオル・収納用品');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-17',   2500, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-19',   1100, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'コンビニ');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-21',   2800, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   '友人とカフェ');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-22',   3600, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-25', 280000, 'INCOME',  (SELECT id FROM categories WHERE name = '給与'),   '6月分給与');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-26',  12000, 'EXPENSE', (SELECT id FROM categories WHERE name = 'その他'), '冠婚葬祭(お祝い金)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-27',   4200, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   '外食(寿司)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-29',   2300, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
-- 月末日のデータ(月次集計の境界確認にも使える)
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-30',    800, 'EXPENSE', (SELECT id FROM categories WHERE name = '交通費'), 'バス');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-06-30',    900, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'コンビニ');

-- ---------- 2026年7月(月初のみ) ----------
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-01',  82000, 'EXPENSE', (SELECT id FROM categories WHERE name = '住居費'), '7月分家賃');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-01',   3100, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-02',   1600, 'EXPENSE', (SELECT id FROM categories WHERE name = '日用品'), 'ドラッグストア');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-03',  10000, 'EXPENSE', (SELECT id FROM categories WHERE name = '交通費'), '通勤定期(1ヶ月)');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-04',   2900, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'スーパーで買い出し');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-05',   1500, 'EXPENSE', (SELECT id FROM categories WHERE name = '娯楽'),   '動画配信サービス');
INSERT INTO transactions (transaction_date, amount, type, category_id, memo) VALUES
    ('2026-07-06',   1300, 'EXPENSE', (SELECT id FROM categories WHERE name = '食費'),   'コンビニ');
