import type { StorybookConfig } from '@storybook/react-vite';

const config: StorybookConfig = {
  stories: [
    "../src/main/frontend/**/*.mdx",
    "../src/main/frontend/**/*.stories.@(js|jsx|mjs|ts|tsx)"
  ],
  addons: [
    '@storybook/addon-docs'
  ],
  framework: {
    name: '@storybook/react-vite',
    options: {}
  },
  staticDirs: ['../src/main/frontend/themes']
};
export default config;
