import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/authHook";

export default function ProtectedRoute() {
  const { isLoading, accessToken } = useAuth();

  if (isLoading) {
    // Don't redirect yet — localStorage rehydration hasn't finished.
    // Returning null avoids a flash-redirect to /login on page refresh.
    return null;
  }

  if (!accessToken) {
    return <Navigate to="/auth" replace />;
  }

  return <Outlet />;
}
