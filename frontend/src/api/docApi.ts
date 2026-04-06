import axios from "axios";
import { ACCESS_TOKEN_KEY } from "./authApi";
import { attachRefreshInterceptor } from "./refreshInterceptor";

const DOC_SERVICE_BASE_URL =
  import.meta.env.VITE_CONNECTION_MANAGER_BASE_API_URL ?? "http://localhost:8080";

const docApi = axios.create({
  baseURL: DOC_SERVICE_BASE_URL,
});

docApi.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

attachRefreshInterceptor(docApi);

export default docApi;
