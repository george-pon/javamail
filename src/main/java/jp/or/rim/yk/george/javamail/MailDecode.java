package jp.or.rim.yk.george.javamail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailDecode {

	// ログ
	private static Logger log = Logger.getLogger(MailDecode.class.getName());

	// 内部変数
	boolean inHeaderPart = true;
	boolean inBodyPart = false;
	String currentHeaderKey = "";
	Map<String, String> headerMap = new HashMap<String, String>();
	Map<String, String> headerContentTypeMap = new HashMap<String, String>();
	Charset headerCharset = StandardCharsets.ISO_8859_1;
	String bodyType = "text/plain";
	Charset bodyCharset = StandardCharsets.ISO_8859_1;
	String bodyContentTransferEncoding = "7bit";

	// 以下ボディ部のマルチパート用
	String bodyBoundary = null;
	String bodyBoundaryEnd = null;
	String bodyBoundaryContentType = "text/plain";
	String bodyBoundaryCharset = "UTF-8";
	String bodyBoundaryTransferEncoding = "quoted-printable";
	StringBuffer bodyBoundaryLineBuffer = new StringBuffer();
	StringBuffer bodyBoundaryPrintableQuotedHtmlBuffer = new StringBuffer();
	StringBuffer bodyBoundaryBase64Buffer = new StringBuffer();
	StringBuffer bodyBoundaryBase64HtmlBuffer = new StringBuffer();
	Map<String, String> bodyBoundaryMap = new HashMap<String, String>();
	Map<String, String> bodyBoundaryContentTypeMap = new HashMap<String, String>();
	Map<String, String> bodyBoundaryContentTransferEncodingMap = new HashMap<String, String>();

	/** 0: まだ入ってない 1:マルチパートのヘッダ部 2:マルチパートのボディ部 */
	int inBodyBoundaryPartNumber = 0;

	/** 結果出力用 */
	StringBuilder sbResult = new StringBuilder();

	/**
	 * メールデコードエントリー for ファイル
	 *
	 * @param file
	 * @return
	 */
	public String decode(File file) {
		try (FileInputStream fis = new FileInputStream(file);) {
			return decode(fis);
		} catch (FileNotFoundException e) {
			String msg = "file not found";
			log.log(Level.SEVERE, msg, e);
			return null;
		} catch (IOException e) {
			String msg = "io exception";
			log.log(Level.SEVERE, msg, e);
			return null;
		}
	}

	/**
	 * メールデコードエントリー for InputStream
	 *
	 * @param in 入力ストリーム
	 * @return
	 */
	public String decode(InputStream in) {
		try {
			boolean outerloop = true;
			while (outerloop) {

				// １行を切り出す。最初は文字エンコーディングが不明なのでReaderは使用できない。
				ByteBuffer bb = ByteBuffer.allocate(64 * 1024);
				while (true) {
					int b = in.read();
					if (b < 0) {
						outerloop = false;
						break;
					}

					if (b == '\r') {
						continue;
					}
					// end of line
					if (b == '\n') {
						break;
					}

					bb.put((byte) b);
				}
				// flipしてバッファ上限を設定
				bb.flip();

				// -----------------------------------------------------
				// メールヘッダ部分読み込み
				//
				if (inHeaderPart == true) {

					// デコード
					String line = headerCharset.decode(bb).toString();
					log.finest("line:" + line.length() + ":" + line);

					// モード切替：ヘッダ中に空の行が来たら、Bodyのはじまり。
					if (line.equals("")) {
						inHeaderPart = false;
						inBodyPart = true;

						{
							// ログ出力
							StringBuilder sb = new StringBuilder();
							sb.append("\n");
							for (Map.Entry<String, String> entry : headerMap.entrySet()) {
								sb.append("headerMap:" + entry.getKey() + ":" + entry.getValue());
								sb.append("\n");
							}
							sb.append("\n");
							log.finer(sb.toString());
						}

						// Content-Typeの解析はここで行う
						headerContentTypeMap = parseContentType("Content-Type: " + headerMap.get("Content-Type"));
						{
							// ログ出力
							StringBuilder sb = new StringBuilder();
							sb.append("\n");
							for (Map.Entry<String, String> entry : headerContentTypeMap.entrySet()) {
								sb.append("contentTypeMap:" + entry.getKey() + ":" + entry.getValue());
								sb.append("\n");
							}
							sb.append("\n");
							log.finer(sb.toString());
						}

						// bodyの形式を設定
						if (headerContentTypeMap.get("Content-Type") != null) {
							bodyType = headerContentTypeMap.get("Content-Type");
						}
						if (headerContentTypeMap.get("boundary") != null) {
							bodyBoundary = "--" + headerContentTypeMap.get("boundary");
							bodyBoundaryEnd = "--" + headerContentTypeMap.get("boundary") + "--";
						}
						{
							String charsetName = headerContentTypeMap.get("charset");
							if (charsetName != null) {
								bodyCharset = Charset.forName(charsetName);
							}
						}
						{
							String s = headerMap.get("Content-Transfer-Encoding");
							if (s != null) {
								bodyContentTransferEncoding = s;
							}
						}

						log.fine("    bodyType:" + bodyType + "    bodyCharset:" + bodyCharset.name()
								+ "    bodyContentTransferEncoding:" + bodyContentTransferEncoding);

						sbResult.append("\n");

						continue;
					}

					// ヘッダ部キーワード切り替え：ヘッダでスペースから開始されるのは継続行
					if (line.startsWith("    ") || line.startsWith("\t")) {
						String old = headerMap.get(currentHeaderKey);
						if (old == null) {
							old = "";
						}
						headerMap.put(currentHeaderKey, old + line);
					} else {
						int idx = line.indexOf(":");
						if (idx >= 0) {
							String newHeaderKey = line.substring(0, idx).trim();
							String newHeaderValue = line.substring(idx + 1).trim();
							if (!newHeaderKey.equals(currentHeaderKey)) {
								currentHeaderKey = newHeaderKey;
								headerMap.put(newHeaderKey, newHeaderValue);
							}
						}
					}

					// Headerの処理。基本は１行１ヘッダ項目。
					// 先頭に空白がある場合は継続行。
					// =?文字セット?エンコード方式?エンコード後の文字列?=
					// =?ISO-2022-JP?B?xxxx?= Base64型
					// =?ISO-2022-JP?Q?xxxx?= Quoted-Printable

					// Content-Typeの解析
					// if (line.startsWith("Content-Type:")) {
					// if (line.equals("Content-Type: text/plain; charset=\"ISO-2022-JP\"")) {
					// bodyCharset = Charset.forName("ISO-2022-JP");
					// bodyType = "text/plain";
					// }
					// }

					// ヘッダ部分の文字列置換
					line = FormatUtility.replaceRegExp(line, new HeaderIso2022jpbase64Replacer());
					line = FormatUtility.replaceRegExp(line, new HeaderUtf8base64Replacer());

					sbResult.append(line);
					sbResult.append("\n");
				}

				// -----------------------------------------------------
				// メールBodyの処理。ここは色々なモードがある。
				//
				if (inBodyPart == true) {

					// デコード
					String line = bodyCharset.decode(bb).toString();

					log.finest("inBodyPart: bodyType: " + bodyType + "    line:" + line);

					if (bodyType.equals("text/plain")) {
						// text/plainの場合
						if (bodyContentTransferEncoding.equals("7bit")) {
							log.finest("text/plain 7bit line:" + line.length() + ":" + line);
							sbResult.append(line);
							sbResult.append("\n");
						} else if (bodyContentTransferEncoding.equals("base64")) {
							log.finest("text/plain base64 line:" + line.length() + ":" + line);
							sbResult.append(line);
							sbResult.append("\n");
						} else if (bodyContentTransferEncoding.equals("8bit")) {
							log.finest("text/plain 8bit line:" + line.length() + ":" + line);
							sbResult.append(line);
							sbResult.append("\n");
						} else if (bodyContentTransferEncoding.equalsIgnoreCase("quoted-printable")) {
							log.finest("text/plain quoted-printable line:" + line.length() + ":" + line);
							// quoted-printable の場合
							// 行終端が = の場合は継続行
							if (line.endsWith("=")) {
								// バッファに追加して次へ
								bodyBoundaryLineBuffer.append(line.substring(0, line.length() - 1));
								continue;
							} else {
								// 一度バッファに追加してまとめて処理
								bodyBoundaryLineBuffer.append(line);
								String s = decodeQuotedPrintable(bodyBoundaryLineBuffer.toString(),
										bodyBoundaryCharset);
								sbResult.append(s);
								sbResult.append("\n");
								bodyBoundaryLineBuffer = new StringBuffer();
							}
						}
					} else if (bodyType.equals("text/html")) {
						// text/htmlの場合
						if (bodyContentTransferEncoding.equals("base64")) {
							// Base64バッファに貯めて続く
							bodyBoundaryBase64HtmlBuffer.append(line);
							log.finest(
									"text/html:" + bodyContentTransferEncoding + ":line:" + line.length() + ":" + line);
							continue;
						} else if (bodyContentTransferEncoding.equals("quoted-printable")) {
							log.finest("text/html quoted-printable line:" + line.length() + ":" + line);
							// text/html + quoted-printable の場合
							// 行終端が = の場合は継続行
							if (line.endsWith("=")) {
								// バッファに追加して次へ
								bodyBoundaryPrintableQuotedHtmlBuffer.append(line.substring(0, line.length() - 1));
								continue;
							} else {
								// 一度バッファに追加してまとめて処理
								bodyBoundaryPrintableQuotedHtmlBuffer.append(line);
								// バッファに追加して次へ
							}
						} else {
							// う。こんなのあるのか。
							log.finest(
									"text/html:" + bodyContentTransferEncoding + ":line:" + line.length() + ":" + line);
							// sbResult.append(line);
							// sbResult.append("\n");
						}
					} else if (bodyType.equals("multipart/alternative")) {
						// multipartの場合は色々

						log.finest("multipart/alternative" + "    part:" + inBodyBoundaryPartNumber + ":" + line);

						// マルチパートのBoundary開始チェック
						if (line.equals(bodyBoundary)) {
							multipartflush();
							inBodyBoundaryPartNumber = 1; // マルチパートヘッダ部
							log.finest("found bodyBoundary start:" + bodyBoundary + "    inBodyBoundaryPartNumber:"
									+ inBodyBoundaryPartNumber);
							continue;
						}
						// マルチパートのBoundary最後チェック
						if (line.equals(bodyBoundaryEnd)) {
							multipartflush();
							inBodyBoundaryPartNumber = 3; // マルチパート終了後
							log.finest("found bodyBoundary end:" + bodyBoundaryEnd + "    inBodyBoundaryPartNumber:"
									+ inBodyBoundaryPartNumber);
							continue;
						}

						if (inBodyBoundaryPartNumber == 1) {
							// Body Boundary Part 1 : ヘッダ部

							// ヘッダ部終了チェック：空行ならヘッダ部の終わり。
							if (line.length() == 0) {
								// ヘッダ部が終わった
								sbResult.append("\n");

								inBodyBoundaryPartNumber = 2; // マルチパートのボディ部に移行
								log.finest("found bodyBoundary Header end " + "    inBodyBoundaryPartNumber:"
										+ inBodyBoundaryPartNumber);
								// マルチパートのヘッダ部が終わったら、解析
								bodyBoundaryContentTypeMap = parseContentType(
										"Content-Type: " + bodyBoundaryMap.get("Content-Type"));
								if (bodyBoundaryContentTypeMap.get("Content-Type") != null) {
									bodyBoundaryContentType = bodyBoundaryContentTypeMap.get("Content-Type");
								}
								if (bodyBoundaryContentTypeMap.get("charset") != null) {
									bodyBoundaryCharset = bodyBoundaryContentTypeMap.get("charset");
								}
								bodyBoundaryContentTransferEncodingMap = parseContentType("Content-Transfer-Encoding: "
										+ bodyBoundaryMap.get("Content-Transfer-Encoding"));
								if (bodyBoundaryContentTransferEncodingMap.get("Content-Transfer-Encoding") != null) {
									bodyBoundaryTransferEncoding = bodyBoundaryContentTransferEncodingMap
											.get("Content-Transfer-Encoding");
								}
								log.fine("multipart body header values"
										+ "    bodyBoundaryContentType:" + bodyBoundaryContentType
										+ "    bodyBoundaryCharset:" + bodyBoundaryCharset
										+ "    bodyBoundaryTransferEncoding:" + bodyBoundaryTransferEncoding);
								continue;
							}

							// ボディ部マルチパートヘッダ部キーワード切り替え：ヘッダでスペースから開始されるのは継続行
							if (line.startsWith("    ") || line.startsWith("\t")) {
								String old = bodyBoundaryMap.get(currentHeaderKey);
								if (old == null) {
									old = "";
								}
								bodyBoundaryMap.put(currentHeaderKey, old + line);
							} else {
								int idx = line.indexOf(":");
								if (idx >= 0) {
									String newHeaderKey = line.substring(0, idx).trim();
									String newHeaderValue = line.substring(idx + 1).trim();
									if (!newHeaderKey.equals(currentHeaderKey)) {
										currentHeaderKey = newHeaderKey;
										bodyBoundaryMap.put(newHeaderKey, newHeaderValue);
									}
								}
							}

							sbResult.append(line);
							sbResult.append("\n");
						}
						if (inBodyBoundaryPartNumber == 2) {
							// Body Boundary Part 2 : ボディ部

							if (bodyBoundaryContentType.equals("text/plain")) {
								// text/plainの場合
								if (bodyBoundaryTransferEncoding.equalsIgnoreCase("quoted-printable")) {
									// quoted-printable の場合
									// 行終端が = の場合は継続行
									if (line.endsWith("=")) {
										// バッファに追加して次へ
										bodyBoundaryLineBuffer.append(line.substring(0, line.length() - 1));
										continue;
									} else {
										// 一度バッファに追加してまとめて処理
										bodyBoundaryLineBuffer.append(line);
										String s = decodeQuotedPrintable(bodyBoundaryLineBuffer.toString(),
												bodyBoundaryCharset);
										sbResult.append(s);
										sbResult.append("\n");
										bodyBoundaryLineBuffer = new StringBuffer();
									}
								} else if (bodyBoundaryTransferEncoding.equals("base64")) {
									// base64の場合
									// 一度バッファに追加してまとめて処理
									bodyBoundaryBase64Buffer.append(line);
								} else {
									// text/plain だが quoted-printabl , base64 ではない場合
									sbResult.append(line);
									sbResult.append("\n");
								}
							} else {
								// text/plain ではない場合
								// sb.append(line);
								// sb.append("\n");
							}
						}
					} else {
						// text/plain , multipart/alternative ではない場合
						// sb.append(line);
						// sb.append("\n");
					}
				}
			} // end of while outer loop

			// 最後にflush
			multipartflush();

		} catch (IOException e) {
			String msg = "ioexception";
			log.log(Level.SEVERE, msg, e);
			return null;
		}
		return sbResult.toString();
	}

	// バッファのフラッシュ処理
	private void multipartflush() {

		// マルチパートのBase64 (text/plain)
		if (bodyBoundaryBase64Buffer.length() > 0) {
			try {
				Base64.Decoder decoder = Base64.getDecoder();
				byte[] bytes = decoder.decode(bodyBoundaryBase64Buffer.toString().getBytes());
				String s = new String(bytes, bodyBoundaryCharset);
				sbResult.append(s);
			} catch (UnsupportedEncodingException e) {
				String msg = e.getMessage();
				log.severe(msg);
				sbResult.append(msg);
			}
			bodyBoundaryBase64Buffer = new StringBuffer();
		}

		// body部htmlのBase64 (text/html)
		if (bodyBoundaryBase64HtmlBuffer.length() > 0) {
			try {
				Base64.Decoder decoder = Base64.getDecoder();
				byte[] bytes = decoder.decode(bodyBoundaryBase64HtmlBuffer.toString().getBytes());
				String s = new String(bytes, bodyCharset.name());
				sbResult.append(s);
				log.finest("text/html body base64 result:");
			} catch (UnsupportedEncodingException e) {
				String msg = e.getMessage();
				log.severe(msg);
				sbResult.append(msg);
			}
			bodyBoundaryBase64HtmlBuffer = new StringBuffer();
		}

		// body部htmlのPrintableQuoted
		{
			String s = decodeQuotedPrintable(bodyBoundaryPrintableQuotedHtmlBuffer.toString(),
					bodyBoundaryCharset);
			sbResult.append(s);
			sbResult.append("\n");
			bodyBoundaryPrintableQuotedHtmlBuffer = new StringBuffer();
		}
	}

	// Content-Type等のヘッダ情報の属性を解析してMapに入れる
	private Map<String, String> parseContentType(String s) {
		Map<String, String> resultMap = new HashMap<String, String>();

		if (s == null) {
			return resultMap;
		}

		// 最初の:まで取得
		int idx = s.indexOf(":");
		if (idx < 0) {
			return resultMap;
		}
		String headerkey = s.substring(0, idx);
		s = s.substring(idx + 1);

		// 次のattrを分解
		String[] array = s.split(";", -1);
		if (array == null) {
			return resultMap;
		}

		for (String keyval : array) {
			// key=value の形、key="value"の形、text/plainの形がある。
			String[] arraysub = keyval.split("=", 2);
			if (arraysub.length == 1) {
				resultMap.put(headerkey, arraysub[0].trim());
			} else if (arraysub.length == 2) {
				String val = arraysub[1].trim();
				if (val.startsWith("\"") && val.endsWith("\"")) {
					val = val.substring(1);
					val = val.substring(0, val.length() - 1);
				}
				resultMap.put(arraysub[0].trim(), val);
			}
		}

		return resultMap;
	}

	/**
	 * メールヘッダ部のBase64を処理する。
	 *
	 * =?文字セット?エンコード方式?エンコード後の文字列?
	 *
	 * =?ISO-2022-JP?B?xxxx?= Base64型
	 *
	 */
	private class HeaderIso2022jpbase64Replacer implements StringReplacer {

		public HeaderIso2022jpbase64Replacer() {
		}

		@Override
		public Pattern getPattern() {
			// ヘッダ =?ISO-2022-JP?B?xxxx?= Base64型
			Pattern iso2022jpbase64Pattern = Pattern.compile("(=\\?ISO-2022-JP\\?B\\?)(.*?)(\\?=)");
			return iso2022jpbase64Pattern;
		}

		@Override
		public String replace(Matcher m) {
			String data = m.group(2);
			Base64.Decoder decoder = Base64.getDecoder();
			byte[] bytes = decoder.decode(data.getBytes());
			Charset charset = Charset.forName("ISO-2022-JP");
			String s = new String(bytes, charset);
			return s;
		}

	}

	/**
	 * メールヘッダ部のBase64を処理する。
	 *
	 * =?文字セット?エンコード方式?エンコード後の文字列?
	 *
	 * =?UTF-8?B?xxxx?= Base64型
	 *
	 */
	private class HeaderUtf8base64Replacer implements StringReplacer {

		public HeaderUtf8base64Replacer() {
		}

		@Override
		public Pattern getPattern() {
			Pattern utf8base64Pattern = Pattern.compile("(=\\?UTF-8\\?B\\?)(.*?)(\\?=)");
			return utf8base64Pattern;
		}

		@Override
		public String replace(Matcher m) {
			String data = m.group(2);
			Base64.Decoder decoder = Base64.getDecoder();
			byte[] bytes = decoder.decode(data.getBytes());
			Charset charset = Charset.forName("UTF-8");
			String s = new String(bytes, charset);
			return s;
		}

	}

	/**
	 * quoted printable をデコードする
	 *
	 * Amazon=E3=83=9D=E3=82=A4=E3=83=B3=E3=83=88=EF=BC=9A
	 *
	 * @param line
	 * @param charsetName
	 * @return
	 */
	String decodeQuotedPrintable(String line, String charsetName) {
		log.finest("charsetName:" + charsetName + "    line:" + line);
		Charset charset = Charset.forName(charsetName);
		ByteBuffer bb = ByteBuffer.allocate(1024 * 256); // 256KB
		for (int i = 0; i < line.length(); i++) {
			String s = line.substring(i, i + 1);
			if (s.equals("=")) {
				if (i + 1 == line.length()) {
					// 行終端の=は継続行の意味
				} else {
					String s2 = "0x" + line.substring(i + 1, i + 3);
					int ch = Integer.decode(s2);
					bb.put((byte) ch);
					i += 2;
				}
			} else {
				bb.put(s.getBytes(charset));
			}
		}
		bb.flip();
		String result = charset.decode(bb).toString();
		log.finest("result:" + result);
		return result;
	}

}
