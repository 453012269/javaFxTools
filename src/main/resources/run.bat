@echo off
chcp 65001
setlocal enabledelayedexpansion

REM ==== 配置区域（可按需修改）====
set JPACKAGE_PATH=D:\tools\jdk\jdk-23.0.2\bin\jpackage.exe
set APP_NAME=FxTools
set MAIN_MODULE=plugin.javafxtools
set MAIN_CLASS=plugin.javafxtools.ToolsApplication
set RUNTIME_IMAGE=D:\github\javaFxTools\target\app
set ICON_PATH=D:\github\javaFxTools\target\classes\favicon.ico
set OUTPUT_DIR=dist

REM 检查jpackage路径
if not exist "%JPACKAGE_PATH%" (
    echo [ERROR] jpackage未找到：%JPACKAGE_PATH%
    pause
    exit /b 1
)

REM 检查运行时镜像目录
if not exist "%RUNTIME_IMAGE%" (
    echo [ERROR] 运行时镜像目录不存在：%RUNTIME_IMAGE%
    pause
    exit /b 1
)

REM 检查主图标文件
if not exist "%ICON_PATH%" (
    echo [WARN] 未找到图标文件：%ICON_PATH%
)

REM 创建输出目录
if not exist "%OUTPUT_DIR%" (
    mkdir "%OUTPUT_DIR%"
)

REM 打包应用
echo [INFO] 正在执行jpackage打包...
"%JPACKAGE_PATH%" ^
  --name "%APP_NAME%" ^
  --type app-image ^
  -m "%MAIN_MODULE%/%MAIN_CLASS%" ^
  --runtime-image "%RUNTIME_IMAGE%" ^
  --icon "%ICON_PATH%" ^
  --dest "%OUTPUT_DIR%"

if errorlevel 1 (
    echo [ERROR] jpackage 执行失败
    pause
    exit /b 2
)

echo [INFO] 打包完成，输出目录：%OUTPUT_DIR%
pause
endlocal