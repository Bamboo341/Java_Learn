package com.example.kakeibo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.service.CategoryService;
import com.example.kakeibo.service.TransactionService;

/**
 * 取引の登録・編集を行うモーダルダイアログ。
 *
 * <p>収支区分を切り替えると、カテゴリの選択肢がその区分のものに連動して変わります。
 * 入力エラーはダイアログ内に赤字で表示します。</p>
 */
public class TransactionDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(TransactionDialog.class.getName());

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    /** 編集対象の取引(新規登録のときはnull) */
    private final Transaction editing;

    private final JTextField dateField = new JTextField(10);
    private final JRadioButton expenseRadio = new JRadioButton("支出", true);
    private final JRadioButton incomeRadio = new JRadioButton("収入");
    private final JComboBox<CategoryItem> categoryCombo = new JComboBox<>();
    private final JTextField amountField = new JTextField(10);
    private final JTextField memoField = new JTextField(20);

    /** バリデーションエラーの表示欄 */
    private final JLabel messageLabel = new JLabel(" ");

    /** 保存に成功したかどうか(呼び出し元が一覧を再読込するかの判断に使う) */
    private boolean saved;

    /**
     * @param editing 編集対象の取引。新規登録の場合はnull
     */
    public TransactionDialog(Frame owner, TransactionService transactionService,
                             CategoryService categoryService, Transaction editing) {
        super(owner, editing == null ? "取引の登録" : "取引の編集", true);
        this.transactionService = transactionService;
        this.categoryService = categoryService;
        this.editing = editing;

        buildLayout();

        // 収支区分を切り替えたら、カテゴリの選択肢をその区分のものに入れ替える
        expenseRadio.addActionListener(e -> reloadCategoryChoices(null));
        incomeRadio.addActionListener(e -> reloadCategoryChoices(null));

        if (editing == null) {
            // 新規登録: 今日の日付を初期値にする
            dateField.setText(LocalDate.now().toString());
            reloadCategoryChoices(null);
        } else {
            // 編集: 既存の値を各入力欄に反映する
            dateField.setText(editing.getDate().toString());
            if (editing.getType() == TransactionType.INCOME) {
                incomeRadio.setSelected(true);
            } else {
                expenseRadio.setSelected(true);
            }
            amountField.setText(String.valueOf(editing.getAmount()));
            memoField.setText(editing.getMemo() == null ? "" : editing.getMemo());
            reloadCategoryChoices(editing.getCategoryId());
        }

        pack();
        setLocationRelativeTo(owner);
    }

    /** 保存に成功してダイアログを閉じたときtrue。 */
    public boolean isSaved() {
        return saved;
    }

    // ---------- 画面の組み立て ----------

    private void buildLayout() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.anchor = GridBagConstraints.WEST;

        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(expenseRadio);
        typeGroup.add(incomeRadio);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        typePanel.add(expenseRadio);
        typePanel.add(incomeRadio);

        int row = 0;
        addRow(form, gbc, row++, "日付 (yyyy-MM-dd):", dateField);
        addRow(form, gbc, row++, "区分:", typePanel);
        addRow(form, gbc, row++, "カテゴリ:", categoryCombo);
        addRow(form, gbc, row++, "金額(円):", amountField);
        addRow(form, gbc, row++, "メモ:", memoField);

        messageLabel.setForeground(Color.RED);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));

        JButton saveButton = new JButton("保存");
        saveButton.addActionListener(e -> onSave());
        JButton cancelButton = new JButton("キャンセル");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        JPanel south = new JPanel(new BorderLayout());
        south.add(messageLabel, BorderLayout.NORTH);
        south.add(buttonPanel, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(saveButton);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row,
                        String labelText, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    // ---------- 入力の処理 ----------

    /** 選択中の収支区分を返す。 */
    private TransactionType getSelectedType() {
        return incomeRadio.isSelected() ? TransactionType.INCOME : TransactionType.EXPENSE;
    }

    /** カテゴリの選択肢を現在の収支区分に合わせて入れ替える。selectIdがあればそれを選択する。 */
    private void reloadCategoryChoices(Long selectId) {
        categoryCombo.removeAllItems();
        for (Category category : categoryService.findByType(getSelectedType())) {
            categoryCombo.addItem(new CategoryItem(category));
        }
        if (selectId == null) {
            return;
        }
        for (int i = 0; i < categoryCombo.getItemCount(); i++) {
            if (selectId.equals(categoryCombo.getItemAt(i).category.getId())) {
                categoryCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    /** 保存ボタン: 入力からTransactionを組み立ててServiceへ渡す。 */
    private void onSave() {
        try {
            Transaction input = buildTransactionFromInput();
            if (editing == null) {
                transactionService.register(input);
            } else {
                transactionService.update(input);
            }
            saved = true;
            dispose();
        } catch (ValidationException e) {
            // 入力ミスはダイアログを閉じず、赤字メッセージで知らせて入力し直してもらう
            messageLabel.setText(e.getMessage());
        } catch (DataAccessException e) {
            logger.log(Level.SEVERE, "取引の保存に失敗", e);
            JOptionPane.showMessageDialog(this,
                    "データベースエラーが発生しました。\n" + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 入力欄の値からTransactionを組み立てる。入力に問題があればValidationExceptionを投げる。 */
    private Transaction buildTransactionFromInput() throws ValidationException {
        LocalDate date = parseDate(dateField.getText());
        int amount = parseAmount(amountField.getText());
        CategoryItem item = (CategoryItem) categoryCombo.getSelectedItem();
        if (item == null) {
            throw new ValidationException("カテゴリを選択してください");
        }
        String memo = memoField.getText().trim();
        if (memo.isEmpty()) {
            memo = null;  // 未入力のメモは空文字ではなくNULLで保存する
        }
        if (editing == null) {
            return new Transaction(date, amount, getSelectedType(), item.category.getId(), memo);
        }
        return new Transaction(editing.getId(), date, amount, getSelectedType(),
                item.category.getId(), memo, editing.getCreatedAt());
    }

    /** 日付欄の文字列をLocalDateに変換する。 */
    private LocalDate parseDate(String text) throws ValidationException {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("日付を入力してください");
        }
        try {
            return LocalDate.parse(text.trim());  // yyyy-MM-dd形式として解釈する
        } catch (DateTimeParseException e) {
            throw new ValidationException("日付は yyyy-MM-dd 形式で入力してください(例: 2026-07-01)");
        }
    }

    /** 金額欄の文字列をintに変換する。 */
    private int parseAmount(String text) throws ValidationException {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException("金額を入力してください");
        }
        return Integer.parseInt(text.trim());
    }

    /** カテゴリコンボの1項目。 */
    private static class CategoryItem {
        final Category category;

        CategoryItem(Category category) {
            this.category = category;
        }

        @Override
        public String toString() {
            return category.getName();
        }
    }
}
