package com.example.kakeibo.service;

import java.time.YearMonth;
import java.util.List;

import com.example.kakeibo.exception.ValidationException;
import com.example.kakeibo.model.Category;
import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.repository.CategoryRepository;
import com.example.kakeibo.repository.TransactionRepository;

/**
 * 取引に関する業務ロジックを担当するサービス。
 *
 * <p>登録・更新時の入力チェック(バリデーション)を行い、
 * 問題があれば {@link ValidationException} を投げます。</p>
 */
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * 取引を検索する。各条件はnullなら「絞り込みなし」。結果は日付の新しい順。
     */
    public List<Transaction> search(YearMonth yearMonth, Long categoryId, TransactionType type) {
        return transactionRepository.findByConditions(yearMonth, categoryId, type);
    }

    /**
     * 取引を新規登録する。
     *
     * @return データベースが採番したID
     * @throws ValidationException 入力内容に問題がある場合
     */
    public long register(Transaction transaction) throws ValidationException {
        validate(transaction);
        return transactionRepository.insert(transaction);
    }

    /**
     * 取引を更新する。
     *
     * @throws ValidationException 入力内容に問題がある場合、または対象が存在しない場合
     */
    public void update(Transaction transaction) throws ValidationException {
        if (transaction.getId() == null) {
            // IDなしでの更新はUI側のプログラムミス(ユーザー入力では起こらない)なので非検査例外
            throw new IllegalArgumentException("更新にはIDが必要です");
        }
        if (transactionRepository.findById(transaction.getId()).isEmpty()) {
            throw new ValidationException("対象の取引が見つかりません。すでに削除された可能性があります");
        }
        validate(transaction);
        transactionRepository.update(transaction);
    }

    /** 取引をIDで削除する。 */
    public void delete(long id) {
        transactionRepository.delete(id);
    }

    /** 指定した年月の月次集計を取得する。 */
    public MonthlySummary summarizeMonth(YearMonth yearMonth) {
        return transactionRepository.summarizeMonth(yearMonth);
    }

    /** 取引が存在する年月の一覧を新しい順で返す(画面のフィルタや集計の選択肢に使う)。 */
    public List<YearMonth> findAvailableMonths() {
        return transactionRepository.findDistinctMonths();
    }

    // [設計意図] バリデーションはUI(ダイアログ)ではなくServiceで行う。
    // UI側のチェックは「入力しやすさのための補助」にはなるが、最終防衛線にはならない。
    // 画面が増えたり、UIの実装にミスがあったりしても、必ずServiceを通る限り
    // 不正なデータはDBに入らない。「UIを信用しない」がServiceの基本姿勢。
    /** 取引の入力内容を検証する。問題があれば ValidationException を投げる。 */
    private void validate(Transaction transaction) throws ValidationException {
        if (transaction.getDate() == null) {
            throw new ValidationException("日付を入力してください");
        }
        if (transaction.getType() == null) {
            throw new ValidationException("収支区分を選択してください");
        }
        if (transaction.getAmount() <= 0) {
            throw new ValidationException("金額は1円以上の整数で入力してください");
        }
        // カテゴリは「存在すること」と「収支区分が一致していること」を確認する
        Category category = categoryRepository.findById(transaction.getCategoryId())
                .orElseThrow(() -> new ValidationException("カテゴリを選択してください"));
        if (category.getType() != transaction.getType()) {
            throw new ValidationException(
                    "カテゴリ「" + category.getName() + "」は" + category.getType().getLabel()
                    + "用のカテゴリです。収支区分を確認してください");
        }
        if (transaction.getMemo() != null && transaction.getMemo().length() > 200) {
            throw new ValidationException("メモは200文字以内で入力してください");
        }
    }
}
