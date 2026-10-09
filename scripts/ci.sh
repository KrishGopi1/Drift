#!/usr/bin/env bash
# Merge-queue check for Drift. Runs, in order, stopping at the first failure with a clear summary:
#   1. checks        ./gradlew driftChecks            (dependency rules, no INTERNET, no placeholders)
#   2. unit-tests    unit tests of every module       (JVM modules: test, Android modules: testDebugUnitTest)
#   3. assemble      ./gradlew :app:assembleDebug
#   4. screenshots   verifyRoborazziDebug for every module that applies Roborazzi
#   5. lint          ./gradlew lintDebug
#
# Usage:
#   scripts/ci.sh                    run all stages
#   scripts/ci.sh --list             print the Gradle command of each stage and exit
#   scripts/ci.sh --only checks      run only the named stage(s); repeat --only to run several
# Environment:
#   GRADLE_ARGS                      extra arguments for every Gradle call (for example "--offline")
#   DRIFT_REQUIRE_SCREENSHOTS=1      fail the screenshots stage when no module has screenshot tests yet
#
# Memory is shared between agents: stages run as separate Gradle invocations on one daemon, one after another.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/env.sh
source "$ROOT/scripts/env.sh"

ALL_STAGES=(checks unit-tests assemble screenshots lint)
GRADLE_EXTRA=${GRADLE_ARGS:-}

# Gradle path (":core:engine") of every module directory that contains build.gradle.kts.
module_paths() {
  find app core feature source testing -mindepth 0 -maxdepth 2 -name build.gradle.kts -not -path '*/build/*' 2>/dev/null \
    | sed -e 's|/build.gradle.kts$||' | sort | while read -r dir; do printf ':%s\n' "${dir//\//:}"; done
}

module_dir() { local p="${1#:}"; printf '%s\n' "${p//://}"; }

# Modules by type, decided from the convention plugin they apply.
jvm_modules() {
  module_paths | while read -r m; do
    grep -qE 'drift\.jvm\.library' "$(module_dir "$m")/build.gradle.kts" 2>/dev/null && printf '%s\n' "$m"
  done
}

screenshot_modules() {
  module_paths | while read -r m; do
    grep -qiE 'roborazzi' "$(module_dir "$m")/build.gradle.kts" 2>/dev/null && printf '%s\n' "$m"
  done
}

# Prints the Gradle task list for a stage, one task per line.
stage_tasks() {
  case "$1" in
    checks) echo "driftChecks" ;;
    unit-tests)
      jvm_modules | while read -r m; do echo "$m:test"; done
      echo "testDebugUnitTest"   # every Android module that has the task
      ;;
    assemble) echo ":app:assembleDebug" ;;
    screenshots) screenshot_modules | while read -r m; do echo "$m:verifyRoborazziDebug"; done ;;
    lint) echo "lintDebug" ;;
    *) return 1 ;;
  esac
}

print_list() {
  for s in "${ALL_STAGES[@]}"; do
    tasks=$(stage_tasks "$s" | tr '\n' ' ')
    printf '%-12s ./gradlew %s%s\n' "$s" "${tasks:-<nothing to run yet>}" "${GRADLE_EXTRA:+ $GRADLE_EXTRA}"
  done
}

# ---- argument parsing
SELECTED=()
while [ $# -gt 0 ]; do
  case "$1" in
    --list) print_list; exit 0 ;;
    --only)
      shift
      [ $# -gt 0 ] || { echo "ci: --only needs a stage name (${ALL_STAGES[*]})" >&2; exit 2; }
      case " ${ALL_STAGES[*]} " in *" $1 "*) SELECTED+=("$1") ;; *) echo "ci: unknown stage '$1' (${ALL_STAGES[*]})" >&2; exit 2 ;; esac
      ;;
    -h|--help) sed -n '2,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "ci: unknown argument '$1' (try --help)" >&2; exit 2 ;;
  esac
  shift
done
[ ${#SELECTED[@]} -gt 0 ] || SELECTED=("${ALL_STAGES[@]}")

# ---- run
declare -A STATUS DURATION
FAILED_STAGE=""
START_ALL=$(date +%s)

summary() {
  local total=$(( $(date +%s) - START_ALL ))
  echo
  echo "================ CI SUMMARY ================"
  for s in "${SELECTED[@]}"; do
    printf '  %-12s %-8s %ss\n' "$s" "${STATUS[$s]:-NOT RUN}" "${DURATION[$s]:-0}"
  done
  echo "  total        ${total}s"
  if [ -n "$FAILED_STAGE" ]; then
    echo "RESULT: FAILED at stage '$FAILED_STAGE' (later stages were not run)"
    echo "Reproduce: scripts/ci.sh --only $FAILED_STAGE"
  else
    echo "RESULT: PASSED"
  fi
  echo "============================================"
}
trap summary EXIT

for stage in "${SELECTED[@]}"; do
  mapfile -t tasks < <(stage_tasks "$stage")
  echo
  echo "---------- stage: $stage ----------"
  if [ ${#tasks[@]} -eq 0 ]; then
    if [ "$stage" = "screenshots" ] && [ "${DRIFT_REQUIRE_SCREENSHOTS:-0}" = "1" ]; then
      echo "ci: no module applies Roborazzi but DRIFT_REQUIRE_SCREENSHOTS=1"
      STATUS[$stage]="FAILED"; DURATION[$stage]=0; FAILED_STAGE="$stage"; exit 1
    fi
    echo "ci: no module has screenshot tests yet; skipping (set DRIFT_REQUIRE_SCREENSHOTS=1 to make this a failure)"
    STATUS[$stage]="SKIPPED"; DURATION[$stage]=0
    continue
  fi
  echo "+ ./gradlew ${tasks[*]} ${GRADLE_EXTRA}"
  t0=$(date +%s)
  # shellcheck disable=SC2086
  if ./gradlew "${tasks[@]}" $GRADLE_EXTRA; then
    STATUS[$stage]="PASSED"
  else
    STATUS[$stage]="FAILED"; FAILED_STAGE="$stage"
  fi
  DURATION[$stage]=$(( $(date +%s) - t0 ))
  if [ -n "$FAILED_STAGE" ]; then exit 1; fi
done
exit 0
