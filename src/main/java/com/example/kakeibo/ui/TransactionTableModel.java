package com.example.kakeibo.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * 取引一覧テーブル(JTable)にデータを供給するモデル。
 *
 * <p>「どの行・列に何を表示するか」というロジックをJTable(画面部品)から
 * 切り離すのがTableModelの役割です。切り離してあるおかげで、
 * このクラスは画面を起動せずに単体テストできます。</p>
 */
public class TransactionTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"日付", "区分", "カテゴリ", "金額", "メモ"};

    /** 表示対象の取引(日付の新しい順で渡される前提) */
    private List<Transaction> transactions = new ArrayList<>();

    /** カテゴリIDから名前を引くための対応表 */
    private Map<Long, String> categoryNames = new HashMap<>();

    /**
     * 表示するデータを丸ごと入れ替える。
     *
     * @param transactions  表示する取引の一覧
     * @param categoryNames カテゴリIDから名前を引くための対応表
     */
    public void setData(List<Transaction> transactions, Map<Long, String> categoryNames) {
        this.transactions = new ArrayList<>(transactions);
        this.categoryNames = new HashMap<>(categoryNames);
        // JTableに「テーブル全体のデータが変わった」と通知して再描画してもらう。
        // この通知を忘れると、データは変わっているのに画面は古いまま、という状態になる。
        fireTableDataChanged();
    }

    /** 指定した行の取引を返す(編集・削除で選択行を特定するために使う)。 */
    public Transaction getTransactionAt(int rowIndex) {
        return transactions.get(rowIndex);
    }

    /** 表示中の取引のうち、指定した区分の金額合計を返す(ステータスバー用)。 */
    public int sumAmount(TransactionType type) {
        int total = 0;
        for (Transaction transaction : transactions) {
            if (transaction.getType() == type) {
                total += transaction.getAmount();
            }
        }
        return total;
    }

    @Override
    public int getRowCount() {
        return transactions.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return COLUMN_NAMES[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // セルの直接編集は許可しない(編集は必ずダイアログ経由にしてバリデーションを通す)
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Transaction transaction = transactions.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return transaction.getDate().toString();  // yyyy-MM-dd形式
            case 1:
                return transaction.getType().getLabel();
            case 2:
                return categoryNames.getOrDefault(transaction.getCategoryId(), "(不明)");
            case 3:
                return String.format("%,d", transaction.getAmount());  // 3桁区切り(例: 3,000)
            case 4:
                return transaction.getMemo() == null ? "" : transaction.getMemo();
            default:
                throw new IllegalArgumentException("不正な列番号です: " + columnIndex);
        }
    }
}
