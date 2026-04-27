import type { Preview } from '@storybook/react';
import { vi } from 'vitest';

// Import Lumo theme so Vaadin web components are styled in Storybook
import '@vaadin/vaadin-lumo-styles/lumo.css';

vi.mock('Frontend/generated/endpoints', () => {
  return {
    CounterService: {
      increment: (current: number) => ({
        block: () => Promise.resolve({ currentCount: current + 1 }),
      }),
      decrement: (current: number) => ({
        block: () => Promise.resolve({ currentCount: current - 1 }),
      }),
      reset: () => ({
        block: () => Promise.resolve({ currentCount: 0 }),
      }),
    },
    AgentInfoService: {
      getAllAgentInfos: () => ({ block: () => Promise.resolve([]) }),
      refreshAgent: () => ({ block: () => Promise.resolve({}) }),
    },
    ScannerService: {
      getAllScannerInfos: () => ({ block: () => Promise.resolve([]) }),
      refreshScanner: () => ({ block: () => Promise.resolve({}) }),
    },
  };
});

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    docs: {
      toc: true,
    },
  },
};

export default preview;
