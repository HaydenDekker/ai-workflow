import { useSignal } from "@vaadin/hilla-react-signals";
import { Button, TextField } from "@vaadin/react-components";

export interface HelloWorldProps {
  /** The greeting message to display */
  greeting?: string;
  /** Whether to show the name input field */
  showInput?: boolean;
}

export function HelloWorld({
  greeting = "Hello",
  showInput = true,
}: HelloWorldProps) {
  const name = useSignal("");

  return (
    <div className="flex flex-col gap-m p-m" style={{ maxWidth: 400 }}>
      <h2>{greeting}</h2>
      {showInput && (
        <div className="flex gap-s items-end">
          <TextField
            label="Your name"
            value={name.value}
            onValueChanged={(e) => {
              name.value = e.detail.value;
            }}
          />
          <Button onClick={() => alert(`${greeting}, ${name.value || "World"}!`)}>
            Say Hello
          </Button>
        </div>
      )}
      {!showInput && <p>{greeting}, World!</p>}
    </div>
  );
}
