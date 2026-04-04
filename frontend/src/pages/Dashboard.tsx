import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { extractErrorMessage } from "../utils/errors";
import { useAuth } from "../hooks/authHook";
import docManagerApi from "../api/docManagerApi";
import docApi from "../api/docApi";
import type { Document } from "../types/document";

export default function Dashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [documents, setDocuments] = useState<Document[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [isCreating, setIsCreating] = useState(false);
  const [isTitleOpen, setIsTitleOpen] = useState(false);
  const [newTitle, setNewTitle] = useState("");

  const displayName = useMemo(() => {
    return user?.name?.trim() || user?.email || "Friend";
  }, [user]);

  useEffect(() => {
    if (!user) return;

    setIsLoading(true);
    setError(null);

    docManagerApi
      .get<Document[]>(`/documents/${user.id}`)
      .then(({ data }) => setDocuments(data))
      .catch((err: unknown) => {
        setError(extractErrorMessage(err, "Failed to load documents"));
      })
      .finally(() => setIsLoading(false));
  }, [user]);

  const handleOpen = (id: string) => {
    navigate(`/doc/${id}`);
  };

  const beginCreate = () => {
    if (isCreating) return;
    setError(null);
    setIsTitleOpen(true);
    setNewTitle("");
  };

  const cancelCreate = () => {
    if (isCreating) return;
    setIsTitleOpen(false);
    setNewTitle("");
  };

  const submitCreate = async () => {
    const title = newTitle.trim();
    if (!title) return;

    setIsCreating(true);
    setError(null);

    try {
      const { data: docId } = await docApi.post<string>(
        `/documents?title=${encodeURIComponent(title)}`,
      );
      navigate(`/doc/${docId}`);
    } catch (err: unknown) {
      setError(extractErrorMessage(err, "Failed to create document"));
    } finally {
      setIsCreating(false);
    }
  };

  const onNewTitleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") submitCreate();
    if (e.key === "Escape") cancelCreate();
  };

  const handleLogout = async () => {
    await logout();
    navigate("/auth");
  };

  const greeting = (() => {
    const hour = new Date().getHours();
    if (hour < 12) return "Good morning";
    if (hour < 18) return "Good afternoon";
    return "Good evening";
  })();

  return (
    <div className="dashboard-shell">
      <div className="container">
        <div className="dashboard-grid">
          {/* Sidebar */}
          <aside className="sidebar">
            <div className="column" style={{ gap: "1.75rem", height: "100%" }}>
              <div className="column" style={{ gap: "1.25rem" }}>
                <div>
                  <div
                    className="logo"
                    style={{ fontSize: "1.4rem", marginBottom: "0.4rem" }}
                  >
                    docs.
                  </div>
                  <div
                    className="small-print"
                    style={{ color: "var(--text-secondary)" }}
                  >
                    A warm place to write together.
                  </div>
                </div>

                <div>
                  <div className="small-print">Signed in</div>
                  <div
                    style={{
                      color: "var(--text-primary)",
                      marginTop: "0.4rem",
                      fontSize: "0.95rem",
                    }}
                  >
                    {displayName}
                  </div>
                </div>
              </div>

              <div
                className="column"
                style={{
                  gap: "0.6rem",
                  marginTop: "auto",
                  paddingTop: "1.25rem",
                  borderTop: "1px solid var(--border)",
                }}
              >
                <button
                  className="secondary no-shadow"
                  onClick={handleLogout}
                  style={{ width: "100%", justifyContent: "center" }}
                >
                  Logout
                </button>
              </div>
            </div>
          </aside>

          {/* Main area */}
          <main className="dashboard-main column" style={{ gap: "1.5rem" }}>
            <header className="flex items-center justify-between">
              <div className="column" style={{ gap: "0.35rem" }}>
                <h1
                  style={{
                    fontSize: "1.8rem",
                    fontFamily: "var(--font-display)",
                  }}
                >
                  {greeting}, {displayName}
                </h1>
                <div className="small-print">
                  Your documents, in one quiet place.
                </div>
              </div>

              <div className="flex items-center" style={{ gap: "0.75rem" }}>
                {!isTitleOpen ? (
                  <button
                    className="primary"
                    onClick={beginCreate}
                    disabled={isCreating}
                    style={{ paddingInline: "1.25rem" }}
                  >
                    New document
                  </button>
                ) : (
                  <div
                    className="flex items-center surface"
                    style={{
                      gap: "0.6rem",
                      padding: "0.6rem 0.75rem 0.6rem 0.85rem",
                      borderRadius: "0.75rem",
                    }}
                  >
                    <input
                      autoFocus
                      value={newTitle}
                      onChange={(e) => setNewTitle(e.target.value)}
                      onKeyDown={onNewTitleKeyDown}
                      placeholder="Document title"
                      disabled={isCreating}
                      aria-label="New document title"
                      style={{
                        background: "transparent",
                        border: "none",
                        padding: 0,
                      }}
                    />
                    <button
                      className="primary"
                      onClick={submitCreate}
                      disabled={isCreating || newTitle.trim() === ""}
                    >
                      {isCreating ? "Creating…" : "Create"}
                    </button>
                    <button
                      className="secondary"
                      onClick={cancelCreate}
                      disabled={isCreating}
                    >
                      Cancel
                    </button>
                  </div>
                )}
              </div>
            </header>

            {error && (
              <div className="error-text" style={{ marginTop: "0.25rem" }}>
                {error}
              </div>
            )}

            {isLoading && (
              <div className="small-print" style={{ marginTop: "0.5rem" }}>
                Loading documents…
              </div>
            )}

            {!isLoading && !error && documents.length === 0 && (
              <div
                className="card"
                style={{
                  textAlign: "center",
                  paddingBlock: "2.5rem",
                }}
              >
                <div
                  style={{
                    fontSize: "2rem",
                    marginBottom: "0.75rem",
                    color: "var(--text-secondary)",
                  }}
                >
                  +
                </div>
                <div
                  style={{
                    color: "var(--text-primary)",
                    marginBottom: "0.35rem",
                    fontFamily: "var(--font-display)",
                  }}
                >
                  No documents yet
                </div>
                <div className="small-print">
                  Start a new page to begin writing.
                </div>
              </div>
            )}

            {!isLoading && !error && documents.length > 0 && (
              <section className="documents-list">
                {documents
                  .slice()
                  .sort(
                    (a, b) =>
                      new Date(b.createdAt).getTime() -
                      new Date(a.createdAt).getTime(),
                  )
                  .map((doc) => (
                    <article
                      key={doc.id}
                      className="document-card"
                      role="button"
                      tabIndex={0}
                      onClick={() => handleOpen(doc.id)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") handleOpen(doc.id);
                      }}
                      aria-label={`Open document ${doc.title}`}
                    >
                      <div>
                        <div className="document-card-title">{doc.title}</div>
                        <div className="document-card-meta">
                          {new Date(doc.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div
                        style={{
                          fontSize: "1.25rem",
                          color: "var(--text-secondary)",
                        }}
                      >
                        →
                      </div>
                    </article>
                  ))}
              </section>
            )}
          </main>
        </div>
      </div>
    </div>
  );
}
