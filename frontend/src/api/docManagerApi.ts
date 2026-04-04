import axios from "axios";
import { ACCESS_TOKEN_KEY } from "./authApi";
import { attachRefreshInterceptor } from "./refreshInterceptor";

const DOC_MANAGER_BASE_URL = "http://localhost:8081";

const docManagerApi = axios.create({
  baseURL: DOC_MANAGER_BASE_URL,
});

docManagerApi.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

attachRefreshInterceptor(docManagerApi);

export default docManagerApi;
