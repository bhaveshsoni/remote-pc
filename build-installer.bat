@echo off
rem ============================================
rem Orbion - Windows installer build script
rem Requires: JDK 21+ (jpackage included), Maven
rem Optional: WiX Toolset 3.x for .exe installers
rem ============================================

echo [1/2] Building application jar...
call mvn clean package -DskipTests
if errorlevel 1 exit /b 1

echo [2/2] Creating OrbionSetup.exe with bundled Java runtime...
jpackage ^
  --type exe ^
  --name Orbion ^
  --app-version 1.0.0 ^
  --vendor Orbion ^
  --description "Mobile-first remote control for AI tools" ^
  --input target ^
  --main-jar orbion.jar ^
  --java-options "-Djava.awt.headless=false" ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --dest dist
if errorlevel 1 exit /b 1

ren "dist\Orbion-1.0.0.exe" "OrbionSetup.exe"

echo.
echo Done! Installer: dist\OrbionSetup.exe
