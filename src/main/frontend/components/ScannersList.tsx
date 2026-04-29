import { useState } from "react";
import { Button, Grid, GridColumn, Icon } from "@vaadin/react-components";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ScannerInfo {
  id: string;
  agentId: string;
  targetDirectory: string;
  status: "IDLE" | "EMITTING_ALL" | "EMITTING_UPDATES" | "ERROR";
  createdAt: string; // ISO datetime
  lastEmittedAt: string | null;
}

export interface ScannersListProps {
  /** When true, renders a loading placeholder */
  loading?: boolean;
  /** When provided, uses this array instead of the built-in dummy data */
  scanners?: ScannerInfo[];
  /** Optional title shown above the grid */
  title?: string;
  /** Called when a delete button is clicked */
  onDelete?: (agentId: string, scannerId: string) => void;
}

// ---------------------------------------------------------------------------
// Dummy data – matches the Java ScannerInfo record shape
// ---------------------------------------------------------------------------

export const DUMMY_SCANNERS: ScannerInfo[] = [
  {
    id: "scanner-001",
    agentId: "agent-alpha",
    targetDirectory: "/data/projects/ai-workflow/src",
    status: "EMITTING_UPDATES",
    createdAt: "2026-04-20T09:15:00",
    lastEmittedAt: "2026-04-29T14:32:11",
  },
  {
    id: "scanner-002",
    agentId: "agent-alpha",
    targetDirectory: "/data/projects/ai-workflow/docs",
    status: "IDLE",
    createdAt: "2026-04-20T09:15:05",
    lastEmittedAt: "2026-04-28T11:00:00",
  },
  {
    id: "scanner-003",
    agentId: "agent-beta",
    targetDirectory: "/data/projects/ai-workflow/tests",
    status: "EMITTING_ALL",
    createdAt: "2026-04-22T16:40:00",
    lastEmittedAt: "2026-04-29T14:30:45",
  },
  {
    id: "scanner-004",
    agentId: "agent-beta",
    targetDirectory: "/data/projects/ai-workflow/config",
    status: "IDLE",
    createdAt: "2026-04-23T08:00:00",
    lastEmittedAt: null,
  },
  {
    id: "scanner-005",
    agentId: "agent-gamma",
    targetDirectory: "/data/projects/ai-workflow/scripts",
    status: "ERROR",
    createdAt: "2026-04-25T12:10:00",
    lastEmittedAt: "2026-04-29T10:05:22",
  },
  {
    id: "scanner-006",
    agentId: "agent-gamma",
    targetDirectory: "/data/projects/ai-workflow/lib",
    status: "EMITTING_UPDATES",
    createdAt: "2026-04-26T07:30:00",
    lastEmittedAt: "2026-04-29T14:28:03",
  },
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const STATUS_COLORS: Record<ScannerInfo["status"], string> = {
  EMITTING_ALL: "#f5a623",
  EMITTING_UPDATES: "#4a90d9",
  ERROR: "#e74c3c",
  IDLE: "#27ae60",
};

function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString("en-GB", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

// ---------------------------------------------------------------------------
// Column renderers
// ---------------------------------------------------------------------------

function StatusRenderer({ item }: { item: ScannerInfo }) {
  return (
    <div className="flex items-center gap-s">
      <span
        className="inline-block rounded-full"
        style={{
          width: 10,
          height: 10,
          backgroundColor: STATUS_COLORS[item.status] ?? "#999",
        }}
      />
      <span>{item.status}</span>
    </div>
  );
}

function ActionsRenderer({
  item,
  onDelete,
}: {
  item: ScannerInfo;
  onDelete?: (agentId: string, scannerId: string) => void;
}) {
  return (
    <Button
      theme="tertiary"
      title="Delete scanner"
      onClick={() => onDelete?.(item.agentId, item.id)}
      aria-label={`Delete scanner ${item.id}`}
    >
      <Icon icon="line-awesome/svg/trash-solid.svg" />
    </Button>
  );
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * A self-contained scanner list component with dummy data.
 *
 * Designed for rapid prototyping and testing of scanner-related UI features
 * without requiring a running backend.  Swap `DUMMY_SCANNERS` with real data
 * from a Hilla endpoint when ready.
 */
export function ScannersList({
  loading = false,
  onDelete,
}: ScannersListProps) {
  const [scanners] = useState<ScannerInfo[]>(DUMMY_SCANNERS);

  if (loading) {
    return (
      <div className="flex items-center justify-center p-xl w-full">
        <span className="text-body text-m">Loading scanners…</span>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-m p-l w-full">
      <h2 className="text-xl font-semibold">File Scanners</h2>

      <Grid items={scanners} className="w-full" size={600} theme="no-border column-borders">
        <GridColumn path="agentId" header="Agent" autoWidth />
        <GridColumn path="targetDirectory" header="Target Directory" flexGrow={2} />
        <GridColumn header="Status" autoWidth renderer={StatusRenderer} />
        <GridColumn header="Created" autoWidth renderer={({ item }) => <span>{formatDateTime(item.createdAt)}</span>} />
        <GridColumn header="Last Emitted" autoWidth renderer={({ item }) => <span>{formatDateTime(item.lastEmittedAt)}</span>} />
        <GridColumn header="Actions" autoWidth renderer={(props) => <ActionsRenderer {...props} onDelete={onDelete} />} />
      </Grid>

      {scanners.length === 0 && (
        <div className="text-center text-body text-m py-xl">
          No scanners found
        </div>
      )}
    </div>
  );
}
