import React, { useEffect, useState } from 'react';
import {
  Table, Card, Tag, Button, Space, Modal, Form, Input, Select, Typography, App, Segmented, Avatar,
  Upload, Alert, Row, Col,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined, DownloadOutlined, InboxOutlined,
} from '@ant-design/icons';
import {
  getUsers, createUser, updateUser, deleteUser, resetPassword, importUsers, downloadUserImportTemplate,
  setUserStatus,
} from '../../api/user';
import type { UserImportResult, UserVO } from '../../types';

const roleOptions = [
  { label: '全部', value: '' },
  { label: '学生', value: 'student' },
  { label: '教师', value: 'teacher' },
  { label: '管理员', value: 'admin' },
];

const roleTags: Record<string, { label: string; color: string }> = {
  admin: { label: '管理员', color: 'orange' },
  teacher: { label: '教师', color: 'green' },
  student: { label: '学生', color: 'blue' },
};

const UserManagement: React.FC = () => {
  const { message, modal } = App.useApp();
  const [data, setData] = useState<UserVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [filterRole, setFilterRole] = useState('');
  const [keyword, setKeyword] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<UserVO | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<UserImportResult | null>(null);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const res = await getUsers({ page, size: 15, role: filterRole || undefined, keyword: keyword || undefined });
      setData(res.data.data.records);
      setTotal(res.data.data.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page, filterRole, keyword]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (user: UserVO) => {
    setEditing(user);
    form.setFieldsValue(user);
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await updateUser(editing.id, values);
        message.success('更新成功');
      } else {
        await createUser(values);
        message.success('创建成功');
      }
      setModalOpen(false);
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const handleDelete = async (id: string) => {
    await deleteUser(id);
    message.success('删除成功');
    load();
  };

  const handleResetPwd = async (id: string) => {
    await resetPassword(id);
    message.success('密码已重置为 123456');
  };

  const handleToggleStatus = async (record: UserVO) => {
    const next = record.status === 0 ? 1 : 0;
    try {
      await setUserStatus(record.id, next);
      message.success(next === 1 ? '账号已启用' : '账号已禁用');
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const confirmToggleStatus = (record: UserVO) => {
    const disabling = record.status !== 0;
    modal.confirm({
      title: disabling ? '确认禁用该账号？' : '确认启用该账号？',
      content: disabling ? '禁用后该账号将无法登录。' : '启用后将解除锁定并清零登录失败次数。',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: disabling ? { danger: true } : undefined,
      onOk: () => handleToggleStatus(record),
    });
  };

  const confirmResetPwd = (id: string) => {
    modal.confirm({
      title: '确认重置密码为 123456？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      onOk: () => handleResetPwd(id),
    });
  };

  const confirmDelete = (id: string) => {
    modal.confirm({
      title: '确认删除？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDelete(id),
    });
  };

  const openImport = () => {
    setImportFile(null);
    setImportResult(null);
    setImportOpen(true);
  };

  const handleDownloadTemplate = async () => {
    try {
      const res = await downloadUserImportTemplate();
      const url = window.URL.createObjectURL(res.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'user-import-template.xlsx';
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (e: any) {
      message.error(e.response?.data?.message || '模板下载失败');
    }
  };

  const handleImport = async () => {
    if (!importFile) {
      message.warning('请先选择 Excel 文件');
      return;
    }
    setImporting(true);
    try {
      const res = await importUsers(importFile);
      setImportResult(res.data.data);
      message.success('导入完成');
      setPage(1);
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '导入失败');
    } finally {
      setImporting(false);
    }
  };

  const columns = [
    {
      title: '姓名',
      dataIndex: 'name',
      render: (name: string) => (
        <Space>
          <Avatar size="small" style={{ background: '#1677FF' }}>{name?.charAt(0)}</Avatar>
          {name}
        </Space>
      ),
    },
    { title: '学号/工号', dataIndex: 'loginId', width: 140 },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (r: string) => <Tag color={roleTags[r]?.color}>{roleTags[r]?.label}</Tag>,
    },
    { title: '学院', dataIndex: 'college', width: 150 },
    { title: '专业', dataIndex: 'major', width: 150 },
    { title: '班级', dataIndex: 'className', width: 120 },
    { title: '邮箱', dataIndex: 'email', width: 200, render: (v: string) => v || '-' },
    { title: '手机号', dataIndex: 'phone', width: 140, render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: number, record: UserVO) => {
        if (status === 0) {
          const locked = (record.failedAttempts ?? 0) >= 6;
          return <Tag color="red">{locked ? '已锁定' : '已禁用'}</Tag>;
        }
        return <Tag color="green">已启用</Tag>;
      },
    },
    {
      title: '操作',
      width: 280,
      render: (_: any, record: UserVO) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button type="link" size="small" onClick={() => confirmResetPwd(record.id)}>重置密码</Button>
          <Button
            type="link"
            size="small"
            danger={record.status !== 0}
            onClick={() => confirmToggleStatus(record)}
          >
            {record.status === 0 ? '启用' : '禁用'}
          </Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(record.id)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  const importErrorColumns = [
    { title: '行号', dataIndex: 'row', width: 80 },
    { title: '账号', dataIndex: 'loginId', width: 140, render: (v: string) => v || '-' },
    { title: '原因', dataIndex: 'reason' },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>用户管理</Typography.Title>
        <Space>
          <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>下载模板</Button>
          <Button icon={<UploadOutlined />} onClick={openImport}>批量导入</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>添加用户</Button>
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 16, marginBottom: 24, alignItems: 'center' }}>
        <Segmented
          options={roleOptions}
          value={filterRole}
          onChange={(v) => { setFilterRole(v as string); setPage(1); }}
        />
        <Input.Search
          placeholder="搜索姓名、学号/工号"
          style={{ width: 280 }}
          onSearch={(v) => { setKeyword(v); setPage(1); }}
          allowClear
        />
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          scroll={{ x: 'max-content' }}
          pagination={{
            current: page,
            total,
            pageSize: 15,
            onChange: setPage,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>

      <Modal
        title={editing ? '编辑用户' : '添加用户'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        width={720}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="loginId" label="学号/工号" rules={[{ required: true }]}>
                <Input placeholder="例：S2024001" disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="name" label="姓名" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="role" label="角色" rules={[{ required: true }]}>
                <Select options={[
                  { label: '学生', value: 'student' },
                  { label: '教师', value: 'teacher' },
                  { label: '管理员', value: 'admin' },
                ]} />
              </Form.Item>
            </Col>
            {!editing && (
              <Col span={12}>
                <Form.Item name="password" label="初始密码">
                  <Input.Password placeholder="留空则默认 123456" />
                </Form.Item>
              </Col>
            )}
            <Col span={12}>
              <Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="手机号"><Input /></Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="college" label="学院"><Input /></Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="major" label="专业"><Input /></Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="className" label="班级"><Input /></Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="grade" label="年级"><Input /></Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      <Modal
        title="批量导入用户"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        onOk={handleImport}
        okText="开始导入"
        confirmLoading={importing}
        width={720}
      >
        <div style={{ marginTop: 16 }}>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="请使用模板填写用户数据"
            description="支持 .xls/.xlsx。loginId、name、role 为必填；role 可填 student/teacher/admin 或 学生/教师/管理员；password 留空默认 123456。"
          />
          <Upload.Dragger
            accept=".xls,.xlsx"
            maxCount={1}
            beforeUpload={(file) => {
              setImportFile(file);
              setImportResult(null);
              return false;
            }}
            onRemove={() => {
              setImportFile(null);
              setImportResult(null);
            }}
            fileList={importFile ? [{
              uid: 'selected-file',
              name: importFile.name,
              status: 'done',
            }] : []}
          >
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">点击或拖拽 Excel 文件到此处</p>
            <p className="ant-upload-hint">导入不会覆盖已有账号，重复账号会跳过并出现在结果明细中。</p>
          </Upload.Dragger>

          {importResult && (
            <div style={{ marginTop: 20 }}>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                  gap: 12,
                  marginBottom: 16,
                }}
              >
                <div style={{ padding: 16, border: '1px solid #F0F0F0', borderRadius: 8, background: '#F6FFED' }}>
                  <div style={{ color: 'rgba(0,0,0,0.45)', marginBottom: 4 }}>成功导入</div>
                  <div style={{ fontSize: 24, fontWeight: 700, color: '#389E0D' }}>{importResult.successCount}</div>
                </div>
                <div style={{ padding: 16, border: '1px solid #F0F0F0', borderRadius: 8, background: '#FFFBE6' }}>
                  <div style={{ color: 'rgba(0,0,0,0.45)', marginBottom: 4 }}>跳过</div>
                  <div style={{ fontSize: 24, fontWeight: 700, color: '#D48806' }}>{importResult.skippedCount}</div>
                </div>
                <div style={{ padding: 16, border: '1px solid #F0F0F0', borderRadius: 8, background: '#FFF1F0' }}>
                  <div style={{ color: 'rgba(0,0,0,0.45)', marginBottom: 4 }}>失败</div>
                  <div style={{ fontSize: 24, fontWeight: 700, color: '#CF1322' }}>{importResult.failedCount}</div>
                </div>
              </div>

              {importResult.errors.length > 0 && (
                <Table
                  size="small"
                  columns={importErrorColumns}
                  dataSource={importResult.errors}
                  rowKey={(_, index) => `${index}`}
                  pagination={{ pageSize: 5 }}
                />
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
};

export default UserManagement;
