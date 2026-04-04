import type {
  AxiosError,
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
} from "axios";
import axios from "axios";
import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY } from "./authApi";

type RefreshResponse = { accessToken: string; refreshToken: string };

type FailedQueueItem = {
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
};

let isRefreshing = false;
let failedQueue: FailedQueueItem[] = [];

function processQueue(error: unknown, token: string | null) {
  const queue = failedQueue;
  failedQueue = [];

  for (const prom of queue) {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    } else {
      prom.reject(new Error("Refresh completed without a token"));
    }
  }
}

function clearSessionAndRedirectToAuth() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);

  // Avoid infinite reload loops if you're already on /auth
  if (typeof window !== "undefined") {
    const path = window.location?.pathname ?? "";
    if (path !== "/auth") {
      window.location.assign("/auth");
    }
  }
}

function isRefreshRequest(config?: AxiosRequestConfig) {
  const url = config?.url ?? "";
  return url.includes("/auth/refresh");
}

function setAuthorizationHeader(
  config: AxiosRequestConfig,
  accessToken: string,
): AxiosRequestConfig {
  const headers = (config.headers ?? {}) as Record<string, unknown>;
  return {
    ...config,
    headers: {
      ...headers,
      Authorization: `Bearer ${accessToken}`,
    },
  };
}

/**
 * Attaches a shared refresh-token response interceptor to an axios instance.
 *
 * Flow:
 * - any 401 triggers a single refresh request
 * - concurrent 401s wait for the refresh to complete, then retry
 * - refresh failure clears session + redirects to /auth
 *
 * Important:
 * - retry explicitly sets Authorization header to avoid relying on request interceptors
 */
export function attachRefreshInterceptor(axiosInstance: AxiosInstance) {
  axiosInstance.interceptors.response.use(
    (response: AxiosResponse) => response,
    async (error: AxiosError) => {
      const status = error.response?.status;
      const originalRequest = error.config as AxiosRequestConfig | undefined;

      if (status !== 401) {
        return Promise.reject(error);
      }

      // If we don't have a request config, we can't retry.
      if (!originalRequest) {
        return Promise.reject(error);
      }

      // Prevent infinite loop: if refresh itself 401s, clear session and bounce to /auth.
      if (isRefreshRequest(originalRequest)) {
        clearSessionAndRedirectToAuth();
        return Promise.reject(error);
      }

      // If a refresh is already in-flight, queue this request until it's done.
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: (token: string) => {
              try {
                const retryConfig = setAuthorizationHeader(originalRequest, token);
                resolve(axiosInstance(retryConfig));
              } catch (e) {
                reject(e);
              }
            },
            reject,
          });
        });
      }

      isRefreshing = true;

      const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
      if (!refreshToken) {
        isRefreshing = false;
        clearSessionAndRedirectToAuth();
        processQueue(new Error("No refresh token available"), null);
        return Promise.reject(error);
      }

      try {
        // Use a plain axios call (not the instance) so we don't create interceptor loops.
        // Absolute URL so it works regardless of the instance's baseURL.
        const refreshResponse = await axios.post<RefreshResponse>(
          "http://localhost:8082/auth/refresh",
          {
            refreshToken,
          },
        );

        const { accessToken: newAccessToken, refreshToken: newRefreshToken } =
          refreshResponse.data;

        localStorage.setItem(ACCESS_TOKEN_KEY, newAccessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken);

        processQueue(null, newAccessToken);

        const retryConfig = setAuthorizationHeader(originalRequest, newAccessToken);
        return axiosInstance(retryConfig);
      } catch (refreshErr) {
        processQueue(refreshErr, null);
        clearSessionAndRedirectToAuth();
        return Promise.reject(refreshErr);
      } finally {
        isRefreshing = false;
      }
    },
  );
}
