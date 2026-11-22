#!/bin/zsh
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 wird benötigt. Beispiel: brew install openjdk@21"
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle fehlt. Mit Homebrew: brew install gradle"
  exit 1
fi
echo "Baue OneKeyDTap-Quellprojekt ..."
gradle build
result=$?
if [ "$result" -eq 0 ]; then
  echo "Fertig. Ausgabe in build/libs/ (ohne -sources oder -dev)."
else
  echo "Build fehlgeschlagen. Die Fehlermeldung bitte zurücksenden."
fi
exit "$result"
