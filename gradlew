#!/bin/sh
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit
exec "$JAVA_HOME/bin/java" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "-Dorg.gradle.appname=gradlew" -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"