import { createContext, useEffect, useState, type ReactNode } from "react";
import authApi, {
  ACCESS_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  USER_KEY,
} from "../api/authApi";

export type User = {
  id: string;
  email: string;
  name: string;
};

export type AuthContextType = {
  user: User | null;
  accessToken: string | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, name: string) => Promise<void>;
  logout: () => Promise<void>;
};

type JwtPayload = {
  sub: string;
  iat: number;
  exp: number;
};

function decodeJwtPayload(token: string): JwtPayload {
  try {
    const base64 = token.split(".")[1];
    if (!base64) throw new Error("Missing JWT payload segment");
    const json = atob(base64.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json) as JwtPayload;
  } catch (cause) {
    throw new Error("Failed to decode JWT payload", { cause });
  }
}

function storeSession(
  accessToken: string,
  refreshToken: string,
  user: User,
): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

function clearSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Rehydrate from localStorage on mount so page refresh doesn't lose state
  useEffect(() => {
    const storedToken = localStorage.getItem(ACCESS_TOKEN_KEY);
    const storedUser = localStorage.getItem(USER_KEY);

    if (storedToken && storedUser) {
      try {
        setAccessToken(storedToken);
        setUser(JSON.parse(storedUser) as User);
      } catch {
        // Corrupted storage — wipe it and treat as logged-out
        clearSession();
      }
    }

    setIsLoading(false);
  }, []);

  const login = async (email: string, password: string): Promise<void> => {
    const { data } = await authApi.post<{
      accessToken: string;
      refreshToken: string;
    }>("/auth/login", { email, password });

    const { sub: id } = decodeJwtPayload(data.accessToken);

    // The server's AuthResponse carries only tokens — no name is returned for
    // login. Using email as the display name until a /users/me endpoint exists.
    const loggedInUser: User = { id, email, name: email };

    storeSession(data.accessToken, data.refreshToken, loggedInUser);
    setAccessToken(data.accessToken);
    setUser(loggedInUser);
  };

  const register = async (
    email: string,
    password: string,
    name: string,
  ): Promise<void> => {
    const { data } = await authApi.post<{
      accessToken: string;
      refreshToken: string;
    }>("/auth/register", { email, password, name });

    const { sub: id } = decodeJwtPayload(data.accessToken);
    const newUser: User = { id, email, name };

    storeSession(data.accessToken, data.refreshToken, newUser);
    setAccessToken(data.accessToken);
    setUser(newUser);
  };

  const logout = async (): Promise<void> => {
    try {
      // The access token attached by the request interceptor is all the server
      // needs — logout reads the user via @AuthenticationPrincipal, no body.
      await authApi.post("/auth/logout");
    } catch {
      // Best-effort — clear the local session regardless of server response
    } finally {
      clearSession();
      setAccessToken(null);
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{ user, accessToken, isLoading, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}
