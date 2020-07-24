package jp.or.rim.yk.george.javamail;

import java.util.regex.Pattern;

/**
 * ある正規表現がマッチする文字列を、別の文字列に置き換えるクラスを
 * 示すインターフェース。
 * 例外規定で、その中にない、という禁止条件を盛り込むことができる
 */
public interface StringReplacerNotLeftNotIn extends StringReplacerNotLeft {
	
	/**
	 * このクラスが置換してはいけない例外ルール(パターンの中にない)に使う正規表現のパターンを返す。
	 * @return 正規表現
	 */
	public Pattern[] getAntiPatterns();
	
}
