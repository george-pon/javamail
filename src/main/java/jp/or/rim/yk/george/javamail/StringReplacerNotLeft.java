package jp.or.rim.yk.george.javamail;

import java.util.regex.Pattern;

/**
 * ある正規表現がマッチする文字列を、別の文字列に置き換えるクラスを
 * 示すインターフェース。
 * 例外規定で、禁止条件を盛り込むことができる
 */
public interface StringReplacerNotLeft extends StringReplacer {
	
	/**
	 * このクラスが置換してはいけない例外ルール(左側)に使う正規表現のパターンを返す。
	 * @return 正規表現
	 */
	public Pattern getAntiPatternLeft();
	
	/**
	 * このクラスが置換してはいけない例外ルール(右側)に使う正規表現のパターンを返す。
	 * @return 正規表現
	 */
	public Pattern getAntiPatternRight();

}
