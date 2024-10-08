#----------------------------------------------------------------------------
# for maven
#

# mvn ローカルリポジトリを再度ダウンロードしたりリフレッシュしたり
function f-mvn-repo-refresh {
    mvn dependency:purge-local-repository
}

# ビルド番号をインクリメントして得る
function f-get-build-no {
    if (Test-Path ./build-no.ps1) {
        . ./build-no.ps1
    }
    else {
        $buildno = 0
    }
    $buildno++
    Write-Output "`$buildno=$buildno" > ./build-no.ps1
    return $buildno
}

# ビルド番号を読み込む。インクリメントはしない。
function f-read-build-no {
    if (Test-Path ./build-no.ps1) {
        . ./build-no.ps1
    }
    else {
        $buildno = 1
        Write-Output "`$buildno=$buildno" > ./build-no.ps1
    }
    return $buildno
}

function f-maven-build {
    $buildno = f-get-build-no
    Write-Output "buildno is ${buildno}"
    & mvn "-Drevision=${buildno}" clean package
}

# ビルドと起動 (maven)
function f-maven-build-run {
    $buildno = f-get-build-no
    Write-Output "buildno is ${buildno}"
    & mvn "-Drevision=${buildno}" clean package dependency:copy-dependencies -DincludeScope=runtime
    java -jar ./target/javamail-1.0-${buildno}-jar-with-dependencies.jar
}

# ビルドと起動 (maven)
function f-maven-build-run-decode {
    $buildno = f-get-build-no
    Write-Output "buildno is ${buildno}"
    & mvn "-Drevision=${buildno}" clean package dependency:copy-dependencies -DincludeScope=runtime
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/nicos.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/amazon.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/spam-amazon.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/paypal.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/pixiv.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/instagram.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/apple-mail.txt
    # ログ詳細を出すなら -vvv を指定
    #java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  -vvv  ./sampledata/apple-mail.txt
}

# 起動するだけ (maven)
function f-maven-run-decode {
    $buildno = f-read-build-no
    Write-Output "buildno is ${buildno}"
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/nicos.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/amazon.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/spam-amazon.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/paypal.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/pixiv.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/instagram.txt
    java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  ./sampledata/apple-mail.txt
    # ログ詳細を出すなら -vvv を指定
    #java -jar ./target/javamail-1.0.${buildno}-jar-with-dependencies.jar  -vvv  ./sampledata/apple-mail.txt
}

# maven で作った javamail を freebsd にリリースする
function f-maven-build-release-freebsd {
    $buildno = f-read-build-no
    Write-Output "buildno is ${buildno}"

    # シェルの作成。改行コードがCRLFになってしまうのでそこは手動で修正する必要がある。
    $main_str = @"
#!/bin/bash
java -jar ~/bin/javamail.jar "$@"
"@
    $main_file = "./target/javamail"
    if ( Test-Path $main_file ) {
        Remove-Item $main_file
    }
    Write-Output $main_str | Add-Content -Encoding UTF8 "${main_file}"

    scp ./target/javamail.jar freebsd66:bin
    # 既に freebsd 側に存在する場合はコメント
    # scp ./target/javamail freebsd66:bin
}

# 開発開始時、maven build 起動まで実施
function f-maven-setup-all {
    f-maven-build
    if ( $LASTEXITCODE -ne 0 ) { Write-Host "build failed." ; return }

    # run eclipse
    f-eclipse
    Start-Sleep -Milliseconds 10000
    f-wait-for-disk-idle

    # run visual studio code
    code . -r
    Start-Sleep -Milliseconds 10000
    f-wait-for-disk-idle

    f-maven-build-run-fetch
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build and run failed." ; return 1 }
}

#----------------------------------------------------------------------
# gradle 今は使っていない
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


#-----------------------------------------------------------------------


# ライブラリを追加した場合などに、eclipseの設定ファイルを作り直す
function f-gradle-eclipse-setup {
    gradle --warning-mode all     clean cleanEclipse   eclipse
}

#----------------------------------------------------------------------
# eclipse起動
function f-eclipse {
    Push-Location
    Set-Location C:\HOME\Eclipse-JEE-2024-09-R\eclipse
    Start-Process C:\HOME\Eclipse-JEE-2024-09-R\eclipse\eclipse.exe
    Pop-Location
}

#----------------------------------------------------------------------
# disk の idle percent を取得する
#
function f-getDiskPerf {
    $max_idle = 0;

    $samplesC = Get-Counter -Counter "\LogicalDisk(c:)\% Disk Time" -SampleInterval 1 -MaxSamples 3;
    $idleC = $samplesC.CounterSamples.CookedValue | Measure-Object -Average | Select-Object -ExpandProperty Average;
    Write-Output "idleC : $idleC"
    if ( $idleC -gt $max_idle ) {
        $max_idle = $idleC
    }

    $samplesD = Get-Counter -Counter "\LogicalDisk(d:)\% Disk Time" -SampleInterval 1 -MaxSamples 3;
    $idleD = $samplesD.CounterSamples.CookedValue | Measure-Object -Average | Select-Object -ExpandProperty Average;
    Write-Output "idleD : $idleD"
    if ( $idleD -gt $max_idle ) {
        $max_idle = $idleD
    }
    Write-Output "max_idle : $max_idle"
    return $max_idle
}

#----------------------------------------------------------------------
# disk が暇になるまで待機する
#
function f-wait-for-disk-idle {

    while ($true) {
        $idle_percent = f-getDiskPerf
        if ($idle_percent -lt 20) {
            return;
        }
    }
}


#----------------------------------------------------------------------
# 開発開始時、ビルド、eclipse設定ファイル生成、gradle tomcat 起動まで実施
function f-gradle-setup-all {
    f-gradle-build
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build failed." ; return 1 }
    f-gradle-eclipse-setup
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build eclipse setup." ; return 1 }

    # run eclipse
    f-eclipse
    Start-Sleep -Milliseconds 10000
    f-wait-for-disk-idle

    # run visual studio code
    code . -r
    Start-Sleep -Milliseconds 10000
    f-wait-for-disk-idle

    f-gradle-build-run
    if ( $LASTEXITCODE -ne 0 ) { Write-Output "build and run failed." ; return 1 }
}

#
# end of file
#
