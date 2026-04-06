import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { createEditor, Transforms } from "slate";
import { Slate, Editable, withReact } from "slate-react";
import { withHistory } from "slate-history";
import {
  slateOpToOT,
  otOpToSlateOp,
  isDocumentStatePayload,
  type OTOperation,
  type WireChannelData,
  OT_INSERT,
} from "../utils/slateOtBridge";
import { useAuth } from "../hooks/authHook";
import type { CustomElement } from "../types/slate";

// Import the type augmentation so Slate's generics are properly typed
import "../types/slate";
import docManagerApi from "../api/docManagerApi";

type Status = "connecting" | "connected" | "disconnected";

const EMPTY_DOC: CustomElement[] = [
  { type: "paragraph", children: [{ text: "" }] },
];

function contentToSlateValue(content: string): CustomElement[] {
  return [{ type: "paragraph", children: [{ text: content }] }];
}

function statusLabel(status: Status): string {
  switch (status) {
    case "connected":
      return "connected";
    case "connecting":
      return "connecting";
    case "disconnected":
      return "disconnected";
  }
}

export default function Editor() {
  const navigate = useNavigate();
  const { id: docId } = useParams<{ id: string }>();
  const { user, accessToken } = useAuth();

  const editor = useMemo(() => withHistory(withReact(createEditor())), []);

  const [isGrantOpen, setIsGrantOpen] = useState(false);
  const [grantEmail, setGrantEmail] = useState("");
  const [isGranting, setIsGranting] = useState(false);
  const [grantError, setGrantError] = useState<string | null>(null);
  const [grantSuccess, setGrantSuccess] = useState<string | null>(null);

  // Refs — don't need to trigger re-renders
  const wsRef = useRef<WebSocket | null>(null);
  const isRemote = useRef(false);
  const versionRef = useRef(0);

  // If a change originated from applying a remote WS message, ignore the next
  // Slate onChange handler invocation(s) to prevent echo loops.
  const suppressNextLocalSendRef = useRef(0);

  // StrictMode-safe connection de-dupe: only the latest connection is allowed
  // to mutate state; stale sockets/events are ignored.
  const connectionSeqRef = useRef(0);

  const [status, setStatus] = useState<Status>("connecting");

  // ─── WebSocket lifecycle ────────────────────────────────────────────────

  useEffect(() => {
    if (!docId || !accessToken) return;

    const seq = ++connectionSeqRef.current;

    if (wsRef.current) {
      try {
        wsRef.current.close();
      } catch {
        // ignore
      } finally {
        wsRef.current = null;
      }
    }

    setStatus("connecting");

    const wsBase = import.meta.env.VITE_WS_BASE_URL ?? "ws://localhost:8080";

    const ws = new WebSocket(
      `${wsBase.replace(/\/$/, "")}/ws?token=${accessToken}&hubId=${docId}`,
    );
    wsRef.current = ws;

    ws.onopen = () => {
      if (connectionSeqRef.current !== seq) return;
      setStatus("connected");
    };

    ws.onmessage = (event: MessageEvent<string>) => {
      if (connectionSeqRef.current !== seq) return;

      let msg: WireChannelData;
      try {
        msg = JSON.parse(event.data) as WireChannelData;
      } catch {
        console.error("Failed to parse WebSocket message:", event.data);
        return;
      }

      // Keep our local version cursor in sync with the server's envelope version.
      versionRef.current = msg.Version;

      if (isDocumentStatePayload(msg.Payload)) {
        const newValue = contentToSlateValue(msg.Payload.Content);
        versionRef.current = msg.Payload.Version;

        isRemote.current = true;
        try {
          Transforms.delete(editor, {
            at: {
              anchor: { path: [0, 0], offset: 0 },
              focus: editor.end([]),
            },
          });
          Transforms.insertNodes(editor, newValue, { at: [0] });
        } finally {
          isRemote.current = false;
        }
      } else {
        // Ignore self-echoed ops (server broadcasts to all clients including sender).
        if (msg.ClientId === user?.id) return;

        const slateOp = otOpToSlateOp(msg.Payload.Operation, editor);

        isRemote.current = true;
        try {
          if (slateOp.type === "insert_text") {
            Transforms.insertText(editor, slateOp.text, {
              at: { path: slateOp.path, offset: slateOp.offset },
            });
          } else if (slateOp.type === "remove_text") {
            Transforms.delete(editor, {
              at: {
                anchor: { path: slateOp.path, offset: slateOp.offset },
                focus: {
                  path: slateOp.path,
                  offset: slateOp.offset + slateOp.text.length,
                },
              },
            });
          }
        } catch (err) {
          console.error("Failed to apply remote operation:", slateOp, err);
        } finally {
          isRemote.current = false;
          suppressNextLocalSendRef.current = Math.max(
            suppressNextLocalSendRef.current,
            2,
          );
        }
      }
    };

    ws.onclose = () => {
      if (connectionSeqRef.current !== seq) return;
      setStatus("disconnected");
    };
    ws.onerror = () => {
      if (connectionSeqRef.current !== seq) return;
      setStatus("disconnected");
    };

    return () => {
      if (connectionSeqRef.current === seq) {
        connectionSeqRef.current += 1;
      }

      try {
        ws.close();
      } catch {
        // ignore
      }

      if (wsRef.current === ws) {
        wsRef.current = null;
      }
    };
  }, [accessToken, docId, editor, user?.id]);

  // ─── Outbound ops ──────────────────────────────────────────────────────

  const handleChange = useCallback(() => {
    if (suppressNextLocalSendRef.current > 0) {
      suppressNextLocalSendRef.current -= 1;
      return;
    }

    if (isRemote.current) return;
    if (wsRef.current?.readyState !== WebSocket.OPEN) return;
    if (!user) return;

    const otOps = [...editor.operations]
      .map((op) => slateOpToOT(op, editor, versionRef.current, user.id))
      .filter((op): op is OTOperation => op !== null);

    if (otOps.length === 0) return;

    const merged: OTOperation[] = [];
    let sendVersion = versionRef.current;

    for (const op of otOps) {
      const opWithVersion = { ...op, version: sendVersion };
      const last = merged[merged.length - 1];

      if (
        last &&
        last.type === OT_INSERT &&
        opWithVersion.type === OT_INSERT &&
        opWithVersion.position === last.position + (last.data as string).length
      ) {
        last.data = (last.data as string) + (opWithVersion.data as string);
      } else {
        merged.push(opWithVersion);
      }

      sendVersion += 1;
    }

    for (const otOp of merged) {
      wsRef.current!.send(JSON.stringify(otOp));
      versionRef.current += 1;
    }
  }, [editor, user]);

  const closeGrant = () => {
    if (isGranting) return;
    setIsGrantOpen(false);
    setGrantEmail("");
    setGrantError(null);
    setGrantSuccess(null);
  };

  const openGrant = () => {
    setIsGrantOpen(true);
    setGrantEmail("");
    setGrantError(null);
    setGrantSuccess(null);
  };

  const submitGrant = async () => {
    if (!docId) {
      setGrantError("Missing document id");
      return;
    }
    if (!user?.id) {
      setGrantError("Missing owner id");
      return;
    }

    const email = grantEmail.trim();
    if (!email) {
      setGrantError("User email is required");
      return;
    }

    setIsGranting(true);
    setGrantError(null);
    setGrantSuccess(null);

    try {
      console.log({ documentId: docId, userEmail: email, ownerId: user.id });
      await docManagerApi.put("/documents/access", {
        documentId: docId,
        userEmail: email,
        ownerId: user.id,
      });
      setGrantSuccess("Access granted");
      setGrantEmail("");
      setIsGrantOpen(false);
    } catch (err: unknown) {
      setGrantError("Failed to grant access");
    } finally {
      setIsGranting(false);
    }
  };

  const onGrantKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") submitGrant();
    if (e.key === "Escape") closeGrant();
  };

  // ─── Render ────────────────────────────────────────────────────────────

  return (
    <div className="editor-shell">
      <header className="editor-top-bar">
        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
          <button
            type="button"
            className="secondary"
            onClick={() => navigate("/")}
            aria-label="Back to dashboard"
            style={{
              padding: "0.6rem 0.7rem",
              letterSpacing: "0.2em",
            }}
          >
            ←
          </button>

          <span className="small-print">doc</span>
        </div>

        <div
          className="title"
          style={{
            flex: 1,
            textAlign: "center",
            color: "var(--text-primary)",
            fontFamily: "var(--font-display)",
            fontStyle: "italic",
          }}
        >
          {docId ?? "untitled"}
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
          {!isGrantOpen ? (
            <button
              type="button"
              className="secondary"
              onClick={openGrant}
              aria-label="Grant access"
              disabled={isGranting}
              style={{ padding: "0.6rem 0.75rem" }}
            >
              + editor
            </button>
          ) : (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.5rem",
                minWidth: 420,
              }}
            >
              <input
                autoFocus
                value={grantEmail}
                onChange={(e) => setGrantEmail(e.target.value)}
                onKeyDown={onGrantKeyDown}
                placeholder="User email…"
                disabled={isGranting}
                aria-label="User email to grant access"
                style={{ padding: "0.6rem 0.75rem" }}
              />
              <button
                type="button"
                className="primary"
                onClick={submitGrant}
                disabled={isGranting || grantEmail.trim() === ""}
                style={{ padding: "0.6rem 0.75rem" }}
              >
                {isGranting ? "Granting…" : "Grant"}
              </button>
              <button
                type="button"
                className="secondary"
                onClick={closeGrant}
                disabled={isGranting}
                style={{ padding: "0.6rem 0.75rem" }}
              >
                Cancel
              </button>
            </div>
          )}

          <div
            className="status-text"
            aria-label={`Connection status: ${statusLabel(status)}`}
          >
            <span className={`status-dot ${status}`} />
            {statusLabel(status)}
          </div>
        </div>
      </header>

      <main className="editor-main">
        <div style={{ margin: "0.75rem auto 0", maxWidth: "56rem" }}>
          {grantError && <div className="error-text">{grantError}</div>}
          {grantSuccess && <div className="success-text">{grantSuccess}</div>}
        </div>

        <div className="editor-page-wrapper">
          <div className="editor-page">
            <Slate
              editor={editor}
              initialValue={EMPTY_DOC}
              onChange={handleChange}
            >
              <Editable
                className="editor-area"
                placeholder={
                  status === "connected" ? "Start typing…" : "Connecting…"
                }
              />
            </Slate>
          </div>
        </div>
      </main>
    </div>
  );
}
