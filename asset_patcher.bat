@echo off
REM ============================================================================
REM External Third Party Asset Patcher for SocketReforge
REM ============================================================================
REM 
REM This batch file runs the asset_patcher.py script to:
REM 1. Open Assets.zip and grab all weapon_.json files
REM 2. Open each jar in the MOD_ROOT and grab all json treated as weapons
REM 3. Clone each items to WEAPONS_PATH directory + subfolders per weapon type
REM 4. Generate all necessary weapon upgrade clones, set BenchRequirement to Reforgebench
REM 5. Generate server.lang file in LANG_PATH
REM 6. Generate manifest.json in MANIFEST_PATH
REM
REM Usage:
REM   asset_patcher.bat                      - Auto-detect (server path first, then client)
REM   asset_patcher.bat -c                    - Use client assets path
REM   asset_patcher.bat -s "path\to\weapons" - Specify custom source path
REM   asset_patcher.bat -h                    - Show help
REM
REM Default paths:
REM   Server: server/mods/HytaleAssets/Server/Item/Items/Weapon
REM   Client: Hytale/install/release/package/game/latest/assets/Server/Item/Items/Weapon
REM
REM ============================================================================

echo.
echo ============================================================
echo External Third Party Asset Patcher for SocketReforge
echo ============================================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH
    echo Please install Python 3 from https://www.python.org/
    echo.
    pause
    exit /b 1
)

REM Check for help flag
if "%~1"=="-h" goto show_help
if "%~1"=="--help" goto show_help
if "%~1"=="/?" goto show_help

REM Get the directory where this batch file is located
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Run the asset patcher with any arguments passed + -y to skip confirmation
echo [*] Running asset patcher with args: %*
echo.

python asset_patcher.py %* -y

echo.
echo ============================================================
echo Asset Patcher Finished
echo ============================================================
echo.

pause
exit /b 0

:show_help
echo Usage: asset_patcher.bat [options]
echo.
echo Options:
echo   -h, --help           Show this help message
echo   -s, --source PATH    Source weapons folder (auto-detected if not specified)
echo   -o, --output PATH    Output MOD_ROOT folder path
echo   -c, --client         Use client assets path
echo   -y                   Skip confirmation (already applied by default)
echo.
echo Default paths:
echo   Server: server/mods/HytaleAssets/Server/Item/Items/Weapon
echo   Client: Hytale/install/release/package/game/latest/assets/Server/Item/Items/Weapon
echo.
echo Examples:
echo   asset_patcher.bat
echo   asset_patcher.bat -c
echo   asset_patcher.bat -c -y
echo   asset_patcher.bat -s "C:\Game\HytaleAssets\Server\Item\Items\Weapon"
echo.
pause
exit /b 0
