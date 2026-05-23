#!/bin/bash
set -e
cd "X:/CODE/WORK_SPACE/SoftWarePrac/kai-fa-promax"

# Clean up
git stash 2>/dev/null || true
git checkout master 2>/dev/null || true

HASHES=($(git log --reverse --format="%H" master))
echo "Master commits: ${#HASHES[@]}"

# Build tags index
for ((i=1; i<=${#HASHES[@]}; i++)); do
    TAG=$(grep "^## $i\." "../COMMIT_HISTORY.md" | sed 's/^## [0-9]*\. \[\(.*\)\]/\1/')
    eval "TAG_$i='$TAG'"
done
echo "Tags indexed for ${#HASHES[@]} commits"

recreate_branch() {
    local BRANCH="$1"
    local COUNT=0

    echo ""
    echo "=== Rebuilding $BRANCH ==="
    git branch -D "$BRANCH" 2>/dev/null || true

    # Start at root commit
    ROOT="${HASHES[0]}"
    git checkout -b "$BRANCH" "$ROOT"
    COUNT=1
    echo "Root: ${ROOT:0:8}"

    for ((i=1; i<${#HASHES[@]}; i++)); do
        local H="${HASHES[$i]}"
        local NUM=$((i+1))

        eval "local TAG=\$TAG_$NUM"
        if ! echo "$TAG" | grep -qi "$BRANCH"; then
            continue
        fi

        # Get files changed in this commit (vs parent)
        FILES=$(git diff-tree --no-commit-id -r --name-only "$H" 2>/dev/null || git ls-tree --name-only "$H" 2>/dev/null)
        if [ -z "$FILES" ]; then
            echo "  SKIP #$NUM: no files detected"
            continue
        fi

        # Extract each file (overwrites existing)
        HAS=0
        while IFS= read -r f; do
            [ -z "$f" ] && continue
            mkdir -p "$(dirname "$f")" 2>/dev/null || true
            if git show "$H:$f" > "$f" 2>/dev/null; then
                HAS=1
            fi
        done <<< "$FILES"

        # Stage EVERYTHING (modified + new + deleted)
        git add -A 2>/dev/null || true

        if git diff --cached --quiet 2>/dev/null; then
            echo "  SKIP #$NUM: no net changes after extraction"
            continue
        fi

        # Reuse original commit metadata. Use -C to copy author/message/date
        if git commit -C "$H" 2>/dev/null; then
            COUNT=$((COUNT+1))
        else
            # Fallback: manual commit with original metadata
            MSG=$(git log --format="%s" -1 "$H")
            AUTHOR=$(git log --format="%an <%ae>" -1 "$H")
            DATE=$(git log --format="%aD" -1 "$H")
            git commit --author="$AUTHOR" --date="$DATE" -m "$MSG" 2>/dev/null || {
                echo "  FAIL #$NUM: cannot commit"
                git reset HEAD . 2>/dev/null || true
                git checkout -- . 2>/dev/null || true
            }
        fi

        if [ $((COUNT % 15)) -eq 0 ]; then
            echo "  ... $COUNT commits"
        fi
    done

    echo "  Done: $BRANCH with $COUNT commits"
}

recreate_branch backend
recreate_branch frontend

git checkout master

echo ""
echo "=== Verification ==="
for B in backend frontend; do
    CNT=$(git rev-list --count "refs/heads/$B" 2>/dev/null || echo 0)
    EXTRA=$(git log --oneline "refs/heads/$B" ^refs/heads/master 2>/dev/null | wc -l)
    echo "$B: ${CNT} commits (extras not in master: ${EXTRA})"
done
echo ""
echo "=== Done ==="
