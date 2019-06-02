@echo off

if exist "jre\bin\java.exe" (
    set JAVA="jre\bin\java.exe"
    goto run
)

if exist "%JAVA_HOME%\bin\java.exe" (
    set JAVA="%JAVA_HOME%\bin\java.exe" 
    goto run
)

where java
if ERRORLEVEL 1 (
    echo Java not found, please install from ...
    echo   https://www.java.com/en/download/win10.jsp
    echo   https://www.java.com/ru/download/win10.jsp
    echo.
    pause 
    goto :eof
)

set JAVA=java

:run
%JAVA% %*
pause