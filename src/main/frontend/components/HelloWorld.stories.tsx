import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";
import { HelloWorld } from "./HelloWorld";

const meta = {
  title: "Components/HelloWorld",
  component: HelloWorld,
  tags: ["autodocs"],
  argTypes: {
    greeting: {
      control: "text",
      description: "The greeting message to display",
    },
    showInput: {
      control: "boolean",
      description: "Whether to show the name input field",
    },
  },
  parameters: {
    docs: {
      description: {
        component: "A simple greeting component with optional name input.",
      },
    },
  },
} satisfies Meta<typeof HelloWorld>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: {
    greeting: "Hello",
    showInput: true,
  },
};

export const CustomGreeting: Story = {
  args: {
    greeting: "Welcome",
    showInput: true,
  },
};

export const NoInput: Story = {
  args: {
    greeting: "Hello",
    showInput: false,
  },
};
