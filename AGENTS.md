# Sleep Project — Agent Instructions

Shared instructions for every AI agent working in this repo: Antigravity's agent and Claude Code (CLI + extension) both read this file.

## Environment
- Code lives in WSL (Ubuntu 24.04) at `/home/krish/Sleep Project`. Antigravity is connected over Remote-WSL.
- Run all commands in the WSL terminal, not in Windows. Node v24 (nvm) is available.

## Working agreements
- Keep changes small and focused; one feature or fix per change.
- Run the project's tests/lint before saying a task is done (once they exist).
- Don't commit secrets. Put them in `.env` (gitignored) and keep a `.env.example` up to date.
- When several agents are working, each one should stick to the files its task covers, so their edits don't collide.

## Project
- Stack: TBD. Fill in here once it's chosen (framework, how to run, how to test).
