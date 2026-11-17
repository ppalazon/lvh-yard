---
title: Windows PowerShell 5.1 drops an empty-string argument to a native command
author: Pablo
date: 2026-11-17
branch: main
id: l-75393d67
scope: repo
status: active
src: agent
---

## Watch out
- In Windows PowerShell 5.1, lvh-yard.exe ws tls ca create --ca-key-password '' reaches the CLI as --ca-key-password with no value: 'flag needs an argument'. PowerShell 5.1 removes an empty-string argument when it calls a native program.
- Write the flag with an equals sign, as one argument: '--ca-key-password='. An empty value means no password.
- Linux and macOS shells pass the empty argument as written, so a Linux test does not show this.
