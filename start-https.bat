@echo off
rem ============================================
rem Orbion - HTTPS mode (required for voice)
rem Generates a self-signed certificate on first
rem run, then starts Orbion on https://...:8443
rem ============================================

rem Keystore password: override via the ORBION_KEYSTORE_PASSWORD env var.
if "%ORBION_KEYSTORE_PASSWORD%"=="" set ORBION_KEYSTORE_PASSWORD=orbion

if not exist orbion.p12 (
  echo Generating self-signed certificate...
  keytool -genkeypair -alias orbion -keyalg RSA -keysize 2048 -validity 825 ^
    -storetype PKCS12 -keystore orbion.p12 -storepass %ORBION_KEYSTORE_PASSWORD% ^
    -dname "CN=Orbion Local"
  if errorlevel 1 exit /b 1
)

if not exist target\orbion.jar (
  echo Building application...
  call mvn clean package -DskipTests
  if errorlevel 1 exit /b 1
)

java -jar target\orbion.jar --spring.profiles.active=https
