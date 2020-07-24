# javamail

mailのmime decodeを行う。

Java 11で使える良さそうなライブラリが無かったので自作してみた。

* メールヘッダ部は ISO-2022-JP , UTF-8 なもの。
* メール本文は、Content-Type: text/plain で charset=ISO-2022-JP なもの。
* メール本文は、Content-Type: multipart/alternative で、 Content-Transfer-Encoding: base64 or quoted-printable なもの。

### 使い方

標準出力に変換結果を出力する。

```
# ファイルから読み込んで変換
javamail sample/amazon.txt

# 標準入力から読み込んで変換
cat sample/amaozn.txt | javamail -
```

