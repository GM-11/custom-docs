import { useCallback, useEffect, useId, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/authHook";
import { extractErrorMessage } from "../utils/errors";

type Mode = "login" | "register";

export default function Auth() {
  const navigate = useNavigate();
  const { login, register } = useAuth();

  const [mode, setMode] = useState<Mode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const emailId = useId();
  const passwordId = useId();
  const nameId = useId();

  // Reset all fields when mode switches
  useEffect(() => {
    setEmail("");
    setPassword("");
    setName("");
    setError(null);
  }, [mode]);

  const handleSubmit = useCallback(async () => {
    if (!email.trim()) {
      setError("Email is required");
      return;
    }
    if (!password.trim()) {
      setError("Password is required");
      return;
    }
    if (mode === "register" && !name.trim()) {
      setError("Name is required");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(email, password, name);
      }
      navigate("/");
    } catch (err: unknown) {
      setError(extractErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }, [mode, email, password, name, login, register, navigate]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleSubmit();
  };

  return (
    <div
      className="full-screen auth-shell"
      style={{ justifyContent: "center" }}
    >
      <div
        className="container"
        style={{ paddingTop: "3rem", paddingBottom: "3rem" }}
      >
        <div
          className="auth-card"
          style={{
            width: "min(520px, 100%)",
            margin: "0 auto",
          }}
        >
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "0.35rem",
              marginBottom: "1.5rem",
            }}
          >
            <div
              className="logo"
              style={{ fontSize: "2.4rem", lineHeight: 1.1 }}
            >
              docs.
            </div>
            <p
              style={{
                marginTop: "0.25rem",
                fontFamily: "var(--font-body)",
                fontSize: "0.95rem",
                color: "var(--text-secondary)",
              }}
            >
              Write together.
            </p>
          </div>

          <div
            className="tabs"
            role="tablist"
            aria-label="Authentication mode"
            style={{ marginBottom: "1.5rem" }}
          >
            <button
              type="button"
              className={`tab ${mode === "login" ? "active" : ""}`}
              aria-selected={mode === "login"}
              role="tab"
              onClick={() => setMode("login")}
              disabled={isSubmitting}
            >
              Login
            </button>

            <button
              type="button"
              className={`tab ${mode === "register" ? "active" : ""}`}
              aria-selected={mode === "register"}
              role="tab"
              onClick={() => setMode("register")}
              disabled={isSubmitting}
            >
              Register
            </button>
          </div>

          <div className="inputs-group">
            <div style={{ display: "grid", gap: "0.35rem" }}>
              <label className="small-print" htmlFor={emailId}>
                Email
              </label>
              <input
                id={emailId}
                type="email"
                placeholder="you@studio.co"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={isSubmitting}
                autoComplete={mode === "login" ? "email" : "email"}
              />
            </div>

            {mode === "register" && (
              <div style={{ display: "grid", gap: "0.35rem" }}>
                <label className="small-print" htmlFor={nameId}>
                  Name
                </label>
                <input
                  id={nameId}
                  type="text"
                  placeholder="Your name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isSubmitting}
                  autoComplete="name"
                />
              </div>
            )}

            <div style={{ display: "grid", gap: "0.35rem" }}>
              <label className="small-print" htmlFor={passwordId}>
                Password
              </label>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.5rem",
                }}
              >
                <input
                  id={passwordId}
                  type={showPassword ? "text" : "password"}
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isSubmitting}
                  autoComplete={
                    mode === "login" ? "current-password" : "new-password"
                  }
                  style={{ flex: 1 }}
                />
                <button
                  type="button"
                  className="secondary"
                  onClick={() => setShowPassword((prev) => !prev)}
                  disabled={isSubmitting}
                  style={{
                    whiteSpace: "nowrap",
                    paddingInline: "0.75rem",
                  }}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </div>

            <button
              type="button"
              className="primary"
              onClick={handleSubmit}
              disabled={isSubmitting}
              style={{ width: "100%", marginTop: "0.5rem" }}
            >
              {isSubmitting
                ? "Loading..."
                : mode === "login"
                  ? "Login"
                  : "Register"}
            </button>
          </div>

          {error && (
            <div style={{ marginTop: "0.9rem" }}>
              <p className="error-text">{error}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
