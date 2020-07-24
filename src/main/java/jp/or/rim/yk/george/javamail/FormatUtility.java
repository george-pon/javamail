package jp.or.rim.yk.george.javamail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 整形のためのユーティリティクラス。
 */
public class FormatUtility {
	/** デフォルトコンストラクタ。 */
	private FormatUtility() {
	}

	/**
	 * 文字列置換の正規表現のテスト用
	 * 
	 * @param s
	 * @param rep
	 * @return
	 */
	public static String replaceRegExpTest(String s, StringReplacer rep) {

		// ひとつも見つからなければ、そのまま返す
		Matcher m = rep.getPattern().matcher(s);
		boolean matched = m.find();
		if (!matched) {
			return "not match. :"+s;
		}

		// 順に置換する
		StringBuilder dst = new StringBuilder();
		int lastIndex = 0;
		while (matched) {
			dst.append(s.substring(lastIndex, m.start(0)));
			dst.append("\n");
			for ( int i = 0 ; i <= m.groupCount() ; i++ ) {
				dst.append("group["+i+"]:"+m.group(i));
				dst.append("\n");
			}
			dst.append("\n");
			dst.append(rep.replace(m));
			lastIndex = m.end(0);

			// 次を検索
			matched = m.find();
		}
		dst.append(s.substring(lastIndex));

		return dst.toString();

	}

	/**
	 * ある文字列のなかの正規表現を、順に検索し、置換クラスを用いて取得した 置換後の文字列で置き換える。
	 * 
	 * @param s   置換する文字列。
	 * @param rep 使用する置換クラス。
	 * @return 置換後の文字列。
	 */
	public static String replaceRegExp(String s, StringReplacer rep) {

		// ひとつも見つからなければ、そのまま返す
		Matcher m = rep.getPattern().matcher(s);
		boolean matched = m.find();
		if (!matched) {
			return s;
		}

		// 順に置換する
		StringBuilder dst = new StringBuilder();
		int lastIndex = 0;
		while (matched) {
			dst.append(s.substring(lastIndex, m.start(0)));
			dst.append(rep.replace(m));
			lastIndex = m.end(0);

			// 次を検索
			matched = m.find();
		}
		dst.append(s.substring(lastIndex));

		return dst.toString();
	}

	/**
	 * ある文字列のなかの正規表現を、順に検索し、置換クラスを用いて取得した 置換後の文字列で置き換える。
	 * 
	 * 左側文字に対して、禁止パターンを指定できる。
	 * 
	 * @param s   置換する文字列。
	 * @param rep 使用する置換クラス。
	 * @return 置換後の文字列。
	 */
	public static String replaceRegExpNotLeft(String s, StringReplacerNotLeft rep) {
		StringBuilder dst;
		Matcher m;
		boolean matched;
		int lastIndex;

		m = rep.getPattern().matcher(s);

		// ひとつも見つからなければ、そのまま返す
		matched = m.find();
		if (!matched) {
			return s;
		}

		// 順に置換する
		dst = new StringBuilder();
		lastIndex = 0;
		while (matched) {
			// 左側文字列の禁止属性をチェック
			String strLeft = s.substring(lastIndex, m.start(0));
			Matcher antim_l = rep.getAntiPatternLeft().matcher(strLeft);
			String strRight = s.substring(m.end(0));
			Matcher antim_r = rep.getAntiPatternRight().matcher(strRight);
			if (antim_l.find() == false && antim_r.find() == false) {
				// anti pattern (left) と (right) にヒットしなければ、置換実施
				dst.append(s.substring(lastIndex, m.start(0)));
				dst.append(rep.replace(m));
				lastIndex = m.end(0);
			} else {
				// anti pattern にヒットしたので、次へ
				dst.append(s.substring(lastIndex, m.end(0)));
				lastIndex = m.end(0);
			}

			// 次を検索
			matched = m.find();
		}
		dst.append(s.substring(lastIndex));

		return dst.toString();
	}

	/**
	 * ある文字列のなかの正規表現を、順に検索し、置換クラスを用いて取得した 置換後の文字列で置き換える。
	 * 
	 * 指定されたパターンの中は、置換禁止となる。
	 * 
	 * @param s   置換する文字列。
	 * @param rep 使用する置換クラス。
	 * @return 置換後の文字列。
	 */
	public static String replaceRegExpNotLeftNotIn(String line, StringReplacerNotLeftNotIn rep) {
		List<Integer> startIdxs;
		List<Integer> endIdxs;
		StringBuilder dst;
		Matcher m;
		boolean matched;
		int lastIndex;

		// 置換対象が出現しても無視する部分を検索
		startIdxs = new ArrayList<Integer>();
		endIdxs = new ArrayList<Integer>();
		for (int i = 0; i < rep.getAntiPatterns().length; i++) {
			Pattern ptn = rep.getAntiPatterns()[i];

			m = ptn.matcher(line);
			while (m.find()) {
				startIdxs.add((m.start(0)));
				endIdxs.add((m.end(0)));
			}
		}

		// 置換
		m = rep.getPattern().matcher(line);

		// ひとつも見つからなければ、そのまま返す
		matched = m.find();
		if (!matched) {
			return line;
		}

		// マッチしている間は置換ループ
		dst = new StringBuilder();
		lastIndex = 0;
		while (matched) {
			int s;
			int e;
			boolean ignore = false;

			// マッチの開始位置と終了位置を取得
			s = m.start(0);
			e = m.end(0);

			// 左側文字列の禁止属性をチェック
			String strLeft = line.substring(lastIndex, m.start(0));
			Matcher antim_l = rep.getAntiPatternLeft().matcher(strLeft);
			String strRight = line.substring(m.end(0));
			Matcher antim_r = rep.getAntiPatternRight().matcher(strRight);
			if (antim_l.find() == true || antim_r.find() == true) {
				ignore = true;
			}

			// 無視するか判定
			for (int i = 0; i < startIdxs.size(); i++) {
				int sn = startIdxs.get(i).intValue();
				int en = endIdxs.get(i).intValue();

				if (sn <= s && e <= en) {
					ignore = true;
					break;
				}
			}

			/*
			 * 置換の実行
			 */
			if (!ignore) {
				// 置換部分の前までの文字列を追加
				dst.append(line.substring(lastIndex, m.start(0)));
				dst.append(rep.replace(m));
				lastIndex = m.end(0);
			}

			// 次を検索
			matched = m.find();
		}

		// 残り部分追加
		dst.append(line.substring(lastIndex));

		return dst.toString();
	}

	/**
	 * 文字列の特定の部分を置き換える。
	 * 
	 * @param src   置き換え対象の文字列。
	 * @param bi    置き換えを開始する文字の位置。
	 * @param ei    置き換えを終了する文字の位置+1。
	 * @param patch 置き換える文字列。
	 * @return 置き換えた文字列。
	 */
	public static String splice(String src, int bi, int ei, String patch) {
		StringBuffer sb;
		String before;
		String after;

		before = src.substring(0, bi);
		after = src.substring(ei);

		sb = new StringBuffer(src.length() - (ei - bi) + patch.length());
		sb.append(before);
		sb.append(patch);
		sb.append(after);

		return sb.toString();
	}

}
