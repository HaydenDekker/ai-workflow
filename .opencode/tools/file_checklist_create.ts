// file_checklist_create.ts - custom tool for OpenCode
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"

/**
 * Recursively collect all file paths under a directory.
 * @param dir starting directory
 * @returns array of file paths relative to the starting directory
 */
function collectFiles(dir: string, baseDir: string): string[] {
  let results: string[] = []
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      results = results.concat(collectFiles(fullPath, baseDir))
    } else if (entry.isFile()) {
      // Store path relative to the base directory for cleaner output
      const rel = path.relative(baseDir, fullPath).replace(/\\/g, "/")
      results.push(rel)
    }
  }
  return results
}

export default tool({
  description: "Create a markdown checklist of all files in a directory and append it to a destination file.",
  args: {
    directoryPath: tool.schema.string().describe("Root directory to search, e.g., 'src/main/java'"),
    destinationFile: tool.schema.string().describe("File to append the checklist to, e.g., 'project/state/file-checklist.md'")
  },
  async execute(args) {
    const dir = args.directoryPath?.trim()
    const dest = args.destinationFile?.trim()
    if (!dir) {
      return "Error: directoryPath argument is required."
    }
    if (!dest) {
      return "Error: destinationFile argument is required."
    }
    // Resolve paths relative to the workspace root
    const workspaceRoot = process.cwd()
    const absDir = path.isAbsolute(dir) ? dir : path.join(workspaceRoot, dir)
    const absDest = path.isAbsolute(dest) ? dest : path.join(workspaceRoot, dest)

    if (!fs.existsSync(absDir) || !fs.statSync(absDir).isDirectory()) {
      return `Error: Directory '${absDir}' does not exist or is not a directory.`
    }

    // Ensure destination directory exists
    const destDir = path.dirname(absDest)
    if (!fs.existsSync(destDir)) {
      fs.mkdirSync(destDir, { recursive: true })
    }

    const files = collectFiles(absDir, absDir)
    const checklistLines = files.map(f => `- [ ] ${f}`).join("\n") + "\n"
    try {
      fs.appendFileSync(absDest, checklistLines, { encoding: "utf8" })
      return `Checklist of ${files.length} files appended to ${absDest}`
    } catch (e) {
      return `Error writing to destination file: ${e}`
    }
  }
})
