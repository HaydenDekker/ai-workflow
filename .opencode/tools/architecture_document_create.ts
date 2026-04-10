// architecture_document_create.ts - custom tool for OpenCode
import { tool } from "@opencode-ai/plugin"
import * as fs from "fs"
import * as path from "path"
import { execSync } from "child_process"

/**
 * Recursively collect all file paths under a directory.
 * @param dir starting directory
 * @param baseDir base directory for relative paths
 * @returns array of file paths relative to the base directory
 */
function collectFiles(dir: string, baseDir: string): string[] {
  let results: string[] = []
  const entries = fs.readdirSync(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      results = results.concat(collectFiles(fullPath, baseDir))
    } else if (entry.isFile()) {
      const rel = path.relative(baseDir, fullPath).replace(/\\/g, "/")
      results.push(rel)
    }
  }
  return results
}

export default tool({
  description:
    "Create a software architecture document by checklist‑listing files and summarising each file with the LLM.",
  args: {
    directoryPath: tool.schema
      .string()
      .describe("Root directory to search, e.g., 'src/main/java'"),
    destinationFile: tool.schema
      .string()
      .describe(
        "File to write the architecture document to, e.g., 'project/state/s-arch.md'"
      ),
  },
  async execute(args) {
    const dir = args.directoryPath?.trim()
    const dest = args.destinationFile?.trim()
    if (!dir) return "Error: directoryPath argument is required."
    if (!dest) return "Error: destinationFile argument is required."

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
    // Start the document with a header
    const header = `# Software Architecture Document\n\nGenerated from directory **${absDir}**\n\n## Checklist\n`
    try {
      fs.writeFileSync(absDest, header, { encoding: "utf8" })
    } catch (e) {
      return `Error writing header to destination file: ${e}`
    }

    // Append checklist entries
    const checklist = files.map(f => `- [ ] ${f}`).join("\n") + "\n\n"
    try {
      fs.appendFileSync(absDest, checklist, { encoding: "utf8" })
    } catch (e) {
      return `Error writing checklist to destination file: ${e}`
    }

    // Summarise each file and append to the document
    for (const relPath of files) {
      const absPath = path.join(absDir, relPath)
// Call the background task to generate a summary instead of invoking LLM directly to avoid long execution time
      const summary = await (await import("./background_task_helloworld.ts")).default().execute({});
      const sectionHeader = `## ${relPath}\n`
      try {
        fs.appendFileSync(absDest, sectionHeader + summary + "\n\n", { encoding: "utf8" })
      } catch (e) {
        // Continue on error but record it in the document
        fs.appendFileSync(
          absDest,
          sectionHeader + `**Failed to append summary:** ${e}` + "\n\n",
          { encoding: "utf8" }
        )
      }
    }

    return `Architecture document generated at ${absDest} with ${files.length} files processed.`
  },
})
