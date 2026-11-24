@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
set "AUTOCHECK_JAR=target\autocheck-1.2.0.jar"
rem Use the same compatible Java for both Maven and the application.
set "AUTOCHECK_JAVA_HOME="
if defined JAVA_HOME call :tryJava "%JAVA_HOME%\bin\java.exe"
if defined AUTOCHECK_JAVA_HOME goto javaReady
for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do (
  call :tryJava "%%J"
  if defined AUTOCHECK_JAVA_HOME goto javaReady
)
echo Java 17 or newer is required. Building also requires a JDK, not just a JRE.
echo Install a compatible JDK and set JAVA_HOME to its installation directory.
pause
exit /b 1

:javaReady
set "JAVA_HOME=%AUTOCHECK_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using Java: "%JAVA_HOME%"
if not exist "%AUTOCHECK_JAR%" (
  where.exe mvn.cmd >nul 2>&1
  if errorlevel 1 (
    echo Maven was not found. Install Maven and add its bin directory to PATH.
    pause
    exit /b 1
  )
  echo Building AutoCheck. The first build may need to download dependencies.
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo Build failed. Please install JDK 17 and Maven, then retry.
    pause
    exit /b 1
  )
)
"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%AUTOCHECK_JAR%"
if errorlevel 1 pause
exit /b

:tryJava
if not exist "%~1" exit /b
set "AUTOCHECK_CANDIDATE="
set "AUTOCHECK_VERSION="
for /f "tokens=1,* delims==" %%A in ('""%~1" -XshowSettings:properties -version 2>&1 | findstr /c:"java.home =" /c:"java.specification.version =""') do (
  for /f "tokens=*" %%K in ("%%A") do (
    if "%%K"=="java.home " for /f "tokens=*" %%V in ("%%B") do set "AUTOCHECK_CANDIDATE=%%V"
    if "%%K"=="java.specification.version " for /f "tokens=1 delims=. " %%V in ("%%B") do set "AUTOCHECK_VERSION=%%V"
  )
)
if not defined AUTOCHECK_CANDIDATE exit /b
if not defined AUTOCHECK_VERSION exit /b
if %AUTOCHECK_VERSION% LSS 17 exit /b
if not exist "%AUTOCHECK_JAR%" if not exist "%AUTOCHECK_CANDIDATE%\bin\javac.exe" exit /b
set "AUTOCHECK_JAVA_HOME=%AUTOCHECK_CANDIDATE%"
exit /b
