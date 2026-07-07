package com.example.kakeibo.repository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;

/**
 * 取引の永続化を担当するリポジトリ。
 */
// [設計意図] Repositoryはinterfaceとして定義し、JDBC実装(JdbcTransactionRepository)と
// 分離している。利用側(Service)は「何ができるか」(このinterface)だけに依存し、
// 「どう実現するか」(JDBCの詳細)を知らない。こうしておくと、テスト時に
// インメモリの偽物実装へ差し替えたり、将来DBアクセスの方式を変えたりしても、
// Service側のコードを修正せずに済む。
public interface TransactionRepository {

    /**
     * 条件を組み合わせて取引を検索する。結果は日付の新しい順。
     *
     * @param yearMonth  絞り込む年月(nullなら全期間)
     * @param categoryId 絞り込むカテゴリのID(nullなら全カテゴリ)
     * @param type       絞り込む収支区分(nullなら収入・支出の両方)
     */
    List<Transaction> findByConditions(YearMonth yearMonth, Long categoryId, TransactionType type);

    /** IDで1件取得する。存在しなければ空のOptionalを返す。 */
    Optional<Transaction> findById(long id);

    /**
     * 取引を新規登録する。
     *
     * @return データベースが採番したID
     */
    long insert(Transaction transaction);

    /** 取引を更新する(IDで対象を特定する)。 */
    void update(Transaction transaction);

    /** 取引をIDで削除する。 */
    void delete(long id);

    /** 指定カテゴリを使っている取引の件数を返す(カテゴリ削除可否の判定に使う)。 */
    int countByCategoryId(long categoryId);

    /** 指定した年月の月次集計(収入合計・支出合計・カテゴリ別支出)をSQLで求める。 */
    MonthlySummary summarizeMonth(YearMonth yearMonth);

    /** 取引が存在する年月の一覧を新しい順で返す(画面の年月フィルタの選択肢に使う)。 */
    List<YearMonth> findDistinctMonths();
}
