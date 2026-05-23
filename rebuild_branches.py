#!/usr/bin/env python3
"""
Rebuild backend/frontend branches as pure subsets of master.
Strategy: for each master commit tagged for a branch, checkout its
branch-relevant files and commit with original metadata.
"""
import re, subprocess, os, sys

os.chdir(r"X:\CODE\WORK_SPACE\SoftWarePrac\kai-fa-promax")
repo = os.getcwd()

with open(r"X:\CODE\WORK_SPACE\SoftWarePrac\COMMIT_HISTORY.md", encoding="utf-8") as f:
    content = f.read()

entries = re.findall(
    r'^## (\d+)\. \[(.*?)\]\n\n作者: (.*?)\n日期: (.*?)\n消息: (.*?)\n文件: (.*?)$',
    content, re.MULTILINE
)

# Get master hashes in order (oldest first)
r = subprocess.run(["git", "log", "--reverse", "--format=%H", "master"],
                   capture_output=True, text=True, check=True)
master_hashes = r.stdout.strip().split()
n = min(len(master_hashes), len(entries), 63)
master_hashes = master_hashes[:n]
entries = entries[:n]

def run(cmd, check=True, **kw):
    print(f"  $ {cmd}")
    r2 = subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)
    if check and r2.returncode != 0:
        print(f"  FAILED (rc={r2.returncode}): {r2.stderr[:300]}")
        sys.exit(1)
    return r2

# Build index: num -> info
def build_info(entries, hashes):
    info = {}
    for i, entry in enumerate(entries):
        num_s, tags, author, date, msg, files_str = entry
        num = int(num_s)
        files_list = [f.strip() for f in files_str.split(",")]
        info[num] = {
            "tags": tags, "author": author, "date": date,
            "msg": msg, "files": files_list, "hash": hashes[i]
        }
    return info

info = build_info(entries, master_hashes)

# files matching a branch prefix
def branch_files(info_item, prefix):
    return [f for f in info_item["files"] if f.startswith(prefix)]

def recreate_branch(name, prefix, commit_numbers, info):
    """Rebuild branch by checking out branch-relevant files from master commits."""
    run("git checkout master", check=False)
    run(f"git branch -D {name} 2>/dev/null || true")

    # Start at root commit
    root_num = commit_numbers[0]
    root_hash = info[root_num]["hash"]
    run(f"git checkout -b {name} {root_hash}")
    print(f"  Started {name} at commit #{root_num}")

    count = 1
    for num in commit_numbers[1:]:
        h = info[num]["hash"]
        files_for_branch = branch_files(info[num], prefix)
        author = info[num]["author"]
        date_raw = info[num]["date"]
        msg = info[num]["msg"]

        if not files_for_branch:
            continue  # skip

        author_m = re.match(r'(.+?) <(.+?)>', author)
        author_name = author_m.group(1)
        author_email = author_m.group(2)
        fmt_date = date_raw.replace(" ", "T") + ":00+08:00"

        # checkout only the files for this branch from master commit
        run(f"git checkout {h} -- {' '.join(files_for_branch)}")

        # Verify something changed
        r2 = run("git diff --cached --quiet 2>/dev/null; echo $?", check=False)
        clean = r2.stdout.strip()
        if clean == "0":
            print(f"  SKIP #{num}: no changes for {name}")
            continue

        escaped = msg.replace('"', '\\"').replace('$', '\\$').replace('`', '\\`')
        cmd = (
            f'GIT_COMMITTER_NAME="{author_name}" GIT_COMMITTER_EMAIL="{author_email}" '
            f'GIT_COMMITTER_DATE="{fmt_date}" '
            f'git commit --author="{author_name} <{author_email}>" '
            f'--date="{fmt_date}" -m "{escaped}"'
        )
        run(cmd)
        count += 1
        if count % 10 == 0:
            print(f"  ... {count}/{len(commit_numbers)} for {name}")

    print(f"  {name}: {count} commits done")

# Determine which commits for each branch
backend_nums = sorted([int(e[0]) for e in entries if "backend" in e[1]])
frontend_nums = sorted([int(e[0]) for e in entries if "frontend" in e[1]])

print(f"backend commits: {len(backend_nums)}")
print(f"frontend commits: {len(frontend_nums)}")

print("\n=== Rebuilding backend ===")
recreate_branch("backend", "backend/", backend_nums, info)

print("\n=== Rebuilding frontend ===")
recreate_branch("frontend", "frontend/", frontend_nums, info)

# Verify
run("git checkout master")
print("\n=== Verification ===")
for b in ["backend", "frontend"]:
    r2 = run(f"git rev-list --count refs/heads/{b}", check=False)
    print(f"{b}: {r2.stdout.strip()} commits")
    r2 = run(f"git log --oneline refs/heads/{b} ^refs/heads/master | wc -l", check=False)
    extra = r2.stdout.strip()
    print(f"  extras (not in master): {extra}")
    r2 = run(f"git log --oneline refs/heads/master ^refs/heads/{b} | head -20", check=False)
    print(f"  master-only ahead (up to 20):")
    for line in r2.stdout.strip().split("\n")[:10]:
        print(f"    {line}")

print("\n=== Done ===")
