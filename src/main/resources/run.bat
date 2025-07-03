@echo off
setlocal

REM Set the paths
set JPACKAGE_PATH="D:\tools\jdk\jdk-23.0.2\bin\jpackage"
set APP_NAME=FxTools
set TYPE=app-image
set MAIN_CLASS=plugin.javafxtools/plugin.javafxtools.ToolsApplication
set RUNTIME_IMAGE=D:\github\javaFxTools\target\app\
set ICON_PATH=D:\github\javaFxTools\target\classes\favicon.ico

REM Execute the jpackage command
%JPACKAGE_PATH% --name %APP_NAME% --type %TYPE% -m %MAIN_CLASS% --runtime-image %RUNTIME_IMAGE% --icon %ICON_PATH%

REM Pause to see the output
pause
endlocal