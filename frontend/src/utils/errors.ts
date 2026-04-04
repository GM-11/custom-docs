export function extractErrorMessage(err: unknown, fallback?: string): string {
  if (
    err &&
    typeof err === "object" &&
    "response" in err &&
    err.response &&
    typeof err.response === "object" &&
    "data" in err.response &&
    err.response.data &&
    typeof err.response.data === "object" &&
    "error" in err.response.data &&
    typeof (err.response.data as { error: unknown }).error === "string"
  ) {
    return (err.response.data as { error: string }).error;
  }
  if (err && typeof err === "object" && "message" in err) {
    return (err as { message: string }).message;
  }
  return fallback ?? "Something went wrong";
}
