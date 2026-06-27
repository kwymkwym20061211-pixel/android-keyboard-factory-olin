#!/bin/bash
# android向けビルドスクリプト。debug/release/cancelを選択し、オプションでインストールも行う。
# これ別のプロジェクトからパクってきたので、ASan同期など不要なものは削ってあります。

while true; do
    read -p "debug?release?cancel?(d/r/c) " choice
    case "$choice" in
        d|r ) break ;;
        c ) echo "Cancelled."; exit 0 ;;
        * ) echo "Invalid input. Please enter d, r, or c." ;;
    esac
done

if [ "$choice" = "d" ]; then
    echo "Building Debug APK..."
    GRADLE_TASK=":app:assembleDebug"
    GRADLE_OPTS=""
    BUILD_TYPE="debug"
else
    echo "Building Release APK..."
    GRADLE_TASK=":app:assembleRelease"
    GRADLE_OPTS=""
    BUILD_TYPE="release"
fi

if ! ./gradlew "$GRADLE_TASK" $GRADLE_OPTS; then
    echo "Error: Build failed."
    exit 1
fi
echo "Build successful!"

echo -n "Install to device?(y/n) "
read install
if [ "$install" = "y" ]; then
    # Scoped to :app only -- the root (unscoped) installDebug/installRelease task installs
    # *every* application module, including :keyboard-template (an internal-only IME template
    # that's bundled into :app's assets, never meant to be installed standalone).
    INSTALL_TASK=":app:install${BUILD_TYPE^}"
    echo "Installing..."
    if ./gradlew "$INSTALL_TASK"; then
        echo "Install successful!"
    else
        echo "Error: Install failed."
        exit 1
    fi
fi