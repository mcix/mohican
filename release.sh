#!/bin/bash
#
# Release script for Mohican
# Usage: ./release.sh <version>
# Example: ./release.sh 1.11
#
# Prerequisites:
# - Set GITHUB_TOKEN environment variable with a GitHub personal access token
#   that has 'repo' permissions
#
# This script will:
# 1. Set the version in pom.xml (removes -SNAPSHOT)
# 2. Build the project
# 3. Create a git tag
# 4. Upload JAR and source ZIP to GitHub Releases
# 5. Prepare next SNAPSHOT version
#

set -e

# Check for version argument
if [ -z "$1" ]; then
    echo "Usage: $0 <version>"
    echo "Example: $0 1.11"
    exit 1
fi

VERSION=$1
NEXT_VERSION=$(echo "$VERSION" | awk -F. '{print $1"."$2+1}')

# Check for GitHub token
if [ -z "$GITHUB_TOKEN" ]; then
    echo "Error: GITHUB_TOKEN environment variable is not set"
    echo "Please set it with: export GITHUB_TOKEN=your_token"
    exit 1
fi

echo "=== Mohican Release Script ==="
echo "Release version: $VERSION"
echo "Next dev version: ${NEXT_VERSION}-SNAPSHOT"
echo ""

# Confirm
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
fi

# Set release version
echo "Setting version to $VERSION..."
mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false

# Build
echo "Building..."
mvn clean package -DskipTests

# Commit and tag
echo "Creating git commit and tag..."
git add pom.xml
git commit -m "Release version $VERSION"
git tag -a "v$VERSION" -m "Release $VERSION"

# Push tag
echo "Pushing tag..."
git push origin "v$VERSION"

# Upload to GitHub Releases
echo "Uploading to GitHub Releases..."
mvn -Prelease deploy -DskipTests

# Set next development version
echo "Setting next development version to ${NEXT_VERSION}-SNAPSHOT..."
mvn versions:set -DnewVersion="${NEXT_VERSION}-SNAPSHOT" -DgenerateBackupPoms=false
git add pom.xml
git commit -m "Prepare next development version ${NEXT_VERSION}-SNAPSHOT"
git push origin main

echo ""
echo "=== Release $VERSION completed! ==="
echo "GitHub Release: https://github.com/avanderheijde/mohican/releases/tag/v$VERSION"
