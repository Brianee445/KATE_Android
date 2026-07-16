#!/bin/sh

# ============================================================================
# Kate Assistant - Gradle Wrapper
# ============================================================================

# Source this script to avoid problems with CDPATH
CDPATH=""
cd "$(dirname "$0")" || exit

# Set APP_HOME
APP_HOME="$(pwd)"

# ============================================================================
# KATE ENHANCEMENTS - MEMORY & ENCODING
# ============================================================================
# Default JVM options - Increased for large builds (Vosk + TFLite)
DEFAULT_JVM_OPTS='"-Dfile.encoding=UTF-8" "-Xmx4096m" "-Xms512m" "-XX:MaxMetaspaceSize=512m" "-XX:+HeapDumpOnOutOfMemoryError"'

# Enable Gradle daemon and cache for faster builds
export GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.daemon=true -Dorg.gradle.caching=true -Dorg.gradle.parallel=true"

# ============================================================================

# Use the maximum available file descriptors
if [ -n "$(ulimit -n)" ] && [ "$(ulimit -n)" -lt 4096 ]; then
    ulimit -n 4096 2>/dev/null || true
fi

# Find Java
if [ -n "$JAVA_HOME" ] ; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA="java"
fi

# Print build info
echo ""
echo "========================================"
echo "  Kate Assistant - Gradle Build"
echo "========================================"
echo ""
echo "Java: $JAVA"
echo "Memory: 4GB Heap"
echo "Encoding: UTF-8"
echo "========================================"
echo ""

# Execute Gradle
exec "$JAVA" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
