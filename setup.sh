#!/bin/bash
# Download gradle wrapper jar if missing
JAR=gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$JAR" ]; then
  echo "Downloading gradle-wrapper.jar..."
  curl -sL "https://services.gradle.org/distributions/gradle-8.4-bin.zip" -o /tmp/gradle.zip
  unzip -j /tmp/gradle.zip "gradle-8.4/lib/plugins/gradle-wrapper-*.jar" -d /tmp/gwjar/ 2>/dev/null || true
  # Fallback: use gradle if installed
  if command -v gradle &>/dev/null; then
    gradle wrapper --gradle-version 8.4
  else
    echo "Please install Gradle or Android Studio to generate the wrapper."
    echo "Or open the project in Android Studio - it will auto-download everything."
  fi
fi
chmod +x gradlew
echo "Setup complete. Run: ./gradlew assembleDebug"
