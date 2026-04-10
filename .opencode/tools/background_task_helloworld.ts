// background_task_helloworld.ts - custom tool for OpenCode
import { tool } from "@opencode-ai/plugin"

export default tool({
  description: "Start a background task that logs 'world' to the console every second for 20 seconds",
  args: {}, // no arguments required
  async execute(_args) {
    // Start interval to log 'world' every second
    const interval = setInterval(() => {
      console.log("world")
    }, 1000)

    // Stop the interval after 20 seconds
    setTimeout(() => {
      clearInterval(interval)
    }, 20000)

    // Return immediately to indicate the task has started
    return "Background task started: logging 'world' every second for 20 seconds."
  }
})
