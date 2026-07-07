package com.example.kakeibo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.service.CategoryService;

/**
 * カテゴリマスタを管理する(追加・名称変更・削除)モーダルダイアログ。
 */
public class CategoryDialog extends JDialog {

    private final CategoryService categoryService;

    private final DefaultListModel<CategoryItem> listModel = new DefaultListModel<>();
    private final JList<CategoryItem> categoryList = new JList<>(listModel);

    public CategoryDialog(Frame owner, CategoryService categoryService) {
        super(owner, "カテゴリ管理", true);
        this.categoryService = categoryService;

        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(categoryList), BorderLayout.CENTER);

        JButton addButton = new JButton("追加");
        addButton.addActionListener(e -> onAdd());
        JButton renameButton = new JButton("名称変更");
        renameButton.addActionListener(e -> onRename());
        JButton deleteButton = new JButton("削除");
        deleteButton.addActionListener(e -> onDelete());
        JButton closeButton = new JButton("閉じる");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(addButton);
        buttonPanel.add(renameButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        reloadList();
        setSize(380, 420);
        setLocationRelativeTo(owner);
    }

    /** カテゴリ一覧を読み直して表示に反映する。 */
    private void reloadList() {
        listModel.clear();
        for (Category category : categoryService.findAll()) {
            listModel.addElement(new CategoryItem(category));
        }
    }

    /** 選択中のカテゴリを返す(未選択ならnull)。 */
    private Category getSelectedCategory() {
        CategoryItem item = categoryList.getSelectedValue();
        return item == null ? null : item.category;
    }

    /** 追加ボタン: 名前と区分を入力してもらい、カテゴリを追加する。 */
    private void onAdd() {
        JTextField nameField = new JTextField(15);
        JRadioButton expenseRadio = new JRadioButton("支出", true);
        JRadioButton incomeRadio = new JRadioButton("収入");
        ButtonGroup group = new ButtonGroup();
        group.add(expenseRadio);
        group.add(incomeRadio);

        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        typePanel.add(expenseRadio);
        typePanel.add(incomeRadio);
        JPanel inputPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        inputPanel.add(new JLabel("カテゴリ名:"));
        inputPanel.add(nameField);
        inputPanel.add(new JLabel("区分:"));
        inputPanel.add(typePanel);

        int answer = JOptionPane.showConfirmDialog(this, inputPanel, "カテゴリの追加",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        TransactionType type = expenseRadio.isSelected()
                ? TransactionType.EXPENSE : TransactionType.INCOME;
        try {
            categoryService.addCategory(nameField.getText(), type);
            reloadList();
        } catch (ValidationException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "入力エラー",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 名称変更ボタン: 新しい名前を入力してもらい、カテゴリ名を変更する。 */
    private void onRename() {
        Category selected = getSelectedCategory();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "名称変更するカテゴリを選択してください");
            return;
        }
        Object input = JOptionPane.showInputDialog(this, "新しいカテゴリ名:", "カテゴリの名称変更",
                JOptionPane.PLAIN_MESSAGE, null, null, selected.getName());
        if (input == null) {
            return;  // キャンセル
        }
        try {
            categoryService.renameCategory(selected.getId(), input.toString());
            reloadList();
        } catch (ValidationException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "入力エラー",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 削除ボタン: 確認のうえカテゴリを削除する(使用中のカテゴリは削除できない)。 */
    private void onDelete() {
        Category selected = getSelectedCategory();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "削除するカテゴリを選択してください");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "カテゴリ「" + selected.getName() + "」を削除しますか?", "削除の確認",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            categoryService.deleteCategory(selected.getId());
            reloadList();
        } catch (ValidationException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "削除できません",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 一覧の1項目。「名前 (区分)」の形式で表示する。 */
    private static class CategoryItem {
        final Category category;

        CategoryItem(Category category) {
            this.category = category;
        }

        @Override
        public String toString() {
            return category.getName() + " (" + category.getType().getLabel() + ")";
        }
    }
}
