#/usr/bin/bash
#
# リリース用のzipを作る
#
# 2024.10.05
#

VERSION=build21

SRCZIP=../javamail_${VERSION}_src.zip
BINZIP=../javamail_${VERSION}.zip

/bin/rm -f $SRCZIP $BINZIP

#
# ソースコードzipを作成
#
zip -r  $SRCZIP  *  -x ".classpath/*" -x ".git/*" -x ".gradle/*" -x ".settings/*" -x ".vscode/*" -x "bin/*" -x "build/*" -x "sampledata/*" -x "target/*"


#
# バイナリzipを作成
#
pushd  target

cat > javamail.sh << "EOF"
#!/bin/bash
java -jar ~/bin/javamail.jar "$@"
EOF

zip -r  ../$BINZIP  javamail.jar  javamail.sh

popd
