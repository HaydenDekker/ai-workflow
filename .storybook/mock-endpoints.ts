/**
 * Mock module for Hilla endpoints used by the Counter component.
 * Provides a simple in-memory counter implementation for Storybook.
 */

export const CounterService = {
  increment: (current: number) => {
    console.log('[Mock] increment called with:', current);
    return {
      block: () => Promise.resolve({ currentCount: current + 1 }),
    };
  },
  decrement: (current: number) => {
    console.log('[Mock] decrement called with:', current);
    return {
      block: () => Promise.resolve({ currentCount: current - 1 }),
    };
  },
  reset: () => {
    console.log('[Mock] reset called');
    return {
      block: () => Promise.resolve({ currentCount: 0 }),
    };
  },
};

export const AgentInfoService = {
  getAllAgentInfos: () => ({ block: () => Promise.resolve([]) }),
  refreshAgent: () => ({ block: () => Promise.resolve({}) }),
};

export const ScannerService = {
  getAllScannerInfos: () => ({ block: () => Promise.resolve([]) }),
  refreshScanner: () => ({ block: () => Promise.resolve({}) }),
};
