/**
 * Global teardown: stops the Spring Boot dev server after all tests complete.
 */
export default async function globalTeardown() {
  const serverProcess = (globalThis as Record<string, unknown>)
    .SERVER_PROCESS as import('child_process').ChildProcess | undefined;

  if (serverProcess) {
    console.log('Stopping Spring Boot dev server...');
    serverProcess.kill('SIGTERM');

    // Wait for graceful shutdown
    await new Promise<void>((resolve) => {
      serverProcess.on('exit', () => resolve());
      setTimeout(() => {
        serverProcess.kill('SIGKILL');
        resolve();
      }, 10000);
    });

    console.log('Spring Boot dev server stopped');
  }
}
