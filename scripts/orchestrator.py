#!/usr/bin/env python3
"""
Phase-by-phase plan orchestrator using pi RPC sub-agents.

Usage:
    python scripts/orchestrator.py                           # run all phases
    python scripts/orchestrator.py --from 3                  # start at phase 3
    python scripts/orchestrator.py --from 3 --dry-run        # show plan, don't execute
    python scripts/orchestrator.py --from 3 --stall-minutes 5

Each phase spawns a pi sub-agent via RPC, monitors for stalls,
then compiles, tests, and commits before moving on.
"""

import subprocess
import json
import re
import sys
import time
import os
import argparse

# Force UTF-8 stdout so LLM unicode chars (->, bullets, etc.) don't crash on Windows
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
PLAN_PATH = "project/plans/scanner-status-observability-refactor.md"
PI_CLI = [
    "node",
    os.path.expanduser("~/.npm/_npx/pi.js"),  # fallback, resolved at runtime
]
STALL_MINUTES = 15          # kill sub-agent if no RPC event for this many minutes
TEST_GLOB = "Scanner*"      # only run scanner-related tests
BRANCH_NAME = "refactor/scanner-observability"
AUTO_ABORT = True           # don't prompt for input; abort on failure

# Cross-platform Maven wrapper
MVNW = "mvnw.cmd" if os.name == "nt" else "./mvnw"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def resolve_pi_cli():
    """Find the real pi cli entry-point (npm installs a .cmd wrapper on Windows)."""
    # Try common locations
    candidates = [
        os.path.expanduser("~/.npm/_npx/pi.js"),
        os.path.join(os.environ.get("APPDATA", ""), "npm", "node_modules",
                     "@earendil-works", "pi-coding-agent", "dist", "cli.js"),
        os.path.join(os.environ.get("APPDATA", ""), "npm", "node_modules",
                     "@earendil-works", "pi-coding-agent", "lib", "cli.js"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            return ["node", c]
    # Last resort: use `pi` via shell=True
    print("WARN: Could not resolve pi CLI path, falling back to shell 'pi'", file=sys.stderr)
    return None  # caller will use shell=True

def run(cmd, check=True, capture=True):
    """Run a shell command, return stdout."""
    r = subprocess.run(cmd, shell=True, capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"Command failed (rc={r.returncode}):\n{r.stderr}")
    return r.stdout.strip()

def parse_plan(path):
    """Extract phases from the markdown plan."""
    with open(path, encoding="utf-8") as f:
        text = f.read()

    # Split on phase headers: "### Phase N: ..."
    parts = re.split(r"^### Phase \d+: ", text, flags=re.MULTILINE)
    phases = []

    # Also extract the problem/target context (everything before first phase)
    context = ""
    for part in parts:
        m = re.match(r"^Phase 0:", part)
        if m or re.match(r"^\d+\.", part.strip()):
            continue
        # Grab everything before first phase header
        break

    # Re-join to find pre-phase context
    context_end = text.find("### Phase 0:")
    if context_end > 0:
        context = text[:context_end].strip()

    # Now parse each phase block
    phase_re = re.compile(
        r"### (Phase \d+): (.+?)\n(.*?)(?=### |## Implementation Summary|## Risks|## Notes|## Design Decisions|EOF)",
        re.DOTALL
    )
    for m in phase_re.finditer(text):
        title = f"{m.group(1)}: {m.group(2)}"
        body = m.group(3).strip()
        phases.append({"title": title, "body": body, "number": int(m.group(1).split()[-1])})

    return context, phases

def safe_text(s):
    """Strip non-ASCII for safe terminal output."""
    return s.encode('ascii', 'ignore').decode('ascii')

def build_prompt(context, phase):
    """Build the prompt for the sub-agent."""
    desc = phase['title'].split(":", 1)[-1].strip() if ":" in phase['title'] else phase['title']
    return (
        f"You are implementing {desc} of a larger scanner observability refactor.\n\n"
        f"## Overall Plan Context\n{context}\n\n"
        f"## Your Task: {phase['title']}\n{phase['body']}\n\n"
        f"## Instructions\n"
        f"1. Read the relevant existing files first to understand the current code.\n"
        f"2. Make the changes described in the checklist items above.\n"
        f"3. After coding, compile with: .\\mvnw compile -q\n"
        f"4. If there are test files mentioned, create/update them and run: .\\mvnw test -Dtest=ClassName -q\n"
        f"5. Run .\\mvnw compile -q one final time to confirm everything builds.\n"
        f"6. Summarize what you changed.\n\n"
        f"## Rules\n"
        f"- Follow the AGENTS.md conventions (already loaded as context).\n"
        f"- Use TDD: write tests first where applicable.\n"
        f"- Do NOT proceed to the next phase - only implement THIS phase.\n"
        f"- If a compile error occurs, fix it before moving on.\n"
        f"- If a test fails, fix the code or test before completing.\n"
        f"- Max 120 char line length, 4-space indent, no raw types.\n"
    )

# ---------------------------------------------------------------------------
# RPC sub-agent
# ---------------------------------------------------------------------------

def run_phase_rpc(phase, prompt, stall_minutes=STALL_MINUTES):
    """
    Spawn a pi sub-agent via RPC, stream events, detect stalls.
    Returns (success: bool, summary: str, tool_calls: int).
    """
    stall_seconds = stall_minutes * 60
    stall_threshold = time.time() + stall_seconds
    tool_calls = 0
    text_parts = []
    last_event_time = time.time()

    pi_cmd = resolve_pi_cli()
    if pi_cmd:
        cmd = pi_cmd + ["--mode", "rpc", "--no-session"]
        kwargs = {}
    else:
        cmd = "pi --mode rpc --no-session"
        kwargs = {"shell": True}

    print(f"  Spawning pi sub-agent (stall timeout: {stall_minutes}m)...")
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding='utf-8',
        errors='replace',  # pi may emit non-ASCII progress chars
        bufsize=1,
        **kwargs
    )

    try:
        # Send prompt
        proc.stdin.write(json.dumps({"type": "prompt", "message": prompt}) + "\n")
        proc.stdin.flush()

        while True:
            line = proc.stdout.readline()
            if not line:
                break  # process exited

            line = line.rstrip("\r\n")
            if not line:
                continue

            now = time.time()

            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue  # skip malformed lines (progress indicators, etc.)

            etype = event.get("type", "")

            # Stall detection: reset timer on any meaningful event
            if etype in ("message_update", "tool_execution_start", "tool_execution_end",
                         "tool_execution_update", "agent_end"):
                last_event_time = now
                if now > stall_threshold:
                    # Already past stall - check every 60s from now
                    stall_threshold = now + stall_seconds

            if etype == "message_update":
                delta = event.get("assistantMessageEvent", {})
                dtype = delta.get("type", "")
                if dtype == "text_delta":
                    chunk = delta.get("delta", "")
                    text_parts.append(chunk)
                    # Print live output
                    sys.stdout.write(chunk)
                    sys.stdout.flush()
                elif dtype in ("toolcall_start",):
                    pass  # logged below

            elif etype == "tool_execution_start":
                tname = event.get("toolName", "?")
                targs = event.get("args", {})
                tool_calls += 1
                if tname == "bash":
                    cmd_preview = str(targs.get("command", "?"))[:80]
                    print(f"\n  [tool {tool_calls}] {tname}: {cmd_preview}")
                elif tname in ("read", "edit", "write"):
                    path = str(targs.get("path", "?"))
                    print(f"\n  [tool {tool_calls}] {tname}: {path}")
                else:
                    print(f"\n  [tool {tool_calls}] {tname}")

            elif etype == "tool_execution_end":
                tname = event.get("toolName", "?")
                if tname == "bash":
                    result = event.get("result", {})
                    content = result.get("content", [{}])
                    if content:
                        text = str(content[0].get("text", ""))
                        # Print first few lines of output
                        lines = text.split("\n")
                        for l in lines[:5]:
                            print(f"    {l}")
                        if len(lines) > 5:
                            print(f"    ... ({len(lines)-5} more lines)")
                last_event_time = now

            elif etype == "agent_end":
                print(f"\n  Agent finished ({tool_calls} tool calls).")
                return True, "".join(text_parts), tool_calls

            else:
                # Other events (agent_start, turn_start, response, etc.)
                last_event_time = now

            # Stall check
            elapsed = now - last_event_time
            # We track stall from the *last meaningful event*, not wall clock
            # Reset stall_threshold whenever we see a meaningful event above
            if elapsed > stall_seconds * 2:  # generous: 2x stall_minutes
                print(f"\n  [!]️  STALL DETECTED - no meaningful events for {elapsed:.0f}s. Killing sub-agent.")
                proc.kill()
                proc.wait()
                return False, "".join(text_parts) + "\n\n[STALLED - sub-agent killed]", tool_calls

    except KeyboardInterrupt:
        print("\n  [!]️  Interrupted by user.")
        proc.kill()
        proc.wait()
        return False, "".join(text_parts) + "\n\n[INTERRUPTED]", tool_calls

    proc.kill()
    proc.wait()
    return False, "".join(text_parts) + "\n\n[PROCESS EXITED]", tool_calls

# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------

def compile_check():
    """Run compilation, return success."""
    print("\n  Compiling...")
    try:
        run(f"{MVNW} compile -q", check=True)
        print("  [OK] Compile OK")
        return True
    except RuntimeError as e:
        print(f"  [FAIL] Compile FAILED: {e}")
        return False

def run_tests():
    """Run scanner-related tests, return success."""
    print("\n  Running scanner tests...")
    cmd = f'{MVNW} test -Dtest="{TEST_GLOB}" -q'
    try:
        run(cmd, check=True)
        print("  [OK] Tests OK")
        return True
    except RuntimeError as e:
        print(f"  [FAIL] Tests FAILED: {e}")
        return False

def git_commit(phase_title):
    """Stage all changes and commit."""
    status = run("git status --short")
    if not status:
        print("\n  No changes to commit.")
        return True

    print(f"\n  Staging and committing '{phase_title}'...")
    run("git add -A")
    msg = f"feat: {phase_title.strip()}"
    run(f'git commit -m "{msg}"')
    print(f"  [OK] Committed: {msg}")
    return True

# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Phase-by-phase plan orchestrator")
    parser.add_argument("--from", dest="start_phase", type=int, default=0,
                        help="Start from this phase number (0-based)")
    parser.add_argument("--stall-minutes", type=int, default=STALL_MINUTES,
                        help="Kill sub-agent if idle for this many minutes")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show phases but don't execute")
    parser.add_argument("--skip-test", action="store_true",
                        help="Skip test execution after each phase")
    parser.add_argument("--skip-commit", action="store_true",
                        help="Skip git commit after each phase")
    args = parser.parse_args()

    if not os.path.isfile(PLAN_PATH):
        print(f"Plan not found: {PLAN_PATH}", file=sys.stderr)
        sys.exit(1)

    context, phases = parse_plan(PLAN_PATH)

    if not phases:
        print("No phases found in plan.", file=sys.stderr)
        sys.exit(1)

    print(f"{'='*70}")
    print(f"Plan: {os.path.basename(PLAN_PATH)}")
    print(f"Phases: {len(phases)}  |  Starting from: Phase {args.start_phase}")
    print(f"Stall timeout: {args.stall_minutes}m")
    print(f"{'='*70}\n")

    # List phases
    for p in phases:
        marker = ">" if p["number"] >= args.start_phase else "-"
        # Strip unicode from plan text for safe terminal output
        clean = p['title'].encode('ascii', 'ignore').decode('ascii').strip()
        # title already contains "Phase N:" so just show the description part
        desc = clean.split(":", 1)[-1].strip() if ":" in clean else clean
        print(f"  [{marker}] Phase {p['number']}: {desc}")
    print()

    if args.dry_run:
        print("Dry run - no execution.")
        return

    # Ensure clean working tree
    status = run("git status --short")
    if status:
        print("WARN: Working tree is not clean. Consider committing first.\n")
        print(status)
        print("\nContinuing anyway...")
        time.sleep(2)

    # Create branch if on main
    branch = run("git rev-parse --abbrev-ref HEAD")
    if branch == "main":
        print(f"\nCreating branch '{BRANCH_NAME}'...")
        run(f"git checkout -b {BRANCH_NAME}")
    print()

    # Run phases
    results = []
    for phase in phases:
        if phase["number"] < args.start_phase:
            continue

        print(f"{'='*70}")
        print(f"Phase {phase['number']}: {phase['title']}")
        print(f"{'='*70}\n")

        prompt = build_prompt(context, phase)
        success, summary, tool_calls = run_phase_rpc(
            phase, prompt, stall_minutes=args.stall_minutes
        )

        # Post-phase: compile
        if not compile_check():
            print(f"\n  [!] Phase {phase['number']} left compilation broken.")
            results.append((phase["number"], "FAIL-COMPILE", tool_calls))
            print("  Aborting. Fix manually then resume with --from.")
            break

        # Post-phase: tests
        if not args.skip_test:
            if not run_tests():
                print(f"\n  [!] Phase {phase['number']} has failing tests.")
                results.append((phase["number"], "FAIL-TEST", tool_calls))
                print("  Aborting. Fix manually then resume with --from.")
                break

        # Post-phase: commit
        if not args.skip_commit:
            git_commit(phase["title"])

        results.append((phase["number"], "OK", tool_calls))
        print(f"\n  [OK] Phase {phase['number']} COMPLETE\n")

    # Summary
    print(f"{'='*70}")
    print("SUMMARY")
    print(f"{'='*70}")
    for num, status, tc in results:
        icon = "[OK]" if status == "OK" else "[FAIL]"
        print(f"  {icon} Phase {num}: {status} ({tc} tool calls)")
    print(f"\nTotal: {len(results)} phases executed, "
          f"{sum(1 for _, s, _ in results if s == 'OK')} passed, "
          f"{sum(1 for _, s, _ in results if s != 'OK')} failed.")

if __name__ == "__main__":
    main()
