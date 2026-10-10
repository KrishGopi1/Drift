# Source before any Gradle command:  source scripts/env.sh
# Uses the per-user toolchain when it is installed; otherwise keeps JAVA_HOME / ANDROID_HOME from the
# environment (CI runners and contributors' own machines).
if [ -d "$HOME/.jdks/jdk-21.0.12.1+1" ]; then
  export JAVA_HOME="$HOME/.jdks/jdk-21.0.12.1+1"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_HOME/platform-tools:$PATH"
