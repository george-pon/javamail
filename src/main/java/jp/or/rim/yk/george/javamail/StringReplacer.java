package jp.or.rim.yk.george.javamail;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ある正規表現がマッチする文字列を、別の文字列に置き換えるクラスを 示すインターフェース。
 */
public interface StringReplacer {
	/**
	 * このクラスが検索に使う正規表現のパターンを返す。
	 * @return 正規表現。
	 */
	public Pattern getPattern();
	
	/**
	 * ある正規表現のマッチを別の文字列に置き換える。
	 * @param	m	置換元の正規表現のマッチ。
	 * @return	置換先の文字列。
	 */
	public String replace(Matcher m);

}
