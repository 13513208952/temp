#!/bin/sh
# Offline reproduction: compile the 36 new sources + 9 context copies, then run 36 JUnit classes.
# Requires JDK 17+ and the four jars/dirs below; no network, Android device or Gradle is used.
set -e
: "${ANDROID_JAR:?set ANDROID_JAR to android-37.2/android.jar}"
: "${GECKO_JAR:?set GECKO_JAR to GeckoView 158 classes.jar}"
: "${JUNIT:?set JUNIT to junit-4.13.2.jar}"
: "${HAMCREST:?set HAMCREST to hamcrest-core-1.3.jar}"
cd "$(dirname "$0")"
rm -rf build/classes build/test-classes
mkdir -p build/classes build/test-classes
find app/src/main/java context/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 17 -proc:none -cp "$ANDROID_JAR:$GECKO_JAR" -d build/classes @build/sources.txt
find app/src/test/java -name '*.java' > build/tests.txt
sed 's|app/src/test/java/||; s|/|.|g; s|\.java$||' build/tests.txt > build/classes.txt
javac -encoding UTF-8 --release 17 -proc:none \
  -cp "$ANDROID_JAR:$GECKO_JAR:$JUNIT:$HAMCREST:build/classes" -d build/test-classes @build/tests.txt
java -cp "build/classes:build/test-classes:$JUNIT:$HAMCREST:$ANDROID_JAR:$GECKO_JAR" \
  org.junit.runner.JUnitCore $(cat build/classes.txt)
