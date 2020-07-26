#
# gradle build 用
#

# ビルド
function f-gradle-build {
    gradle --warning-mode all     clean build jar distZip
}

# ビルドと起動
function f-gradle-build-run {
    gradle --warning-mode all     clean build jar distZip run
}

# ビルドと起動
function f-gradle-build-run-sample {
    gradle --warning-mode all     clean build jar distZip run
    mkdir hoge
    Push-Location hoge
    tar xvf ../build/distributions/javamail.tar
    Copy-Item javamail/bin  /home   -Recurse -Force
    Copy-Item javamail/lib  /home   -Recurse -Force
    Pop-Location
    Remove-Item hoge -Recurse

    # javamail  -vvv sampledata/amazon.txt
    javamail  sampledata/nicos.txt
    javamail  sampledata/amazon.txt
    javamail  sampledata/spam-amazon.txt
    javamail  sampledata/beruna.txt
    javamail  sampledata/paypal.txt
    javamail  sampledata/pixiv.txt
}

# ライブラリを追加した場合などに、eclipseの設定ファイルを作り直す
function f-gradle-eclipse-setup {
    gradle --warning-mode all     clean cleanEclipse   eclipse
}

# 開発開始時、ビルド、eclipse設定ファイル生成、gradle tomcat 起動まで実施
function f-gradle-setup-all {
    f-gradle-build
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build failed." ; return 1 }
    f-gradle-eclipse-setup
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build eclipse setup." ; return 1 }

    Push-Location
    # Set-Location C:\home\Eclipse-4.8-Photon-JEE\eclipse
    # Start-Process C:\home\Eclipse-4.8-Photon-JEE\eclipse\eclipse.exe
    # Set-Location C:\home\Eclipse-2019-06-R-JEE\eclipse
    # Start-Process C:\home\Eclipse-2019-06-R-JEE\eclipse\eclipse.exe
    # Set-Location C:\home\Eclipse-2020-03R-JEE\eclipse
    # Start-Process C:\home\Eclipse-2020-03R-JEE\eclipse\eclipse.exe
    if ( Test-Path "C:\home\Eclipse-2020-06-R-JEE\eclipse\eclipse.exe" ) {
        Set-Location C:\home\Eclipse-2020-06-R-JEE\eclipse
        Start-Process C:\home\Eclipse-2020-06-R-JEE\eclipse\eclipse.exe
    }
    if ( Test-Path "C:\eclipse-jee-2020-06-R\eclipse\eclipse.exe" ) {
        Set-Location C:\eclipse-jee-2020-06-R\eclipse\
        Start-Process C:\eclipse-jee-2020-06-R\eclipse\eclipse.exe
    }
    Pop-Location
    Start-Sleep -Milliseconds 55000

    # run visual studio code
    code . -r
    Start-Sleep -Milliseconds 15000
}

f-gradle-setup-all

