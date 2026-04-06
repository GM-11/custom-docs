import axios from "axios";
import { attachRefreshInterceptor } from "./refreshInterceptor";

export const ACCESS_TOKEN_KEY = "access_token";
export const REFRESH_TOKEN_KEY = "refresh_token";
export const USER_KEY = "user";

const AUTH_API_BASE_URL =
  import.meta.env.VITE_AUTH_API_BASE_URL ?? "http://localhost:8082";

const authApi = axios.create({
  baseURL: AUTH_API_BASE_URL,
});

authApi.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

attachRefreshInterceptor(authApi);

export default authApi;
