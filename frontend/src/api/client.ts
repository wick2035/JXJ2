import axios from 'axios';
import { message } from 'antd';
import { useAuthStore } from '../store/authStore';

const client = axios.create({
  baseURL: 'http://localhost:8081',
  timeout: 30000,
});

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** 防止并发 401 重复弹窗 / 重复跳转 */
let sessionExpiredHandled = false;

client.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 403 && error.response?.data?.code === 40301) {
      if (window.location.pathname !== '/force-change-password') {
        window.location.href = '/force-change-password';
      }
      return Promise.reject(error);
    }
    if (error.response?.status === 401) {
      // 登录态接口（登录/刷新）自身的 401 交给页面处理，避免“密码错误”被当成会话过期并触发跳转循环
      const url = error.config?.url ?? '';
      const isAuthEndpoint = url.includes('/api/auth/login') || url.includes('/api/auth/refresh');
      if (!isAuthEndpoint && !sessionExpiredHandled) {
        sessionExpiredHandled = true;
        useAuthStore.getState().logout();
        if (window.location.pathname !== '/login') {
          message.error('登录已过期，请重新登录');
          window.location.href = '/login';
        }
      }
    }
    return Promise.reject(error);
  }
);

export default client;
