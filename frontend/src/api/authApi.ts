import axios from "axios";
import { attachRefreshInterceptor } from "./refreshInterceptor";

export const ACCESS_TOKEN_KEY = "access_token";
export const REFRESH_TOKEN_KEY = "refresh_token";
export const USER_KEY = "user";

const authApi = axios.create({
  baseURL: "http://localhost:8082",
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
