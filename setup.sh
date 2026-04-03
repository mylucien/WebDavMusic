#!/bin/bash
# Download gradle wrapper jar if missing
set -e

JAR=gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$JAR" ]; then
  echo "Gradle wrapper JAR is missing. Downloading..."
  
  # Method 1: Try to use installed gradle to generate wrapper
  if command -v gradle &>/dev/null; then
    echo "Found gradle installation, generating wrapper..."
    gradle wrapper --gradle-version 8.4
  # Method 2: Download wrapper JAR directly from GitHub
  elif command -v curl &>/dev/null; then
    echo "Downloading gradle-wrapper.jar from GitHub..."
    curl -L -o "$JAR" "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar" || {
      echo "Failed to download from GitHub, trying alternative source..."
      curl -L -o "$JAR" "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" || {
        echo "ERROR: Could not download gradle-wrapper.jar"
        echo "Please install Gradle manually or use Android Studio to open this project."
        exit 1
      }
    }
  else
    echo "ERROR: Neither gradle nor curl is available."
    echo "Please install Gradle or Android Studio to generate the wrapper."
    echo "Or open the project in Android Studio - it will auto-download everything."
    exit 1
  fi
fi

chmod +x gradlew
echo "Setup complete. Run: ./gradlew assembleDebug"
