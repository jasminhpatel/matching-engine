#!/usr/bin/env bash
# Build the Maven artifacts solfini-match-engine depends on that exist in no
# repository Maven can reach, and install them into the local Maven repository.
#
# Eleven of this project's compile dependencies cannot be resolved on a clean
# machine, which is why `mvn package` fails on a fresh clone:
#
#   com.solfini.messaging:solfini-{infrastructure,sbe,admin}-messaging
#       Private org repositories; never published anywhere.
#   org.knowm.xchange:xchange-{core,binance,bybit,mexc,okex,kraken,bitget}:5.1.1-cc
#       Built from the org's XChange fork. The "-cc" version qualifier exists
#       only in that fork, so Maven Central can never satisfy these.
#   com.github.mmazi:rescu:3.1-SNAPSHOT
#       On the compile classpath directly (XExchange imports si.mazi.rescu) and
#       the XChange fork compiles against it. It was only ever served by
#       oss.sonatype.org, which was sunset in 2025; Maven Central stops at 3.0
#       and upstream master is already 3.2-SNAPSHOT. The pinned commit below is
#       the last one whose pom still declares 3.1-SNAPSHOT.
#
# This is solfini-api-server's script with the dependency manifest adjusted for
# this project: it also needs xchange-kraken, which solfini-api-server does not.
#
# Re-running this is cheap: it records a fingerprint of the resolved dependency
# commits in the local Maven repository and returns immediately when nothing has
# moved. CI caches ~/.m2/repository, so the builds happen only when a dependency
# actually changes.
#
# Usage:
#   ./scripts/install-dependencies.sh                     # install what is missing or stale
#   ./scripts/install-dependencies.sh --force             # rebuild unconditionally
#   ./scripts/install-dependencies.sh --print-fingerprint # resolve refs, print cache key, build nothing
#
# Environment variables:
#   DEPS_READ_TOKEN   GitHub token with read access to the four solfini-org
#                     repositories. When set, clones use HTTPS with this token;
#                     otherwise they use SSH, which is what you want locally.
#   SOLFINI_DEPS_DIR  Where the dependency checkouts live
#                     (default: ~/.cache/solfini-deps).
#   MAVEN_REPO_LOCAL  Install into this Maven repository instead of the default
#                     ~/.m2/repository. Useful for testing in isolation.
#   JDK17_HOME        JDK 17, used for the XChange fork and rescu.
#   JDK21_HOME        JDK 21, used for the messaging artifacts.
#                     Either variable may be omitted when the JDK on PATH
#                     already is that major version.
#   SOLFINI_DEPS_REF_<NAME>
#                     Override the git ref for one dependency, e.g.
#                     SOLFINI_DEPS_REF_XCHANGE=my-branch.
#
# Prerequisites:
#   - git, mvn
#   - JDK 21 (this project targets 21) and JDK 17. Two JDKs are needed because
#     the XChange fork pins Lombok 1.18.26, which predates JDK 21 and fails on
#     it with "NoSuchFieldError: JCTree$JCImport.qualid".
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEPS_DIR="${SOLFINI_DEPS_DIR:-${HOME}/.cache/solfini-deps}"
MAVEN_REPO="${MAVEN_REPO_LOCAL:-${HOME}/.m2/repository}"
STAMP_FILE="${MAVEN_REPO}/.solfini-match-engine-deps.stamp"

log() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

# repo | git ref | JDK major | extra Maven arguments
#
# Order is load-bearing: the XChange fork compiles against rescu, so rescu has
# to be installed first.
#
# Only the seven XChange modules this project depends on are built. The fork has
# 121 modules and building all of them would add minutes for no benefit.
dependency_manifest() {
  cat <<'MANIFEST'
solfini-org/solfini-infrastructure-messaging|main|21|
solfini-org/solfini-sbe-messaging|main|21|
solfini-org/solfini-admin-messaging|main|21|
mmazi/rescu|3e19b7dd6436a37c27823a1025facc63f1c9d668|17|-Dgpg.skip=true -Dmaven.javadoc.skip=true
solfini-org/XChange|main|17|-am -pl xchange-core,xchange-binance,xchange-bybit,xchange-mexc,xchange-okex,xchange-kraken,xchange-bitget
MANIFEST
}

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum | cut -d' ' -f1
  else
    shasum -a 256 | cut -d' ' -f1 # macOS has no sha256sum
  fi
}

repo_name() { printf '%s\n' "${1##*/}"; }

# SOLFINI_DEPS_REF_XCHANGE, SOLFINI_DEPS_REF_RESCU, ...
ref_override() {
  local name upper var
  name="$(repo_name "$1")"
  upper="$(printf '%s' "$name" | tr '[:lower:]-' '[:upper:]_')"
  var="SOLFINI_DEPS_REF_${upper}"
  printf '%s\n' "${!var:-}"
}

clone_url() {
  if [[ -n "${DEPS_READ_TOKEN:-}" ]]; then
    printf 'https://x-access-token:%s@github.com/%s.git\n' "${DEPS_READ_TOKEN}" "$1"
  else
    printf 'git@github.com:%s.git\n' "$1"
  fi
}

# git, for commands that talk to github.com, with any Authorization header
# inherited from a surrounding repository cleared.
#
# actions/checkout persists its credentials as http.https://github.com/.extraheader
# in the workspace's .git/config, and that header takes precedence over
# credentials embedded in a clone URL. Since this script runs with the workspace
# as its working directory, every clone would otherwise authenticate as the
# workspace's repo-scoped GITHUB_TOKEN instead of DEPS_READ_TOKEN and fail with
# "Repository not found" on the other repositories. An empty value resets the
# header list, which restores the URL credentials.
git_remote() { git -c 'http.https://github.com/.extraheader=' "$@"; }

# Resolve every ref to a concrete commit. A 40-character hex ref is already one,
# which keeps the pinned rescu commit from costing a network round trip.
resolve_manifest() {
  local repo ref jdk extra override sha
  while IFS='|' read -r repo ref jdk extra; do
    [[ -n "$repo" ]] || continue
    override="$(ref_override "$repo")"
    [[ -n "$override" ]] && ref="$override"
    if [[ "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      sha="$ref"
    else
      sha="$(git_remote ls-remote "$(clone_url "$repo")" "$ref" | cut -f1)"
      [[ -n "$sha" ]] || die "Could not resolve ref '${ref}' in ${repo}. Is DEPS_READ_TOKEN set and valid?"
    fi
    printf '%s|%s|%s|%s\n' "$repo" "$sha" "$jdk" "$extra"
  done < <(dependency_manifest)
}

# Hashing the resolved manifest rather than just the commits means a change to a
# JDK or to the Maven arguments also invalidates the cache.
fingerprint_of() { sha256 <"$1"; }

jdk_major_of() {
  "$1/bin/java" -version 2>&1 | head -1 |
    sed -nE 's/.*version "([0-9]+).*/\1/p'
}

jdk_home_for() {
  local major="$1" var="JDK${1}_HOME" configured found
  configured="${!var:-}"
  if [[ -n "$configured" ]]; then
    [[ -x "${configured}/bin/java" ]] || die "${var} is set to '${configured}', which has no bin/java"
    printf '%s\n' "$configured"
    return
  fi
  if [[ -n "${JAVA_HOME:-}" && "$(jdk_major_of "$JAVA_HOME")" == "$major" ]]; then
    printf '%s\n' "$JAVA_HOME"
    return
  fi
  if [[ -x /usr/libexec/java_home ]] && found="$(/usr/libexec/java_home -v "$major" 2>/dev/null)"; then
    printf '%s\n' "$found" # macOS
    return
  fi
  die "No JDK ${major} found. Set ${var} to a JDK ${major} installation. The XChange fork needs 17 (its Lombok predates 21) and this project needs 21."
}

checkout_at() {
  local repo="$1" sha="$2" dir="$3" url
  url="$(clone_url "$repo")"
  if [[ -d "${dir}/.git" ]]; then
    git -C "$dir" remote set-url origin "$url"
    git_remote -C "$dir" rev-parse --verify --quiet "${sha}^{commit}" >/dev/null ||
      git_remote -C "$dir" fetch --quiet origin
  else
    rm -rf "$dir"
    git_remote clone --quiet "$url" "$dir"
  fi
  # Detached on purpose: these checkouts are build inputs, not working copies.
  git -C "$dir" checkout --quiet --detach "$sha"
  git -C "$dir" clean -qxfd
}

build_at() {
  local dir="$1" java_home="$2" extra="$3"
  local -a repo_arg=()
  [[ -n "${MAVEN_REPO_LOCAL:-}" ]] && repo_arg=("-Dmaven.repo.local=${MAVEN_REPO_LOCAL}")
  # Run from inside the checkout rather than with `mvn -f`: solfini-sbe-messaging
  # generates its codecs with exec-maven-plugin, and SbeTool resolves
  # "src/main/resources/sbe/business-schema.xml" against the process working
  # directory rather than the Maven basedir, so `-f` fails with NoSuchFileException.
  (
    cd "$dir"
    # shellcheck disable=SC2086 # extra holds several arguments and must word-split
    JAVA_HOME="$java_home" mvn -B -ntp -DskipTests "${repo_arg[@]}" $extra install
  )
}

main() {
  local force=0 print_fingerprint=0
  case "${1:-}" in
    --force) force=1 ;;
    --print-fingerprint) print_fingerprint=1 ;;
    -h | --help)
      sed -n '2,53p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      return 0
      ;;
    '') ;;
    *) die "Unknown argument: $1 (try --help)" ;;
  esac

  need_cmd git
  need_cmd mvn

  local resolved fingerprint
  resolved="$(mktemp)"
  # shellcheck disable=SC2064 # expand the path now, while it is known
  trap "rm -f '${resolved}'" EXIT
  resolve_manifest >"$resolved"
  fingerprint="$(fingerprint_of "$resolved")"

  if ((print_fingerprint)); then
    printf '%s\n' "$fingerprint"
    return 0
  fi

  if ((!force)) && [[ -f "$STAMP_FILE" ]] && [[ "$(cat "$STAMP_FILE")" == "$fingerprint" ]]; then
    log "Dependencies are up to date (${fingerprint}); nothing to build."
    log "Re-run with --force to rebuild anyway."
    return 0
  fi

  mkdir -p "$DEPS_DIR"
  local repo sha jdk extra name dir java_home
  while IFS='|' read -r repo sha jdk extra; do
    name="$(repo_name "$repo")"
    dir="${DEPS_DIR}/${name}"
    java_home="$(jdk_home_for "$jdk")"
    log ""
    log "==> ${repo} @ ${sha} (JDK ${jdk})"
    checkout_at "$repo" "$sha" "$dir"
    build_at "$dir" "$java_home" "$extra"
  done <"$resolved"

  # Written last so an interrupted run does not look complete. It lives in the
  # Maven repository because that is what it describes, and because CI caches
  # that directory and needs the stamp restored with it.
  mkdir -p "$MAVEN_REPO"
  printf '%s\n' "$fingerprint" >"$STAMP_FILE"

  log ""
  log "Installed all dependencies into ${MAVEN_REPO} (${fingerprint})."
  log "You can now run: cd ${ROOT_DIR} && mvn -Dskip-tests=false test"
}

main "$@"
