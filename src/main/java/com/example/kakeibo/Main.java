package com.example.kakeibo;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.example.kakeibo.exception.DataAccessException;
import com.example.kakeibo.infra.DatabaseManager;
import com.example.kakeibo.repository.CategoryRepository;
import com.example.kakeibo.repository.JdbcCategoryRepository;
import com.example.kakeibo.repository.JdbcTransactionRepository;
import com.example.kakeibo.repository.TransactionRepository;
import com.example.kakeibo.service.CategoryService;
import com.example.kakeibo.service.TransactionService;
import com.example.kakeibo.ui.MainFrame;

/**
 * アプリケーションのエントリポイント。
 *
 * <p>ここでアプリを構成する部品(Repository・Service・画面)を生成し、
 * コンストラクタ経由で依存関係を注入して組み立てます。</p>
 */
public class Main {

    public static void main(String[] args) {
        // 画面を表示する前にデータベースを準備する(初回起動時はテーブル作成+初期データ投入)
        DatabaseManager databaseManager = new DatabaseManager();
        try {
            databaseManager.initialize();
        } catch (DataAccessException e) {
            // DBが使えなければアプリは何もできないので、メッセージを出して終了する
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "データベースの初期化に失敗しました。\n" + e.getMessage(),
                    "起動エラー", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        // 手動のコンストラクタインジェクション:
        // 依存される側(下位層)から順に生成し、コンストラクタで渡して組み立てる。
        // SpringのようなDIコンテナが自動でやってくれることを、ここでは手で書いている。
        // 変数の型をinterface(TransactionRepositoryなど)で宣言している点にも注目。
        TransactionRepository transactionRepository = new JdbcTransactionRepository(databaseManager);
        CategoryRepository categoryRepository = new JdbcCategoryRepository(databaseManager);
        TransactionService transactionService =
                new TransactionService(transactionRepository, categoryRepository);
        CategoryService categoryService =
                new CategoryService(categoryRepository, transactionRepository);

        // Swingの画面操作はイベントディスパッチスレッド(EDT)上で行うのが原則
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(transactionService, categoryService);
            frame.setVisible(true);
        });
    }
}
