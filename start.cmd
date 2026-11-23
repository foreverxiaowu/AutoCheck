@echo off
setlocal
cd /d "%~dp0"
if not exist "target\autocheck-1.0.0.jar" (
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo Build failed. Please install JDK 17 and Maven, then retry.
    pause
    exit /b 1
  )
)
java -Dfile.encoding=UTF-8 -jar "target\autocheck-1.0.0.jar"
if errorlevel 1 pause
