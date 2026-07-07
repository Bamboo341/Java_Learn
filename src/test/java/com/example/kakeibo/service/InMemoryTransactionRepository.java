package com.example.kakeibo.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.kakeibo.model.MonthlySummary;
import com.example.kakeibo.model.Transaction;
import com.example.kakeibo.model.TransactionType;
import com.example.kakeibo.repository.TransactionRepository;

/**
 * テスト専用のインメモリ実装(Mapに保存するだけの偽物Repository)。
 *
 * <p>差し替えの狙いは {@link InMemoryCategoryRepository} のコメントを参照。</p>
 */
class InMemoryTransactionRepository implements TransactionRepository {

    /** IDをキーにした保存領域 */
    private final Map<Long, Transaction> storage = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public List<Transaction> findByConditions(YearMonth yearMonth, Long categoryId,
                                              TransactionType type) {
        List<Transaction> results = new ArrayList<>();
        for (Transaction t : storage.values()) {
            if (yearMonth != null && !YearMonth.from(t.getDate()).equals(yearMonth)) {
                continue;
            }
            if (categoryId != null && t.getCategoryId() != categoryId) {
                continue;
            }
            if (type != null && t.getType() != type) {
                continue;
            }
            results.add(t);
        }
        results.sort(Comparator.comparing(Transaction::getDate)
                .thenComparing(Transaction::getId).reversed());
        return results;
    }

    @Override
    public Optional<Transaction> findById(long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public long insert(Transaction transaction) {
        long id = nextId++;
        storage.put(id, new Transaction(id, transaction.getDate(), transaction.getAmount(),
                transaction.getType(), transaction.getCategoryId(), transaction.getMemo(),
                LocalDateTime.now()));
        return id;
    }

    @Override
    public void update(Transaction transaction) {
        storage.put(transaction.getId(), transaction);
    }

    @Override
    public void delete(long id) {
        storage.remove(id);
    }

    @Override
    public int countByCategoryId(long categoryId) {
        int count = 0;
        for (Transaction t : storage.values()) {
            if (t.getCategoryId() == categoryId) {
                count++;
            }
        }
        return count;
    }

    @Override
    public MonthlySummary summarizeMonth(YearMonth yearMonth) {
        // 集計ロジックの正しさはJdbcTransactionRepositoryの結合テストで検証しているため、
        // この偽物では実装しない(Serviceは委譲するだけでロジックを持たない)
        throw new UnsupportedOperationException("このテスト用実装では集計は未対応です");
    }

    @Override
    public List<YearMonth> findDistinctMonths() {
        List<YearMonth> months = new ArrayList<>();
        for (Transaction t : storage.values()) {
            YearMonth month = YearMonth.from(t.getDate());
            if (!months.contains(month)) {
                months.add(month);
            }
        }
        months.sort(Comparator.reverseOrder());
        return months;
    }
}
