import { useSignal } from "@vaadin/hilla-react-signals";
import { Button } from "@vaadin/react-components";
import { CounterService } from "Frontend/generated/endpoints";

export interface CounterProps {
  /** The display label for the counter */
  label?: string;
  /** The initial count value */
  initialCount?: number;
}

/**
 * A simple counter component that uses a mocked backend endpoint.
 *
 * In production this would call `CounterService.increment()` and `CounterService.decrement()`
 * to persist state on the server. For Storybook we mock those endpoints via `vi.mock()`.
 */
export function Counter({
  label = "Counter",
  initialCount = 0,
}: CounterProps) {
  const count = useSignal(initialCount);

  const handleIncrement = async () => {
    try {
      const result = await CounterService.increment(count.value).block();
      count.value = result.currentCount;
    } catch (error) {
      console.error("Failed to increment counter:", error);
      count.value = count.value + 1; // fallback for offline/demo
    }
  };

  const handleDecrement = async () => {
    try {
      const result = await CounterService.decrement(count.value).block();
      count.value = result.currentCount;
    } catch (error) {
      console.error("Failed to decrement counter:", error);
      count.value = count.value - 1; // fallback for offline/demo
    }
  };

  const handleReset = async () => {
    try {
      const result = await CounterService.reset().block();
      count.value = result.currentCount;
    } catch (error) {
      console.error("Failed to reset counter:", error);
      count.value = 0; // fallback for offline/demo
    }
  };

  return (
    <div className="flex flex-col gap-m p-m items-center" style={{ maxWidth: 300 }}>
      <h2 className="text-lg font-semibold">{label}</h2>
      <div className="text-4xl font-mono font-bold py-4">{count.value}</div>
      <div className="flex gap-s">
        <Button theme="primary" onClick={handleDecrement}>−</Button>
        <Button theme="secondary" onClick={handleReset}>Reset</Button>
        <Button theme="primary" onClick={handleIncrement}>+</Button>
      </div>
    </div>
  );
}
