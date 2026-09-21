@echo off
rem Fruit Jump launcher - JDK 27 + JavaFX 27
rem Paths point at the JavaFX 27 SDK in Downloads. If you move the SDK,
rem update JFX_LIB below.
set JFX_LIB=%USERPROFILE%\Downloads\javafx-sdk-27\lib
set JAVA=%ProgramFiles%\Java\jdk-27\bin\java.exe

rem JavaFX native DLLs must be on PATH for glass/prism to load
set PATH=%USERPROFILE%\Downloads\javafx-sdk-27\bin;%PATH%

"%JAVA%" --module-path "%JFX_LIB%" --add-modules javafx.controls,javafx.graphics,javafx.media -jar "%~dp0fruit-jump-27.jar"
