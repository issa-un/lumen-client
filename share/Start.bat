@echo off
rem Starts the injector window with the Java 25 that comes with the Minecraft Launcher.
setlocal
set "JAR=%~dp0spear-injector.jar"
set "RT=runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\javaw.exe"

rem Microsoft Store / Xbox app launcher
set "JAVAW=%LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\%RT%"
if exist "%JAVAW%" goto run
rem Classic launcher (installed for all users, or in .minecraft)
set "JAVAW=%ProgramFiles(x86)%\Minecraft Launcher\%RT%"
if exist "%JAVAW%" goto run
set "JAVAW=%ProgramFiles%\Minecraft Launcher\%RT%"
if exist "%JAVAW%" goto run
set "JAVAW=%APPDATA%\.minecraft\%RT%"
if exist "%JAVAW%" goto run
rem Your own Java 25 or newer, if JAVA_HOME points at it
set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
if defined JAVA_HOME if exist "%JAVAW%" goto run

echo Could not find Java 25.
echo Start Minecraft 26.1.2 once from the official launcher, or set JAVA_HOME to a Java 25 JDK.
pause
exit /b 1

:run
start "" "%JAVAW%" --add-modules=jdk.attach -jar "%JAR%"
