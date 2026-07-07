package com.example.kakeibo.repository;

import java.util.List;
import java.util.Optional;

import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.TransactionType;

/**
 * カテゴリマスタの永続化を担当するリポジトリ。
 *
 * <p>interfaceと実装を分離する理由は {@link TransactionRepository} のコメントを参照。</p>
 */
public interface CategoryRepository {

    /** 全カテゴリを表示順で返す。 */
    List<Category> findAll();

    /** 指定した収支区分のカテゴリだけを表示順で返す(登録画面のカテゴリ選択肢に使う)。 */
    List<Category> findByType(TransactionType type);

    /** IDで1件取得する。存在しなければ空のOptionalを返す。 */
    Optional<Category> findById(long id);

    /**
     * カテゴリを新規登録する。
     *
     * @return データベースが採番したID
     */
    long insert(Category category);

    /** カテゴリを更新する(IDで対象を特定する)。 */
    void update(Category category);

    /** カテゴリをIDで削除する。使用中カテゴリの削除可否チェックはServiceが行う。 */
    void delete(long id);

    /** 同じ名前のカテゴリが既に存在するかを返す(重複チェックに使う)。 */
    boolean existsByName(String name);
}
