#!/usr/bin/env node
import { spawnSync } from "node:child_process";

const args = process.argv.slice(2);
const gradleArgs = [":cli:run", "--args", args.join(" ")];

const result = spawnSync("./gradlew", gradleArgs, { stdio: "inherit", shell: true });
process.exit(result.status ?? 1);
