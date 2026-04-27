import type { Meta, StoryObj } from "@storybook/react";
import { expect, userEvent, within } from "storybook/test";
import { Counter } from "./Counter";

const meta = {
  title: "Components/Counter",
  component: Counter,
  tags: ["autodocs"],
  argTypes: {
    label: {
      control: "text",
      description: "The display label for the counter",
    },
    initialCount: {
      control: "number",
      description: "The initial count value",
    },
  },
  parameters: {
    docs: {
      description: {
        component:
          "A simple counter component backed by a mocked Hilla endpoint. " +
          "In production it calls CounterService.increment/decrement/reset on the server.",
      },
    },
  },
} satisfies Meta<typeof Counter>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
  args: { label: "Counter", initialCount: 0 },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const display = canvas.getByText("0");
    expect(display).toBeInTheDocument();

    const incrementButton = canvas.getByText("+");
    await userEvent.click(incrementButton);
    expect(canvas.getByText("1")).toBeInTheDocument();
  },
};

export const StartingAtFive: Story = {
  args: { label: "My Counter", initialCount: 5 },
};

export const NegativeStart: Story = {
  args: { label: "Negative", initialCount: -3 },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const decrementButton = canvas.getByText("−");
    await userEvent.click(decrementButton);
    expect(canvas.getByText("-4")).toBeInTheDocument();
  },
};

export const ResetDemo: Story = {
  args: { label: "Reset Me", initialCount: 10 },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const resetButton = canvas.getByText("Reset");
    await userEvent.click(resetButton);
    expect(canvas.getByText("0")).toBeInTheDocument();
  },
};
