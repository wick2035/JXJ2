import React, { useState } from 'react';
import { App, Button, Form, Input, Typography } from 'antd';
import { CheckCircleOutlined, LockOutlined, LogoutOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { changePassword } from '../../api/auth';
import { useAuthStore } from '../../store/authStore';

const ForceChangePasswordPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { user, setUser, logout } = useAuthStore();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const onFinish = async (values: { oldPassword: string; newPassword: string; confirmPassword: string }) => {
    setLoading(true);
    try {
      await changePassword(values.oldPassword, values.newPassword);
      if (user) {
        setUser({ ...user, forcePasswordChange: 0 });
      }
      message.success('密码已更新');
      navigate('/', { replace: true });
    } catch (e: any) {
      message.error(e.response?.data?.message || '修改密码失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
        background:
          'linear-gradient(135deg, #F6F8FB 0%, #EEF5F4 52%, #F7F2EA 100%)',
      }}
    >
      <div
        style={{
          width: 'min(100%, 460px)',
          background: '#fff',
          border: '1px solid rgba(15, 23, 42, 0.08)',
          borderRadius: 18,
          boxShadow: '0 24px 70px rgba(15, 23, 42, 0.12)',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '34px 36px 24px',
            background: 'linear-gradient(135deg, rgba(22,119,255,0.10), rgba(19,194,194,0.08))',
            borderBottom: '1px solid rgba(15, 23, 42, 0.06)',
          }}
        >
          <div
            style={{
              width: 52,
              height: 52,
              display: 'grid',
              placeItems: 'center',
              borderRadius: 14,
              color: '#fff',
              background: 'linear-gradient(135deg, #1677ff 0%, #13c2c2 100%)',
              boxShadow: '0 14px 30px rgba(22,119,255,0.24)',
              marginBottom: 18,
              fontSize: 24,
            }}
          >
            <SafetyCertificateOutlined />
          </div>
          <Typography.Title level={3} style={{ margin: 0, letterSpacing: 0 }}>
            修改初始密码
          </Typography.Title>
          <Typography.Paragraph style={{ margin: '8px 0 0', color: 'rgba(0,0,0,0.56)' }}>
            为保护账号安全，首次登录或密码重置后需要先设置新密码。
          </Typography.Paragraph>
        </div>

        <div style={{ padding: '28px 36px 34px' }}>
          <Form layout="vertical" size="large" onFinish={onFinish}>
            <Form.Item
              name="oldPassword"
              label="当前密码"
              rules={[{ required: true, message: '请输入当前密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请输入当前密码" />
            </Form.Item>

            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 6, message: '新密码至少 6 位' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || value !== getFieldValue('oldPassword')) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('新密码不能与当前密码相同'));
                  },
                }),
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请设置新密码" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认新密码"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请再次输入新密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的新密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请再次输入新密码" />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              icon={<CheckCircleOutlined />}
              loading={loading}
              block
              style={{ height: 44, borderRadius: 8, marginTop: 8 }}
            >
              更新密码并进入系统
            </Button>
            <Button
              icon={<LogoutOutlined />}
              block
              onClick={handleLogout}
              style={{ height: 42, borderRadius: 8, marginTop: 12 }}
            >
              退出登录
            </Button>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default ForceChangePasswordPage;
