@echo off
rem Compiles the app into pl-stats.jar without Maven. Needs JDK 17+ (no other dependencies).
rem (In IntelliJ, or with Maven installed, use "mvn package" instead - output goes to target\pl-stats.jar.)
setlocal
cd /d "%~dp0"

rem Find jar.exe: on PATH, else in the JDK that "java" belongs to.
set "JAR=jar"
where jar >nul 2>nul || (
  for /f "tokens=2 delims==" %%h in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home"') do set "JHOME=%%h"
)
if defined JHOME set "JAR=%JHOME:~1%\bin\jar.exe"

if exist out rmdir /s /q out
mkdir out
rem javac follows App's imports through the source path, so every class gets compiled.
javac -encoding UTF-8 -d out --source-path src\main\java src\main\java\pl\App.java || exit /b 1
"%JAR%" --create --file pl-stats.jar --main-class pl.App -C out . || exit /b 1
rmdir /s /q out
echo Built pl-stats.jar
