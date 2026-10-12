@echo off
REM ---------------------------------------------------------------------------
REM  Shadow-Mapper launcher (Windows)
REM
REM  Two things are required beyond a plain `java -jar`:
REM    1. A JDK 11-17 to build.  Built here with Temurin 17 (JAVA_HOME below).
REM    2. --add-opens java.desktop/sun.awt=ALL-UNNAMED at RUN time: JOGL 2.3.2
REM       reflects into sun.awt.Win32GraphicsConfig to pick a pixel format.
REM       Java 17+ strongly encapsulates that package, the reflection silently
REM       fails and GLCanvas dies with
REM       "ArrayIndexOutOfBoundsException ... WindowsAWTWGLGraphicsConfigurationFactory".
REM
REM  Build first (once):   gradlew.bat installDist   with JAVA_HOME set to a JDK 17
REM ---------------------------------------------------------------------------
setlocal
set "JAVA_HOME=D:\Modding\GTA_IV\Tools\jdk17\jdk-17.0.20.1+1"
set "JAVA_OPTS=--add-opens java.desktop/sun.awt=ALL-UNNAMED"
cd /d "%~dp0"

if not exist "build\install\Shadow-Mapper\bin\Shadow-Mapper.bat" (
    echo [run-shadow-mapper] build\install not found - run: gradlew.bat installDist
    exit /b 1
)

REM launch from this directory so installs.ini is written here
call "build\install\Shadow-Mapper\bin\Shadow-Mapper.bat" %*
