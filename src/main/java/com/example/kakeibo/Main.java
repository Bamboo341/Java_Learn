package com.example.kakeibo;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.example.kakeibo.infra.DatabaseManager;

/**
 * アプリケーションのエントリポイント。
 *
 * <p>画面は起動確認用の仮実装です。
 * ステージ5で、DatabaseManager → Repository → Service → MainFrame を
 * 手動のコンストラクタインジェクションで組み立てる実装に置き換えます。</p>
 */
public class Main {

    public static void main(String[] args) {
        // 画面を表示する前にデータベースを準備する(初回起動時はテーブル作成+初期データ投入)
        DatabaseManager databaseManager = new DatabaseManager();
        databaseManager.initialize();

        // Swingの画面操作はイベントディスパッチスレッド(EDT)上で行うのが原則
        SwingUtilities.invokeLater(Main::createAndShowWindow);
    }

    /** 起動確認用の仮ウィンドウを表示する。 */
    private static void createAndShowWindow() {
        JFrame frame = new JFrame("家計簿アプリ");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new JLabel("家計簿アプリ(準備中)", SwingConstants.CENTER));
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null); // 画面中央に表示
        frame.setVisible(true);
    }
}
