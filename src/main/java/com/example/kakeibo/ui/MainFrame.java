package com.example.kakeibo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.service.CategoryService;
import com.example.kakeibo.service.TransactionService;

/**
 * メイン画面。
 *
 * <p>上部にフィルタ(年月・カテゴリ・収支区分)、中央に取引一覧(日付の新しい順)、
 * 下部に登録・編集・削除ボタンとステータスバーを配置します。
 * メニューバーからカテゴリ管理と月次集計を開けます。</p>
 */
public class MainFrame extends JFrame {

    private static final Logger logger = Logger.getLogger(MainFrame.class.getName());

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    private final TransactionTableModel tableModel = new TransactionTableModel();
    private final JTable table = new JTable(tableModel);

    private final JComboBox<MonthItem> monthCombo = new JComboBox<>();
    private final JComboBox<CategoryItem> categoryCombo = new JComboBox<>();
    private final JRadioButton allTypeRadio = new JRadioButton("すべて", true);
    private final JRadioButton incomeRadio = new JRadioButton("収入");
    private final JRadioButton expenseRadio = new JRadioButton("支出");
    private final JLabel statusLabel = new JLabel(" ");

    /** フィルタの選択肢を組み替えている最中は、選択変更イベントによる再検索を止めるためのフラグ */
    private boolean updatingFilters;

    public MainFrame(TransactionService transactionService, CategoryService categoryService) {
        super("家計簿アプリ");
        this.transactionService = transactionService;
        this.categoryService = categoryService;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);  // 画面中央に表示

        setJMenuBar(buildMenuBar());
        add(buildFilterPanel(), BorderLayout.NORTH);
        add(buildTablePane(), BorderLayout.CENTER);
        add(buildSouthPanel(), BorderLayout.SOUTH);

        // 初期表示: フィルタの選択肢を用意してから一覧を読み込む
        refreshFilterChoices();
        reloadTransactions();
    }

    // ---------- 画面の組み立て ----------

    private JMenuBar buildMenuBar() {
        JMenu menu = new JMenu("メニュー");

        JMenuItem categoryItem = new JMenuItem("カテゴリ管理");
        categoryItem.addActionListener(e -> onOpenCategoryDialog());
        menu.add(categoryItem);

        JMenuItem summaryItem = new JMenuItem("月次集計");
        summaryItem.addActionListener(e -> onOpenSummaryDialog());
        menu.add(summaryItem);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        return menuBar;
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        panel.add(new JLabel("年月:"));
        monthCombo.addActionListener(e -> onFilterChanged());
        panel.add(monthCombo);

        panel.add(new JLabel("カテゴリ:"));
        categoryCombo.addActionListener(e -> onFilterChanged());
        panel.add(categoryCombo);

        panel.add(new JLabel("区分:"));
        ButtonGroup typeGroup = new ButtonGroup();
        for (JRadioButton radio : List.of(allTypeRadio, incomeRadio, expenseRadio)) {
            typeGroup.add(radio);
            radio.addActionListener(e -> onFilterChanged());
            panel.add(radio);
        }
        return panel;
    }

    private JScrollPane buildTablePane() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // 列幅の初期値(メモ欄を広めにとる)
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(360);
        return new JScrollPane(table);
    }

    private JPanel buildSouthPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton registerButton = new JButton("登録");
        registerButton.addActionListener(e -> onRegister());
        JButton editButton = new JButton("編集");
        editButton.addActionListener(e -> onEdit());
        JButton deleteButton = new JButton("削除");
        deleteButton.addActionListener(e -> onDelete());
        buttonPanel.add(registerButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);

        statusLabel.setBorder(BorderFactory.createEtchedBorder());

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttonPanel, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);
        return south;
    }

    // ---------- ボタン・メニューの処理 ----------

    /** フィルタの選択が変わったら一覧を読み直す。 */
    private void onFilterChanged() {
        if (updatingFilters) {
            return;  // 選択肢の組み替え中に発生したイベントは無視する
        }
        reloadTransactions();
    }

    private void onRegister() {
        TransactionDialog dialog = new TransactionDialog(this, transactionService,
                categoryService, null);
        dialog.setVisible(true);  // モーダルなので、閉じられるまでここで待つ
        if (dialog.isSaved()) {
            refreshFilterChoices();  // 新しい月の取引が増えた場合に備えて選択肢も更新する
            reloadTransactions();
        }
    }

    private void onEdit() {
        Transaction selected = getSelectedTransaction();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "編集する取引を選択してください");
            return;
        }
        TransactionDialog dialog = new TransactionDialog(this, transactionService,
                categoryService, selected);
        dialog.setVisible(true);
    }

    private void onDelete() {
        Transaction selected = getSelectedTransaction();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "削除する取引を選択してください");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "選択した取引を削除しますか?", "削除の確認", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            transactionService.delete(selected.getId());
            refreshFilterChoices();  // その月の取引がなくなった場合に備えて選択肢も更新する
            reloadTransactions();
        } catch (DataAccessException e) {
            showError(e);
        }
    }

    private void onOpenCategoryDialog() {
        new CategoryDialog(this, categoryService).setVisible(true);
        // カテゴリの追加・名称変更・削除が一覧やフィルタに反映されるよう読み直す
        refreshFilterChoices();
        reloadTransactions();
    }

    private void onOpenSummaryDialog() {
        new SummaryDialog(this, transactionService).setVisible(true);
    }

    // ---------- データの読み込みと画面反映 ----------

    /** 現在のフィルタ条件で取引一覧を読み直し、テーブルとステータスバーへ反映する。 */
    private void reloadTransactions() {
        try {
            List<Transaction> transactions = transactionService.search(
                    getSelectedMonth(), getSelectedCategoryId(), getSelectedType());
            Map<Long, String> categoryNames = new HashMap<>();
            for (Category category : categoryService.findAll()) {
                categoryNames.put(category.getId(), category.getName());
            }
            tableModel.setData(transactions, categoryNames);
            updateStatusBar();
        } catch (DataAccessException e) {
            showError(e);
        }
    }

    /** フィルタの選択肢(年月・カテゴリ)を最新のデータで作り直す。可能なら以前の選択を保つ。 */
    private void refreshFilterChoices() {
        updatingFilters = true;
        try {
            YearMonth selectedMonth = getSelectedMonth();
            Long selectedCategoryId = getSelectedCategoryId();

            monthCombo.removeAllItems();
            monthCombo.addItem(new MonthItem(null));  // 「すべて」
            for (YearMonth month : transactionService.findAvailableMonths()) {
                monthCombo.addItem(new MonthItem(month));
            }
            selectMonth(selectedMonth);

            categoryCombo.removeAllItems();
            categoryCombo.addItem(new CategoryItem(null));  // 「すべて」
            for (Category category : categoryService.findAll()) {
                categoryCombo.addItem(new CategoryItem(category));
            }
            selectCategory(selectedCategoryId);
        } catch (DataAccessException e) {
            showError(e);
        } finally {
            updatingFilters = false;
        }
    }

    private void selectMonth(YearMonth month) {
        for (int i = 0; i < monthCombo.getItemCount(); i++) {
            YearMonth value = monthCombo.getItemAt(i).value;
            if (month == null ? value == null : month.equals(value)) {
                monthCombo.setSelectedIndex(i);
                return;
            }
        }
        // 見つからなければ「すべて」に戻す
        monthCombo.setSelectedIndex(0);
    }

    private void selectCategory(Long categoryId) {
        for (int i = 0; i < categoryCombo.getItemCount(); i++) {
            Category value = categoryCombo.getItemAt(i).category;
            Long valueId = (value == null) ? null : value.getId();
            if (categoryId == null ? valueId == null : categoryId.equals(valueId)) {
                categoryCombo.setSelectedIndex(i);
                return;
            }
        }
        categoryCombo.setSelectedIndex(0);
    }

    private void updateStatusBar() {
        statusLabel.setText(String.format(" %d件 | 収入合計: %,d円 | 支出合計: %,d円",
                tableModel.getRowCount(),
                tableModel.sumAmount(TransactionType.INCOME),
                tableModel.sumAmount(TransactionType.EXPENSE)));
    }

    // ---------- フィルタ選択値・選択行の取得 ----------

    /** 選択中の年月を返す(「すべて」ならnull)。 */
    private YearMonth getSelectedMonth() {
        MonthItem item = (MonthItem) monthCombo.getSelectedItem();
        return item == null ? null : item.value;
    }

    /** 選択中のカテゴリIDを返す(「すべて」ならnull)。 */
    private Long getSelectedCategoryId() {
        CategoryItem item = (CategoryItem) categoryCombo.getSelectedItem();
        return (item == null || item.category == null) ? null : item.category.getId();
    }

    /** 選択中の収支区分を返す(「すべて」ならnull)。 */
    private TransactionType getSelectedType() {
        if (incomeRadio.isSelected()) {
            return TransactionType.INCOME;
        }
        if (expenseRadio.isSelected()) {
            return TransactionType.EXPENSE;
        }
        return null;
    }

    /** テーブルで選択中の取引を返す(未選択ならnull)。 */
    private Transaction getSelectedTransaction() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        // 並べ替え等で表示上の行番号とモデルの行番号がずれる場合に備えた変換
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.getTransactionAt(modelRow);
    }

    // ---------- エラー表示 ----------

    /** DB障害をユーザー向けメッセージに変換して表示する(例外方針は exception パッケージ参照)。 */
    private void showError(DataAccessException e) {
        logger.log(Level.SEVERE, "データベースエラー", e);
        JOptionPane.showMessageDialog(this,
                "データベースエラーが発生しました。\n" + e.getMessage(),
                "エラー", JOptionPane.ERROR_MESSAGE);
    }

    // ---------- コンボボックスの選択肢 ----------

    /** 年月コンボの1項目。valueがnullのときは「すべて」を表す。 */
    private static class MonthItem {
        final YearMonth value;

        MonthItem(YearMonth value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value == null ? "すべて" : value.getYear() + "年" + value.getMonthValue() + "月";
        }
    }

    /** カテゴリコンボの1項目。categoryがnullのときは「すべて」を表す。 */
    private static class CategoryItem {
        final Category category;

        CategoryItem(Category category) {
            this.category = category;
        }

        @Override
        public String toString() {
            return category == null ? "すべて" : category.getName();
        }
    }
}
