#!/bin/sh
set -eu
GRADLE_VERSION="8.7"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin"
GRADLE_DIR="$DIST_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
    mkdir -p "$DIST_DIR"
    if [ ! -f "$ZIP_FILE" ]; then
        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 3 --retry-delay 2 "$URL" -o "$ZIP_FILE"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$ZIP_FILE" "$URL"
        else
            echo "curl or wget is required to download Gradle." >&2
            exit 1
        fi
    fi
    rm -rf "$DIST_DIR/.extracting"
    mkdir -p "$DIST_DIR/.extracting"
    unzip -q "$ZIP_FILE" -d "$DIST_DIR/.extracting"
    rm -rf "$GRADLE_DIR"
    mv "$DIST_DIR/.extracting/gradle-$GRADLE_VERSION" "$GRADLE_DIR"
    rm -rf "$DIST_DIR/.extracting"
fi
exec "$GRADLE_DIR/bin/gradle" "$@"
