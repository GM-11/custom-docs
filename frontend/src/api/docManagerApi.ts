import axios from "axios";
import { ACCESS_TOKEN_KEY } from "./authApi";
import { attachRefreshInterceptor } from "./refreshInterceptor";

const DOC_MANAGER_BASE_URL =
  import.meta.env.VITE_DOC_MANAGER_API_BASE_URL ?? "http://localhost:8081";

const docManagerApi = axios.create({
  baseURL: `${DOC_MANAGER_BASE_URL}`,
});

docManagerApi.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);

  if (token) {

    config.headers = config.headers ?? {};
    (config.headers as Record<string, string>).Authorization = `Bearer ${token}`;
  }

  return config;
});

attachRefreshInterceptor(docManagerApi);

export default docManagerApi;
