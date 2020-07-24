//
// mailをMIME DECODEする
//
// 2020.07.24
//

package jp.or.rim.yk.george.javamail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * mail mime decode を実行するコマンドラインエントリ
 */
public class AppMain {

	// ログ
	private static Logger log = Logger.getLogger(AppMain.class.getName());

	// 使用時ヘルプ
	private static final String usageMsg1 = "javamail : 標準入力からメールを読み込んでプレーンテキストに変換する。\n";
	private static final String usageMsg2 = "    build:";
	private static final String usageMsg3 = "    usage:java -jar javamail.jar [options] [filename or -]" + "\n"
			+ "    options)\n" + "        -help          ヘルプ表示\n\n" + "        -debug         デバッグ情報表示\n"
			+ "        -v             デバッグ情報表示\n" + "        -vv            デバッグ情報表示\n"
			+ "        -vvv           デバッグ情報表示\n\n";

	// メイン
	public static void main(String[] args) {
		AppMain app = new AppMain();
		int status = app.entry(args);
		System.exit(status);
	}

	/**
	 * 引数解析
	 *
	 * @param args
	 */
	public int entry(String[] args) {
		ArrayList<String> argFilenameList = new ArrayList<String>();

		// 引数個数チェック
		if (args.length == 0) {
			usage("");
			return 0;
		}

		// Mail Decoder オブジェクト作成
		MailDecode maildecoder = new MailDecode();

		// 引数解析
		for (int i = 0; i < args.length; i++) {
			if (setDebugLevel(args[i])) {
				// デバッグレベル設定
			} else if (args[i].equals("-input-file")) {
				if (args.length < i + 1) {
					usage("オプションの引数がありません");
				}
				argFilenameList.add(args[i + 1]);
				i++;
			} else if (args[i].startsWith("-help")) {
				usage("");
				return 0;
			} else if (args[i].startsWith("-")) {
				argFilenameList.add(args[i]);
			} else {
				argFilenameList.add(args[i]);
			}
		}

		// 引数チェック
		if (argFilenameList.size() == 0) {
			usage("filenameの指定、または-input-fileの指定は必須です");
		}

		// 取得メソッド呼び出し
		int exitCode = 0;

		// ファイルの数だけループ
		for (String fromFileName : argFilenameList) {
			if (fromFileName.equals("-")) {
				// 標準入力から読み込むモード
				try {
					// 取得実施
					String result = maildecoder.decode(System.in);
					// 取得結果表示
					System.out.println(result);
				} catch (Exception e) {
					System.err.println(e.getMessage());
					exitCode = 1;
					e.printStackTrace();
				}
			} else {
				// ファイルから処理するモード
				File f = new File(fromFileName);
				String result2 = maildecoder.decode(f);
				System.out.print(result2);
			}
		}

		return (exitCode);
	}

	// -debug, -v, -vv, -vvv オプションからデバッグレベルを取得して設定する。
	// @return マッチしなかった場合はfalse
	private boolean setDebugLevel(String param) {

		HashMap<String, java.util.logging.Level> debugLevelMap = new HashMap<String, java.util.logging.Level>();
		debugLevelMap.put("-debug", java.util.logging.Level.FINE);
		debugLevelMap.put("-v", java.util.logging.Level.FINE);
		debugLevelMap.put("-vv", java.util.logging.Level.FINER);
		debugLevelMap.put("-vvv", java.util.logging.Level.ALL);

		// パラメータからレベルを選択
		if (debugLevelMap.get(param) == null) {
			// マッチしない時はfalse
			return false;
		}

		// このアプリのpackage名の上位であるjavamailのロガーを取得
		Logger toolLog = Logger.getLogger("jp.or.rim.yk.george.javamail");
		// ログレベル設定
		// toolのログレベルを設定
		toolLog.setLevel(debugLevelMap.get(param));
		// ハンドラー設定
		toolLog.addHandler(new StreamHandler() {
			{
				setOutputStream(System.err);
				// ハンドラーの中でもログレベル設定
				setLevel(debugLevelMap.get(param));
			}
		});
		// logのsetLevelと、HandlerのsetLevelと、両方設定しないといけない。
		// info以上は２回出力されるオマケ付き
		log.info("turn on debug mode:" + AppMain.class.getName());

		return true;
	}

	/**
	 * エラーメッセージを出力し、異常終了する
	 *
	 * @param msg
	 */
	private static void usage(String msg) {
		if ("".equals(msg)) {
			// 引数が "" の場合はヘルプ全体を出力
			// リソース読み込み
			String build = null;
			try (InputStream is = AppMain.class.getClassLoader().getResourceAsStream("buildnumber.properties");) {
				if (is != null) {
					Properties prop = new Properties();
					prop.load(is);
					build = prop.getProperty("build.number");
				}
			} catch (IOException e) {
				// 握りつぶす
			} catch (Throwable e) {
				// 握りつぶす
			}

			msg += usageMsg1;
			if (build != null) {
				msg += usageMsg2 + build + "\n";
			}
			msg += usageMsg3;
		}
		System.err.print(msg);
	}

}
