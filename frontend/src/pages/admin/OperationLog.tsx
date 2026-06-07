import React, { useEffect, useState } from 'react';
import {
  Table, Card, Tag, Button, Space, Typography, App, Select, Input, DatePicker, Modal, Tooltip,
} from 'antd';
import { DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { getOperationLogs, deleteOperationLogs } from '../../api/operationLog';
import type { OperationLogVO } from '../../types';

const { RangePicker } = DatePicker;

const roleTags: Record<string, { label: string; color: string }> = {
  admin: { label: '管理员', color: 'orange' },
  teacher: { label: '教师', color: 'green' },
  student: { label: '学生', color: 'blue' },
};

const roleOptions = [
  { label: '全部角色', value: '' },
  { label: '管理员', value: 'admin' },
  { label: '教师', value: 'teacher' },
  { label: '学生', value: 'student' },
];

const moduleOptions = [
  '登录', '用户管理', '批次管理', '申报管理', '审核管理', '奖项库', '奖项分类',
  '基础获奖', '公告管理', '附件管理', '操作记录', '系统配置', '账号安全', '其他',
].map((m) => ({ label: m, value: m }));

const PAGE_SIZE = 15;

const OperationLog: React.FC = () => {
  const { message } = App.useApp();
  const [data, setData] = useState<OperationLogVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [role, setRole] = useState('');
  const [module, setModule] = useState<string | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [secondaryPassword, setSecondaryPassword] = useState('');
  const [deleting, setDeleting] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await getOperationLogs({
        page,
        size: PAGE_SIZE,
        role: role || undefined,
        module: module || undefined,
        keyword: keyword || undefined,
        startDate: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
        endDate: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
      });
      setData(res.data.data.records);
      setTotal(res.data.data.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page, role, module, keyword, range]);

  const openDelete = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先勾选要删除的记录');
      return;
    }
    setSecondaryPassword('');
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!secondaryPassword) {
      message.warning('请输入二级密码');
      return;
    }
    setDeleting(true);
    try {
      await deleteOperationLogs(selectedRowKeys as string[], secondaryPassword);
      message.success('删除成功');
      setDeleteOpen(false);
      setSelectedRowKeys([]);
      load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '删除失败');
    } finally {
      setDeleting(false);
    }
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => (v ? v.replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作人',
      width: 140,
      render: (_: any, r: OperationLogVO) => (
        <span>{r.operatorName || '-'}<br />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{r.operatorLoginId || ''}</Typography.Text>
        </span>
      ),
    },
    {
      title: '角色',
      dataIndex: 'operatorRole',
      width: 90,
      render: (r?: string) => (r && roleTags[r] ? <Tag color={roleTags[r].color}>{roleTags[r].label}</Tag> : '-'),
    },
    { title: '模块', dataIndex: 'module', width: 110, render: (v?: string) => v || '-' },
    { title: '动作', dataIndex: 'action', width: 130, render: (v?: string) => v || '-' },
    {
      title: '接口',
      width: 220,
      render: (_: any, r: OperationLogVO) => (
        <Tooltip title={`${r.method || ''} ${r.uri || ''}`}>
          <Typography.Text style={{ fontSize: 12 }} ellipsis>
            <Tag>{r.method}</Tag>{r.uri}
          </Typography.Text>
        </Tooltip>
      ),
    },
    { title: 'IP', dataIndex: 'ip', width: 130, render: (v?: string) => v || '-' },
    {
      title: '结果',
      dataIndex: 'success',
      width: 90,
      render: (s: number, r: OperationLogVO) =>
        s === 1
          ? <Tag color="green">成功</Tag>
          : <Tooltip title={r.errorMsg}><Tag color="red">失败</Tag></Tooltip>,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>操作记录</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
          <Button
            danger
            icon={<DeleteOutlined />}
            disabled={selectedRowKeys.length === 0}
            onClick={openDelete}
          >
            删除选中{selectedRowKeys.length ? `(${selectedRowKeys.length})` : ''}
          </Button>
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap', alignItems: 'center' }}>
        <Select
          options={roleOptions}
          value={role}
          style={{ width: 120 }}
          onChange={(v) => { setRole(v); setPage(1); }}
        />
        <Select
          allowClear
          placeholder="全部模块"
          options={moduleOptions}
          value={module}
          style={{ width: 140 }}
          onChange={(v) => { setModule(v); setPage(1); }}
        />
        <Input.Search
          placeholder="搜索操作人/账号/动作"
          style={{ width: 240 }}
          onSearch={(v) => { setKeyword(v); setPage(1); }}
          allowClear
        />
        <RangePicker
          onChange={(v) => { setRange(v as any); setPage(1); }}
        />
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
          pagination={{
            current: page,
            total,
            pageSize: PAGE_SIZE,
            onChange: setPage,
            showTotal: (t) => `共 ${t} 条`,
          }}
          scroll={{ x: 1100 }}
        />
      </Card>

      <Modal
        title="删除操作记录"
        open={deleteOpen}
        onCancel={() => setDeleteOpen(false)}
        onOk={handleDelete}
        okText="确认删除"
        okButtonProps={{ danger: true }}
        confirmLoading={deleting}
        centered
        width={420}
      >
        <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
          您将删除选中的 {selectedRowKeys.length} 条操作记录。此操作需要二级密码验证。
        </Typography.Paragraph>
        <Input.Password
          placeholder="请输入二级密码"
          value={secondaryPassword}
          onChange={(e) => setSecondaryPassword(e.target.value)}
          onPressEnter={handleDelete}
        />
      </Modal>
    </div>
  );
};

export default OperationLog;
