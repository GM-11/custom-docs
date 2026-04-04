package auth

import (
	"context"
	"net/http"
	"strings"
)

// Extract Authorization header, check Bearer prefix
// Call a ValidateToken(token string) (string, error) function that verifies the JWT and returns user_id
// If invalid → w.WriteHeader(http.StatusUnauthorized) and return
// If valid → stuff user_id into request context using context.WithValue, call next
type contextKey string

const UserIdKey contextKey = "userId"

func AuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			token := r.URL.Query().Get("token")
			if token == "" {
				w.WriteHeader(http.StatusUnauthorized)
				return
			}
			userId, err := ValidateToken(token)
			if err != nil {
				http.Error(w, "Invalid token: "+err.Error(), http.StatusUnauthorized)
				return
			}
			ctx := context.WithValue(r.Context(), UserIdKey, userId)
			next.ServeHTTP(w, r.WithContext(ctx))
			return
		}

		prefix := "Bearer "
		if !strings.HasPrefix(authHeader, prefix) {
			http.Error(w, "Malformed authorization header: not a bearer token", http.StatusUnauthorized)
			return
		}

		reqToken := strings.TrimPrefix(authHeader, prefix)

		userId, err := ValidateToken(reqToken)
		if err != nil {
			http.Error(w, "Invalid token: "+err.Error(), http.StatusUnauthorized)
			return
		}

		ctx := context.WithValue(r.Context(), UserIdKey, userId)

		rWithCtx := r.WithContext(ctx)
		next.ServeHTTP(w, rWithCtx)
	})
}
