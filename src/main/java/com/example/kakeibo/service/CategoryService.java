package com.example.kakeibo.service;

import java.util.List;

import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.repository.CategoryRepository;
import com.example.kakeibo.repository.TransactionRepository;

/**
 * カテゴリマスタに関する業務ロジックを担当するサービス。
 *
 * <p>カテゴリ名の重複チェックや、使用中カテゴリの削除禁止などの
 * 業務ルールをここで実施します(バリデーションをServiceで行う理由は
 * {@link TransactionService} のコメントを参照)。</p>
 */
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    /** 全カテゴリを表示順で返す。 */
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    /** 指定した収支区分のカテゴリだけを表示順で返す。 */
    public List<Category> findByType(TransactionType type) {
        return categoryRepository.findByType(type);
    }

    /**
     * カテゴリを追加する。表示順は既存の最後尾+1になる。
     *
     * @return データベースが採番したID
     * @throws ValidationException カテゴリ名が不正、または重複している場合
     */
    public long addCategory(String name, TransactionType type) throws ValidationException {
        String validName = validateName(name);
        if (type == null) {
            throw new ValidationException("収支区分を選択してください");
        }
        if (categoryRepository.existsByName(validName)) {
            throw new ValidationException("カテゴリ「" + validName + "」は既に存在します");
        }
        int maxOrder = 0;
        for (Category category : categoryRepository.findAll()) {
            maxOrder = Math.max(maxOrder, category.getDisplayOrder());
        }
        return categoryRepository.insert(new Category(null, validName, type, maxOrder + 1));
    }

    /**
     * カテゴリの名前を変更する。収支区分と表示順は変わらない。
     *
     * @throws ValidationException 名前が不正・重複している場合、または対象が存在しない場合
     */
    public void renameCategory(long id, String newName) throws ValidationException {
        String validName = validateName(newName);
        Category current = categoryRepository.findById(id)
                .orElseThrow(() -> new ValidationException("対象のカテゴリが見つかりません"));
        // 名前が変わらないなら重複チェック不要(自分自身と重複扱いになってしまうため)
        if (!validName.equals(current.getName()) && categoryRepository.existsByName(validName)) {
            throw new ValidationException("カテゴリ「" + validName + "」は既に存在します");
        }
        categoryRepository.update(
                new Category(current.getId(), validName, current.getType(), current.getDisplayOrder()));
    }

    /**
     * カテゴリを削除する。取引で使用中のカテゴリは削除できない。
     *
     * @throws ValidationException 使用中のカテゴリを削除しようとした場合
     */
    public void deleteCategory(long id) throws ValidationException {
        // 取引から使われているカテゴリを消すと、外部キー制約違反(DBエラー)になるうえ、
        // 過去の取引の分類が失われてしまう。削除前に必ず使用中チェックを行う。
        int usageCount = transactionRepository.countByCategoryId(id);
        if (usageCount > 0) {
            throw new ValidationException(
                    "このカテゴリは" + usageCount + "件の取引で使用中のため削除できません");
        }
        categoryRepository.delete(id);
    }

    /** カテゴリ名を検証し、前後の空白を除いた名前を返す。 */
    private String validateName(String name) throws ValidationException {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("カテゴリ名を入力してください");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw new ValidationException("カテゴリ名は50文字以内で入力してください");
        }
        return trimmed;
    }
}
