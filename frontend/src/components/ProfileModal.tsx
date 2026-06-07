import React, { useEffect, useMemo, useState } from 'react';
import { App, Avatar, Button, Descriptions, Form, Input, Modal, Segmented, Tag, Typography } from 'antd';
import {
  IdcardOutlined,
  KeyOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { changePassword } from '../api/auth';
import { changeSecondaryPassword } from '../api/config';
import { updateMe } from '../api/user';
import { useAuthStore } from '../store/authStore';
import type { UserVO } from '../types';

type ProfileModalProps = {
  open: boolean;
  onClose: () => void;
};

type ProfileFormValues = {
  email?: string;
  phone?: string;
};

type PasswordFormValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

type SecondaryFormValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

const roleMeta: Record<UserVO['role'], { label: string; color: string }> = {
  admin: { label: '管理员', color: 'gold' },
  teacher: { label: '教师', color: 'green' },
  student: { label: '学生', color: 'blue' },
};

const display = (value?: string) => value || '未填写';

const ProfileModal: React.FC<ProfileModalProps> = ({ open, onClose }) => {
  const { message } = App.useApp();
  const { user, setUser } = useAuthStore();
  const [activeTab, setActiveTab] = useState<'profile' | 'security' | 'secondary'>('profile');
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);
  const [savingSecondary, setSavingSecondary] = useState(false);
  const [profileForm] = Form.useForm<ProfileFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const [secondaryForm] = Form.useForm<SecondaryFormValues>();
  const isAdmin = user?.role === 'admin';

  const meta = useMemo(
    () => roleMeta[user?.role || 'student'],
    [user?.role]
  );

  useEffect(() => {
    if (!open || !user) return;
    setActiveTab('profile');
    profileForm.setFieldsValue({
      email: user.email,
      phone: user.phone,
    });
    passwordForm.resetFields();
    secondaryForm.resetFields();
  }, [open, passwordForm, profileForm, secondaryForm, user]);

  const handleProfileSave = async () => {
    const values = await profileForm.validateFields();
    setSavingProfile(true);
    try {
      const res = await updateMe({
        email: values.email?.trim() || undefined,
        phone: values.phone?.trim() || undefined,
      });
      setUser(res.data.data);
      message.success('个人资料已更新');
    } catch (error: any) {
      message.error(error.response?.data?.message || '资料更新失败');
    } finally {
      setSavingProfile(false);
    }
  };

  const handlePasswordSave = async () => {
    const values = await passwordForm.validateFields();
    setSavingPassword(true);
    try {
      await changePassword(values.oldPassword, values.newPassword);
      passwordForm.resetFields();
      message.success('密码已更新');
    } catch (error: any) {
      message.error(error.response?.data?.message || '密码更新失败');
    } finally {
      setSavingPassword(false);
    }
  };

  const handleSecondarySave = async () => {
    const values = await secondaryForm.validateFields();
    setSavingSecondary(true);
    try {
      await changeSecondaryPassword(values.oldPassword, values.newPassword);
      secondaryForm.resetFields();
      message.success('二级密码已更新');
    } catch (error: any) {
      message.error(error.response?.data?.message || '二级密码更新失败');
    } finally {
      setSavingSecondary(false);
    }
  };

  return (
    <Modal
      centered
      width={740}
      open={open}
      onCancel={onClose}
      footer={null}
      className="profile-modal"
    >
      <div className="profile-modal-shell">
        <div className="profile-identity">
          <Avatar size={72} className="profile-identity-avatar">
            {user?.name?.charAt(0) || <UserOutlined />}
          </Avatar>
          <div className="profile-identity-main">
            <div className="profile-identity-line">
              <Typography.Title level={3}>{user?.name}</Typography.Title>
              {user?.role && <Tag color={meta.color}>{meta.label}</Tag>}
            </div>
            <div className="profile-identity-sub">
              <IdcardOutlined />
              <span>{user?.loginId}</span>
            </div>
          </div>
        </div>

        <Segmented
          block
          className="profile-segment"
          value={activeTab}
          onChange={(value) => setActiveTab(value as 'profile' | 'security' | 'secondary')}
          options={[
            { label: '资料', value: 'profile', icon: <UserOutlined /> },
            { label: '安全', value: 'security', icon: <SafetyCertificateOutlined /> },
            ...(isAdmin ? [{ label: '二级密码', value: 'secondary', icon: <KeyOutlined /> }] : []),
          ]}
        />

        {activeTab === 'profile' ? (
          <div className="profile-panel">
            <Descriptions column={2} size="small" className="profile-readonly">
              <Descriptions.Item label="账号">{display(user?.loginId)}</Descriptions.Item>
              <Descriptions.Item label="角色">{user?.role ? meta.label : '未填写'}</Descriptions.Item>
              <Descriptions.Item label="姓名">{display(user?.name)}</Descriptions.Item>
              <Descriptions.Item label="学院">{display(user?.college)}</Descriptions.Item>
              <Descriptions.Item label="专业">{display(user?.major)}</Descriptions.Item>
              <Descriptions.Item label="班级">{display(user?.className)}</Descriptions.Item>
              <Descriptions.Item label="年级">{display(user?.grade)}</Descriptions.Item>
            </Descriptions>

            <Form form={profileForm} layout="vertical" className="profile-form">
              <Form.Item
                name="email"
                label="邮箱"
                rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}
              >
                <Input prefix={<MailOutlined />} placeholder="name@example.com" />
              </Form.Item>
              <Form.Item name="phone" label="电话">
                <Input prefix={<PhoneOutlined />} placeholder="请输入联系电话" />
              </Form.Item>
              <div className="profile-actions">
                <Button onClick={onClose}>取消</Button>
                <Button type="primary" loading={savingProfile} onClick={handleProfileSave}>
                  保存资料
                </Button>
              </div>
            </Form>
          </div>
        ) : activeTab === 'security' ? (
          <div className="profile-panel">
            <Form form={passwordForm} layout="vertical" className="profile-form">
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
                ]}
              >
                <Input.Password prefix={<LockOutlined />} placeholder="请输入新密码" />
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
              <div className="profile-actions">
                <Button onClick={onClose}>取消</Button>
                <Button type="primary" loading={savingPassword} onClick={handlePasswordSave}>
                  更新密码
                </Button>
              </div>
            </Form>
          </div>
        ) : (
          <div className="profile-panel">
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
              二级密码用于删除操作记录等高危操作的二次校验。系统默认二级密码为
              <Typography.Text code>Sec@123456</Typography.Text>，建议首次使用后立即修改。
            </Typography.Paragraph>
            <Form form={secondaryForm} layout="vertical" className="profile-form">
              <Form.Item
                name="oldPassword"
                label="当前二级密码"
                rules={[{ required: true, message: '请输入当前二级密码' }]}
              >
                <Input.Password prefix={<KeyOutlined />} placeholder="请输入当前二级密码" />
              </Form.Item>
              <Form.Item
                name="newPassword"
                label="新二级密码"
                rules={[
                  { required: true, message: '请输入新二级密码' },
                  { min: 6, message: '新二级密码至少 6 位' },
                ]}
              >
                <Input.Password prefix={<KeyOutlined />} placeholder="请输入新二级密码" />
              </Form.Item>
              <Form.Item
                name="confirmPassword"
                label="确认新二级密码"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: '请再次输入新二级密码' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('newPassword') === value) {
                        return Promise.resolve();
                      }
                      return Promise.reject(new Error('两次输入的新二级密码不一致'));
                    },
                  }),
                ]}
              >
                <Input.Password prefix={<KeyOutlined />} placeholder="请再次输入新二级密码" />
              </Form.Item>
              <div className="profile-actions">
                <Button onClick={onClose}>取消</Button>
                <Button type="primary" loading={savingSecondary} onClick={handleSecondarySave}>
                  更新二级密码
                </Button>
              </div>
            </Form>
          </div>
        )}
      </div>
    </Modal>
  );
};

export default ProfileModal;
