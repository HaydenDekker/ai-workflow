#!/usr/bin/env python3
"""
Phase-by-phase plan orchestrator using pi RPC sub-agents.

Spawns a pi sub-agent for each plan phase, monitors progress,
verifies compile/tests, and commits before advancing.

Usage:
    python orchestrator.py <plan-path>                         # run all phases
    python orchestrator.py <plan-path> --from 3                # start at phase 3
    python orchestrator.py <plan-path> --dry-run               # show phases, don't execute
    python orchestrator.py <plan-path> --stall-minutes 10      # custom stall timeout
    python orchestrator.py <plan-path> --skip-test             # skip test step
    python orchestrator.py <plan-path> --skip-commit           # skip auto-commit
    python orchestrator.py <plan-path> --test-glob "*Service*" # custom test pattern
    python orchestrator.py <plan-path> --provider llama-workhorse # custom LLM provider
    python orchestrator.py <plan-path> --model qwen3-35b        # custom LLM model

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
# Config (override via CLI or env vars)
# ---------------------------------------------------------------------------

STALL_MINUTES = int(os.environ.get("ORCH_STRALL_MINUTES", "15"))
BRANCH_PREFIX = os.environ.get("ORCH_BRANCH_PREFIX", "refactor")
AUTO_ABORT = True

# Cross-platform Maven wrapper
MVNW = "mvnw.cmd" if os.name == "nt" else "./mvnw"

# Default build/test commands (override with --build-cmd and --test-cmd)
DEFAULT_BUILD_CMD = f"{MVNW} compile -q"
DEFAULT_TEST_CMD = f"{MVNW} test -Dtest=\"*\" -q"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def resolve_pi_cli():
    """Find the real pi cli entry-point (npm installs a .cmd wrapper on Windows)."""
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
    print("WARN: Could not resolve pi CLI path, falling back to shell 'pi'", file=sys.stderr)
    return None

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

    # Extract pre-phase context (everything before first phase header)
    context_end = text.find("### Phase 0:")
    if context_end > 0:
        context = text[:context_end].strip()
    else:
        context = text.strip()

    # Parse phase blocks
    phase_re = re.compile(
        r"### (Phase \d+): (.+?)\n(.*?)(?=### |## Implementation Summary|## Risks|## Notes|## Design Decisions|EOF)",
        re.DOTALL
    )
    phases = []
    for m in phase_re.finditer(text):
        title = f"{m.group(1)}: {m.group(2)}"
        body = m.group(3).strip()
        phases.append({"title": title, "body": body, "number": int(m.group(1).split()[-1])})

    return context, phases

def build_prompt(context, phase):
    """Build the prompt for the sub-agent."""
    desc = phase['title'].split(":", 1)[-1].strip() if ":" in phase['title'] else phase['title']
    return (
        f"You are implementing {desc} of a larger refactoring effort.\n\n"
        f"## Overall Plan Context\n{context}\n\n"
        f"## Your Task: {phase['title']}\n{phase['body']}\n\n"
        f"## Instructions\n"
        f"1. Read the relevant existing files first to understand the current code.\n"
        f"2. Make the changes described in the checklist items above.\n"
        f"3. After coding, compile with: {MVNW} compile -q\n"
        f"4. Run the relevant tests first: {MVNW} test -Dtest=ClassName#methodName -q\n"
        f"5. Then run the FULL test suite to verify nothing else broke: {MVNW} test -q\n"
        f"6. Run {MVNW} compile -q one final time to confirm everything builds.\n"
        f"7. Provide a concise summary of what you changed, then STOP. Do not attempt to do any other work.\n\n"
        f"## Rules\n"
        f"- Follow the project conventions (AGENTS.md is already loaded as context).\n"
        f"- Use TDD: write tests first where applicable.\n"
        f"- Do NOT proceed to the next phase - only implement THIS phase.\n"
        f"- Do NOT continue working after you have provided your summary.\n"
        f"- If a compile error occurs, fix it before moving on.\n"
        f"- If a test fails, fix the code or test before completing.\n"
        f"- Max 120 char line length, 4-space indent, no raw types.\n"
    )

# ---------------------------------------------------------------------------
# RPC sub-agent
# ---------------------------------------------------------------------------

def run_phase_rpc(phase, prompt, provider=None, model=None, stall_minutes=STALL_MINUTES):
    """
    Spawn a pi sub-agent via RPC, stream events, detect stalls.
    Returns (success: bool, summary: str, tool_calls: int).
    """
    stall_seconds = stall_minutes * 60
    stall_threshold = time.time() + stall_seconds
    tool_calls = 0
    text_parts = []
    last_llm_activity = time.time()

    pi_cmd = resolve_pi_cli()
    if pi_cmd:
        cmd = pi_cmd + ["--mode", "rpc"]
        if provider:
            cmd += ["--provider", provider]
        if model:
            cmd += ["--model", model]
        kwargs = {}
    else:
        cmd = "pi --mode rpc"
        if provider:
            cmd += f" --provider {provider}"
        if model:
            cmd += f" --model {model}"
        kwargs = {"shell": True}

    print(f"  Spawning pi sub-agent (stall timeout: {stall_minutes}m)...")
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding='utf-8',
        errors='replace',
        bufsize=1,
        **kwargs
    )

    try:
        proc.stdin.write(json.dumps({"type": "prompt", "message": prompt}) + "\n")
        proc.stdin.flush()

        while True:
            line = proc.stdout.readline()
            if not line:
                break

            line = line.rstrip("\r\n")
            if not line:
                continue

            now = time.time()

            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue

            etype = event.get("type", "")

            # Stall detection: only reset on LLM activity (not tool output).
            # Long-running Maven tests produce tool_execution_update events that
            # would mask a real LLM stall.
            if etype == "message_update":
                delta = event.get("assistantMessageEvent", {})
                dtype = delta.get("type", "")
                if dtype in ("text_delta", "thinking_delta", "toolcall_delta"):
                    last_llm_activity = now

            if etype == "message_update":
                delta = event.get("assistantMessageEvent", {})
                dtype = delta.get("type", "")
                if dtype == "text_delta":
                    chunk = delta.get("delta", "")
                    text_parts.append(chunk)
                    sys.stdout.write(chunk)
                    sys.stdout.flush()

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
                        lines = text.split("\n")
                        for l in lines[:5]:
                            print(f"    {l}")
                        if len(lines) > 5:
                            print(f"    ... ({len(lines)-5} more lines)")

            elif etype == "agent_end":
                print(f"\n  Agent finished ({tool_calls} tool calls).")
                return True, "".join(text_parts), tool_calls

            # Stall check
            elapsed = now - last_llm_activity
            if elapsed > stall_seconds * 2:
                print(f"\n  [!] STALL DETECTED - no LLM activity for {elapsed:.0f}s. Killing sub-agent.")
                proc.kill()
                proc.wait()
                return False, "".join(text_parts) + "\n\n[STALLED]", tool_calls

    except KeyboardInterrupt:
        print("\n  [!] Interrupted by user.")
        proc.kill()
        proc.wait()
        return False, "".join(text_parts) + "\n\n[INTERRUPTED]", tool_calls

    proc.kill()
    proc.wait()
    return False, "".join(text_parts) + "\n\n[PROCESS EXITED]", tool_calls

# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------

def compile_check(build_cmd):
    """Run compilation, return success."""
    print("\n  Compiling...")
    try:
        run(build_cmd, check=True)
        print("  [OK] Compile OK")
        return True
    except RuntimeError as e:
        print(f"  [FAIL] Compile FAILED: {e}")
        return False

def run_tests(test_cmd):
    """Run tests, return success."""
    print("\n  Running tests...")
    try:
        run(test_cmd, check=True)
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

    print(f"\n  Staging and committing...")
    run("git add -A")
    msg = f"feat: {phase_title.strip()}"
    run(f'git commit -m "{msg}"')
    print(f"  [OK] Committed: {msg}")
    return True

# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Phase-by-phase plan orchestrator using pi RPC sub-agents",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python orchestrator.py project/plans/my-plan.md
  python orchestrator.py project/plans/my-plan.md --from 2 --stall-minutes 10
  python orchestrator.py project/plans/my-plan.md --dry-run
  python orchestrator.py project/plans/my-plan.md --build-cmd "./gradlew build"
  python orchestrator.py project/plans/my-plan.md --skip-test --skip-commit

Environment variables:
  ORCH_STALL_MINUTES    Stall timeout in minutes (default: 15)
  ORCH_BRANCH_PREFIX    Branch name prefix (default: refactor)
        """
    )
    parser.add_argument("plan_path", help="Path to the plan markdown file")
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
    parser.add_argument("--build-cmd", default=DEFAULT_BUILD_CMD,
                        help=f"Build command (default: {DEFAULT_BUILD_CMD})")
    parser.add_argument("--test-cmd", default=DEFAULT_TEST_CMD,
                        help=f"Test command (default: {DEFAULT_TEST_CMD})")
    parser.add_argument("--test-glob",
                        help="Test class pattern (alternative to --test-cmd)")
    parser.add_argument("--provider",
                        help="LLM provider for sub-agents (e.g. ollama, llama-workhorse)")
    parser.add_argument("--model",
                        help="LLM model for sub-agents (e.g. qwen3-35b)")
    args = parser.parse_args()

    # Override test command if --test-glob provided
    if args.test_glob:
        args.test_cmd = f'{MVNW} test -Dtest="{args.test_glob}" -q'

    if not os.path.isfile(args.plan_path):
        print(f"Plan not found: {args.plan_path}", file=sys.stderr)
        sys.exit(1)

    context, phases = parse_plan(args.plan_path)

    if not phases:
        print("No phases found in plan.", file=sys.stderr)
        sys.exit(1)

    branch_name = f"{BRANCH_PREFIX}/{os.path.splitext(os.path.basename(args.plan_path))[0]}"

    print(f"{'='*70}")
    print(f"Plan: {os.path.basename(args.plan_path)}")
    print(f"Phases: {len(phases)}  |  Starting from: Phase {args.start_phase}")
    print(f"Stall timeout: {args.stall_minutes}m")
    print(f"Build command: {args.build_cmd}")
    print(f"Test command: {args.test_cmd}")
    print(f"Provider: {args.provider or 'default'}")
    print(f"Model: {args.model or 'default'}")
    print(f"{'='*70}\n")

    # List phases
    for p in phases:
        marker = ">" if p["number"] >= args.start_phase else "-"
        clean = p['title'].encode('ascii', 'ignore').decode('ascii').strip()
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
        print(f"\nCreating branch '{branch_name}'...")
        run(f"git checkout -b {branch_name}")
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
            phase, prompt, provider=args.provider, model=args.model, stall_minutes=args.stall_minutes
        )

        # Post-phase: compile
        if not compile_check(args.build_cmd):
            print(f"\n  [!] Phase {phase['number']} left compilation broken.")
            results.append((phase["number"], "FAIL-COMPILE", tool_calls))
            print("  Aborting. Fix manually then resume with --from.")
            break

        # Post-phase: tests
        if not args.skip_test:
            if not run_tests(args.test_cmd):
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
