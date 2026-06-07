import React, { useState } from 'react';
import { Form, Input, Button, Card, App } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { login } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';

const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const { message, modal } = App.useApp();

  const onFinish = async (values: { loginId: string; password: string }) => {
    setLoading(true);
    try {
      const res = await login(values.loginId, values.password);
      const { token, refreshToken, user } = res.data.data;
      setAuth(token, refreshToken, user);
      message.success(`欢迎回来，${user.name}`);
      const mustChangePassword = user.forcePasswordChange === true || user.forcePasswordChange === 1;
      navigate(mustChangePassword ? '/force-change-password' : '/', { replace: true });
    } catch (e: any) {
      modal.error({
        title: '登录失败',
        content: e.response?.data?.message || '用户名或密码错误',
        centered: true,
        okText: '我知道了',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #F7F8FA 0%, #EEF1F5 100%)',
      }}
    >
      <Card
        style={{
          width: 420,
          borderRadius: 16,
          boxShadow: '0 8px 32px rgba(0,0,0,0.08)',
          border: 'none',
        }}
        styles={{ body: { padding: '48px 40px' } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 40 }}>
          <img
            src="/logo.jpg"
            alt="综合素质评价系统"
            style={{
              width: 52,
              height: 52,
              borderRadius: 14,
              display: 'inline-block',
              objectFit: 'cover',
              marginBottom: 16,
            }}
          />
          <div style={{ fontSize: 22, fontWeight: 600, color: 'rgba(0,0,0,0.88)' }}>
            综合素质评价系统
          </div>
          <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.35)', marginTop: 4, letterSpacing: 0.5 }}>
            Student Evaluation System
          </div>
        </div>

        <Form layout="vertical" onFinish={onFinish} size="large">
          <Form.Item name="loginId" rules={[{ required: true, message: '请输入学号/工号' }]}>
            <Input
              prefix={<UserOutlined style={{ color: 'rgba(0,0,0,0.25)' }} />}
              placeholder="请输入学号/工号"
            />
          </Form.Item>

          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password
              prefix={<LockOutlined style={{ color: 'rgba(0,0,0,0.25)' }} />}
              placeholder="请输入密码"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
              style={{ height: 44, borderRadius: 8, fontSize: 16 }}
            >
              登 录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center', marginTop: 32, fontSize: 12, color: 'rgba(0,0,0,0.25)' }}>
          &copy; 2026 综合素质评价系统
        </div>
      </Card>
    </div>
  );
};

export default LoginPage;
