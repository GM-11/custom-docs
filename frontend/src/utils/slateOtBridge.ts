import type { Editor, Operation as SlateOperation } from "slate";

// ─── OT types (mirror of engine.go) ─────────────────────────────────────────

export const OT_INSERT = 0;
export const OT_DELETE = 1;

export type OTOperation = {
  type: typeof OT_INSERT | typeof OT_DELETE;
  position: number;
  version: number;
  clientId: string;
  data: string | number; // string for insert, number for delete length
};

// ─── Wire message types (mirror of Go ChannelData / WritePump JSON) ───────────
//
// Go marshals struct field names as-is (no json tags on these structs), so
// keys are PascalCase exactly as defined in the Go source.

export type WireDocumentState = {
  Version: number;
  Content: string;
  Operations: OTOperation[];
  Id: string;
};

export type WireOperationPayload = {
  Operation: OTOperation;
};

// The two payload shapes share the ChannelData envelope.
// Discriminate by checking for "Content" in Payload (initial state)
// vs "Operation" in Payload (subsequent ops).
export type WireChannelData =
  | { Payload: WireDocumentState; ClientId: string; Version: number }
  | { Payload: WireOperationPayload; ClientId: string; Version: number };

export function isDocumentStatePayload(
  payload: WireDocumentState | WireOperationPayload,
): payload is WireDocumentState {
  return "Content" in payload;
}

// ─── Position helpers ────────────────────────────────────────────────────────
//
// MVP assumption: single paragraph — the Slate tree is always:
//   [{ children: [{ text: "..." }] }]
// path is always [0, 0] and flat position === offset directly.
// Generalise to multi-paragraph by summing text lengths of preceding nodes.

export function flatPositionToSlate(
  position: number,
  editor: Editor,
): { path: [number, number]; offset: number } {
  let remaining = position;

  for (let blockIdx = 0; blockIdx < editor.children.length; blockIdx++) {
    const block = editor.children[blockIdx] as {
      children: { text: string }[];
    };

    for (let inlineIdx = 0; inlineIdx < block.children.length; inlineIdx++) {
      const leafText = block.children[inlineIdx].text;

      if (remaining <= leafText.length) {
        return { path: [blockIdx, inlineIdx], offset: remaining };
      }

      // +1 accounts for the implicit newline between paragraphs
      remaining -= leafText.length + 1;
    }
  }

  // Position is past the end of the document — clamp to the last leaf
  const lastBlockIdx = editor.children.length - 1;
  const lastBlock = editor.children[lastBlockIdx] as {
    children: { text: string }[];
  };
  const lastInlineIdx = lastBlock.children.length - 1;
  const lastText = lastBlock.children[lastInlineIdx].text;

  return {
    path: [lastBlockIdx, lastInlineIdx],
    offset: lastText.length,
  };
}

export function slatePathToFlatPosition(
  path: [number, number],
  offset: number,
  editor: Editor,
): number {
  let flat = 0;

  for (let blockIdx = 0; blockIdx < path[0]; blockIdx++) {
    const block = editor.children[blockIdx] as {
      children: { text: string }[];
    };
    for (const leaf of block.children) {
      flat += leaf.text.length;
    }
    flat += 1; // implicit newline between paragraphs
  }

  const block = editor.children[path[0]] as {
    children: { text: string }[];
  };
  for (let inlineIdx = 0; inlineIdx < path[1]; inlineIdx++) {
    flat += block.children[inlineIdx].text.length;
  }

  flat += offset;
  return flat;
}

// ─── Slate → OT ──────────────────────────────────────────────────────────────

export function slateOpToOT(
  op: SlateOperation,
  editor: Editor,
  version: number,
  clientId: string,
): OTOperation | null {
  if (op.type === "insert_text") {
    const position = slatePathToFlatPosition(
      op.path as [number, number],
      op.offset,
      editor,
    );
    return {
      type: OT_INSERT,
      position,
      version,
      clientId,
      data: op.text,
    };
  }

  if (op.type === "remove_text") {
    const position = slatePathToFlatPosition(
      op.path as [number, number],
      op.offset,
      editor,
    );
    return {
      type: OT_DELETE,
      position,
      version,
      clientId,
      data: op.text.length,
    };
  }

  // Ignore all non-text operations so formatting / alignment changes are kept
  // local in Slate and never sent through OT. We only ever send insert/delete.
  return null;
}

// ─── OT → Slate ──────────────────────────────────────────────────────────────

export function otOpToSlateOp(
  op: OTOperation,
  editor: Editor,
): SlateOperation {
  if (op.type === OT_INSERT) {
    const { path, offset } = flatPositionToSlate(op.position, editor);
    return {
      type: "insert_text",
      path,
      offset,
      text: op.data as string,
    };
  }

  // OT_DELETE
  const { path, offset } = flatPositionToSlate(op.position, editor);
  const length = op.data as number;

  // A single OT delete always targets one leaf in the MVP single-paragraph
  // model. Slate's remove_text needs the actual text being removed, not just
  // the length, so we read it from the current editor state.
  const block = editor.children[path[0]] as {
    children: { text: string }[];
  };
  const leafText = block.children[path[1]].text;
  const textToRemove = leafText.slice(offset, offset + length);

  return {
    type: "remove_text",
    path,
    offset,
    text: textToRemove,
  };
}
