package com.example.kakeibo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.time.YearMonth;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import com.example.kakeibo.model.CategoryExpense;
import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.service.TransactionService;

/**
 * 月次集計を表示するモーダルダイアログ。
 *
 * <p>年月を選ぶと、収入合計・支出合計・収支と、カテゴリ別支出
 * (金額の大きい順、構成比%付き)を表示します。</p>
 */
public class SummaryDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(SummaryDialog.class.getName());

    private final TransactionService transactionService;

    private final JComboBox<MonthItem> monthCombo = new JComboBox<>();
    private final JLabel incomeLabel = new JLabel("-");
    private final JLabel expenseLabel = new JLabel("-");
    private final JLabel balanceLabel = new JLabel("-");

    /** カテゴリ別支出のテーブル(表示専用なのでセル編集は不可にする) */
    private final DefaultTableModel breakdownModel = new DefaultTableModel(
            new Object[] {"カテゴリ", "金額", "構成比"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    /** 選択肢の組み立て中に選択イベントで集計が走らないようにするためのフラグ */
    private boolean updatingCombo;

    public SummaryDialog(Frame owner, TransactionService transactionService) {
        super(owner, "月次集計", true);
        this.transactionService = transactionService;

        buildLayout();

        // 年月の選択肢を用意する(新しい月が先頭)
        updatingCombo = true;
        for (YearMonth month : transactionService.findAvailableMonths()) {
            monthCombo.addItem(new MonthItem(month));
        }
        updatingCombo = false;
        monthCombo.addActionListener(e -> onMonthChanged());

        // 最初は最新の月を表示する
        if (monthCombo.getItemCount() > 0) {
            monthCombo.setSelectedIndex(0);  // ここでActionEventが発生しonMonthChangedが呼ばれる
        }

        setSize(440, 460);
        setLocationRelativeTo(owner);
    }

    // ---------- 画面の組み立て ----------

    private void buildLayout() {
        JPanel monthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        monthPanel.add(new JLabel("年月:"));
        monthPanel.add(monthCombo);

        JPanel totalsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        totalsPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        totalsPanel.add(new JLabel("収入合計:"));
        totalsPanel.add(incomeLabel);
        totalsPanel.add(new JLabel("支出合計:"));
        totalsPanel.add(expenseLabel);
        totalsPanel.add(new JLabel("収支:"));
        totalsPanel.add(balanceLabel);

        JPanel north = new JPanel(new BorderLayout());
        north.add(monthPanel, BorderLayout.NORTH);
        north.add(totalsPanel, BorderLayout.SOUTH);

        JTable breakdownTable = new JTable(breakdownModel);

        JButton closeButton = new JButton("閉じる");
        closeButton.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(closeButton);

        add(north, BorderLayout.NORTH);
        add(new JScrollPane(breakdownTable), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    // ---------- 集計の読み込みと表示 ----------

    private void onMonthChanged() {
        if (updatingCombo) {
            return;
        }
        MonthItem item = (MonthItem) monthCombo.getSelectedItem();
        if (item != null) {
            loadSummary(item.value);
        }
    }

    /** 指定した年月の集計を読み込み、画面に反映する。 */
    private void loadSummary(YearMonth month) {
        // 読み込み中であることが分かるように表示を切り替えておく
        incomeLabel.setText("集計中...");
        expenseLabel.setText("集計中...");
        balanceLabel.setText("集計中...");
        balanceLabel.setForeground(Color.BLACK);
        breakdownModel.setRowCount(0);

        // [設計意図] 集計はSwingWorkerでバックグラウンド実行する。Swingの描画や
        // ボタン操作はEDT(イベントディスパッチスレッド)という1本のスレッドが
        // 処理しているため、EDT上で時間のかかる処理をすると画面全体が固まる。
        // SwingWorkerを使うと、doInBackgroundは別スレッドで、done(画面更新)は
        // EDTで実行され、「重い処理は裏で・画面更新は表で」を安全に実現できる。
        SwingWorker<MonthlySummary, Void> worker = new SwingWorker<>() {
            @Override
            protected MonthlySummary doInBackground() {
                return transactionService.summarizeMonth(month);
            }

            @Override
            protected void done() {
                try {
                    showSummary(get());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "月次集計の取得に失敗", e);
                    JOptionPane.showMessageDialog(SummaryDialog.this,
                            "集計中にエラーが発生しました。\n" + e.getMessage(),
                            "エラー", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /** 集計結果を画面に反映する。 */
    private void showSummary(MonthlySummary summary) {
        incomeLabel.setText(String.format("%,d円", summary.getIncomeTotal()));
        expenseLabel.setText(String.format("%,d円", summary.getExpenseTotal()));
        balanceLabel.setText(String.format("%,d円", summary.getBalance()));
        // 赤字の月はひと目で分かるように赤色で表示する
        balanceLabel.setForeground(summary.getBalance() < 0 ? Color.RED : new Color(0, 102, 0));

        breakdownModel.setRowCount(0);
        for (CategoryExpense expense : summary.getCategoryExpenses()) {
            double ratio = 100.0 * expense.getAmount() / summary.getExpenseTotal();
            breakdownModel.addRow(new Object[] {
                    expense.getCategoryName(),
                    String.format("%,d円", expense.getAmount()),
                    String.format("%.1f%%", ratio)});
        }
    }

    /** 年月コンボの1項目。 */
    private static class MonthItem {
        final YearMonth value;

        MonthItem(YearMonth value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value.getYear() + "年" + value.getMonthValue() + "月";
        }
    }
}
