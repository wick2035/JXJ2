import React, { useEffect, useMemo, useState } from 'react';
import {
  App,
  Avatar,
  Badge,
  Button,
  Descriptions,
  Empty,
  Form,
  Input,
  Modal,
  Progress,
  Select,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  BellOutlined,
  CheckCircleOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { NoticeSaveRequest, NoticeVO, UserVO } from '../../types';
import {
  confirmMyNotice,
  createNotice,
  getAdminNotices,
  getMyNotice,
  getMyNotices,
  updateNotice,
  withdrawNotice,
} from '../../api/notice';
import { getUsers } from '../../api/user';
import { useAuthStore } from '../../store/authStore';

const statusMeta: Record<string, { label: string; color: string }> = {
  published: { label: '已发布', color: 'processing' },
  withdrawn: { label: '已撤回', color: 'default' },
};

const targetMeta: Record<string, string> = {
  all: '全体用户',
  specified: '指定用户',
};

const roleLabels: Record<string, string> = {
  admin: '管理员',
  teacher: '教师',
  student: '学生',
};

const formatTime = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN') : '-');

const isEditedNotice = (notice: NoticeVO) => {
  if (!notice.createdAt || !notice.updatedAt) return false;
  return Math.abs(new Date(notice.updatedAt).getTime() - new Date(notice.createdAt).getTime()) > 1000;
};

const noticeTime = (notice: NoticeVO) => new Date(notice.createdAt || 0).getTime();

const sortUnreadFirst = (records: NoticeVO[]) => [...records].sort((left, right) => {
  const leftUnread = left.confirmed ? 0 : 1;
  const rightUnread = right.confirmed ? 0 : 1;
  if (leftUnread !== rightUnread) {
    return rightUnread - leftUnread;
  }
  return noticeTime(right) - noticeTime(left);
});

const NoticeCenter: React.FC = () => {
  const { message, modal } = App.useApp();
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === 'admin';

  const [view, setView] = useState<'my' | 'admin'>('my');
  const [myNotices, setMyNotices] = useState<NoticeVO[]>([]);
  const [adminNotices, setAdminNotices] = useState<NoticeVO[]>([]);
  const [myTotal, setMyTotal] = useState(0);
  const [adminTotal, setAdminTotal] = useState(0);
  const [myPage, setMyPage] = useState(1);
  const [adminPage, setAdminPage] = useState(1);
  const [myLoading, setMyLoading] = useState(false);
  const [adminLoading, setAdminLoading] = useState(false);
  const [unconfirmedOnly, setUnconfirmedOnly] = useState(false);
  const [adminStatus, setAdminStatus] = useState<string>();
  const [detail, setDetail] = useState<NoticeVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<NoticeVO | null>(null);
  const [saving, setSaving] = useState(false);
  const [userOptions, setUserOptions] = useState<UserVO[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [form] = Form.useForm<NoticeSaveRequest>();
  const targetType = Form.useWatch('targetType', form);
  const isUnreadMyDetail = view === 'my' && !!detail && !detail.confirmed;

  const loadMy = async () => {
    setMyLoading(true);
    try {
      const res = await getMyNotices({ page: myPage, size: 10, unconfirmedOnly });
      setMyNotices(sortUnreadFirst(res.data.data.records));
      setMyTotal(res.data.data.total);
    } finally {
      setMyLoading(false);
    }
  };

  const loadAdmin = async () => {
    if (!isAdmin) return;
    setAdminLoading(true);
    try {
      const res = await getAdminNotices({ page: adminPage, size: 10, status: adminStatus });
      setAdminNotices(res.data.data.records);
      setAdminTotal(res.data.data.total);
    } finally {
      setAdminLoading(false);
    }
  };

  useEffect(() => {
    loadMy();
  }, [myPage, unconfirmedOnly]);

  useEffect(() => {
    loadAdmin();
  }, [adminPage, adminStatus, isAdmin]);

  const refreshNoticeCount = () => {
    window.dispatchEvent(new Event('notice-count-refresh'));
  };

  const searchUsers = async (keyword = '') => {
    if (!isAdmin) return;
    setUserLoading(true);
    try {
      const res = await getUsers({ page: 1, size: 30, keyword: keyword || undefined });
      setUserOptions(res.data.data.records);
    } finally {
      setUserLoading(false);
    }
  };

  const openDetail = async (record: NoticeVO) => {
    if (view === 'admin') {
      setDetail(record);
      setDetailOpen(true);
      return;
    }
    const res = await getMyNotice(record.id);
    setDetail(res.data.data);
    setDetailOpen(true);
  };

  const handleConfirm = async () => {
    if (!detail) return;
    await confirmMyNotice(detail.id);
    message.success('已确认阅读');
    setDetail({ ...detail, confirmed: 1, confirmedAt: new Date().toISOString() });
    setDetailOpen(false);
    await loadMy();
    refreshNoticeCount();
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ targetType: 'all', recipientUserIds: [] });
    setUserOptions([]);
    setEditorOpen(true);
  };

  const openEdit = (record: NoticeVO) => {
    setEditing(record);
    form.setFieldsValue({
      title: record.title,
      content: record.content,
      targetType: record.targetType,
      recipientUserIds: record.recipientUserIds || [],
    });
    setUserOptions((record.recipients || []).map((recipient) => ({
      id: recipient.id,
      loginId: recipient.loginId || '',
      name: recipient.name || '',
      role: recipient.role || 'student',
    })));
    setEditorOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: NoticeSaveRequest = {
      title: values.title.trim(),
      content: values.content.trim(),
      targetType: values.targetType,
      recipientUserIds: values.targetType === 'specified' ? values.recipientUserIds : [],
    };
    setSaving(true);
    try {
      if (editing) {
        await updateNotice(editing.id, payload);
        message.success('公告已更新');
      } else {
        await createNotice(payload);
        message.success('公告已发布');
      }
      setEditorOpen(false);
      await Promise.all([loadAdmin(), loadMy()]);
      refreshNoticeCount();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    } finally {
      setSaving(false);
    }
  };

  const handleWithdraw = async (id: string) => {
    await withdrawNotice(id);
    message.success('公告已撤回');
    await Promise.all([loadAdmin(), loadMy()]);
    refreshNoticeCount();
  };

  const confirmWithdraw = (id: string) => {
    modal.confirm({
      title: '确认撤回该公告？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleWithdraw(id),
    });
  };

  const userSelectOptions = useMemo(
    () => userOptions.map((item) => ({
      label: `${item.name} · ${item.loginId} · ${roleLabels[item.role] || item.role}`,
      value: item.id,
    })),
    [userOptions]
  );

  const myColumns = [
    {
      title: '公告',
      dataIndex: 'title',
      render: (_: string, record: NoticeVO) => (
        <Space size={12}>
          <Badge status={record.confirmed ? 'default' : 'processing'} />
          <div>
            <div style={{ fontWeight: 600 }}>{record.title}</div>
            <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
              {record.creatorName || '系统'} · {formatTime(record.createdAt)}
            </div>
          </div>
        </Space>
      ),
    },
    {
      title: '状态',
      width: 120,
      render: (_: unknown, record: NoticeVO) => (
        record.confirmed
          ? <Tag color="success">已确认</Tag>
          : <Tag color="warning">待确认</Tag>
      ),
    },
    {
      title: '范围',
      dataIndex: 'targetType',
      width: 120,
      render: (value: string) => targetMeta[value] || value,
    },
  ];

  const adminColumns = [
    {
      title: '公告',
      dataIndex: 'title',
      render: (_: string, record: NoticeVO) => (
        <div>
          <Space>
            <Typography.Text strong>{record.title}</Typography.Text>
            <Tag color={statusMeta[record.status]?.color}>{statusMeta[record.status]?.label}</Tag>
          </Space>
          <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12, marginTop: 4 }}>
            {targetMeta[record.targetType]} · 发布于 {formatTime(record.createdAt)}
          </div>
        </div>
      ),
    },
    {
      title: '确认进度',
      width: 220,
      render: (_: unknown, record: NoticeVO) => {
        const percent = record.recipientCount
          ? Math.round((record.confirmedCount / record.recipientCount) * 100)
          : 0;
        return (
          <Space direction="vertical" size={2} style={{ width: 180 }}>
            <Progress percent={percent} size="small" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {record.confirmedCount}/{record.recipientCount} 已确认
            </Typography.Text>
          </Space>
        );
      },
    },
    {
      title: '操作',
      width: 210,
      render: (_: unknown, record: NoticeVO) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} disabled={record.status === 'withdrawn'} onClick={(e) => {
            e.stopPropagation();
            openEdit(record);
          }}>
            编辑
          </Button>
          {record.status === 'published' && (
            <Button
              type="link"
              size="small"
              danger
              icon={<RollbackOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                confirmWithdraw(record.id);
              }}
            >
              撤回
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const renderDetail = () => {
    if (!detail) return <Empty description="未选择公告" />;
    const confirmedPercent = detail.recipientCount
      ? Math.round((detail.confirmedCount / detail.recipientCount) * 100)
      : 0;

    return (
      <Space direction="vertical" size={24} style={{ width: '100%' }}>
        <div>
          <Typography.Title
            level={2}
            style={{ margin: 0, lineHeight: 1.2, letterSpacing: 0, overflowWrap: 'anywhere' }}
          >
            {detail.title}
          </Typography.Title>
          <Space size={10} wrap style={{ marginTop: 10 }}>
            <Typography.Text type="secondary">
              {detail.creatorName || '系统'} · {formatTime(detail.updatedAt)}
              {isEditedNotice(detail) ? ' · 已编辑' : ''}
            </Typography.Text>
            {view === 'my' && (
              detail.confirmed ? <Tag color="success">已确认</Tag> : <Tag color="warning">待确认</Tag>
            )}
          </Space>
        </div>

        <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 15, lineHeight: 1.9, margin: 0 }}>
          {detail.content}
        </Typography.Paragraph>

        {(view === 'admin' || detail.withdrawnAt) && (
          <Descriptions bordered size="small" column={1}>
            {view === 'admin' && (
              <Descriptions.Item label="确认进度">
                {detail.confirmedCount}/{detail.recipientCount} 已确认
                <Progress percent={confirmedPercent} size="small" style={{ marginTop: 8 }} />
              </Descriptions.Item>
            )}
            {detail.withdrawnAt && <Descriptions.Item label="撤回时间">{formatTime(detail.withdrawnAt)}</Descriptions.Item>}
          </Descriptions>
        )}

        {view === 'admin' && detail.recipients && (
          <div>
            <Typography.Text strong>接收人</Typography.Text>
            <div style={{ display: 'grid', gap: 8, marginTop: 12 }}>
              {detail.recipients.map((recipient) => (
                <div
                  key={recipient.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 12px',
                    border: '1px solid #F0F0F0',
                    borderRadius: 8,
                  }}
                >
                  <Space>
                    <Avatar size="small" style={{ background: '#E6F4FF', color: '#1677FF' }}>
                      {recipient.name?.charAt(0)}
                    </Avatar>
                    <span>{recipient.name || recipient.loginId}</span>
                    <Typography.Text type="secondary">{roleLabels[recipient.role || '']}</Typography.Text>
                  </Space>
                  {recipient.confirmed
                    ? <Tag color="success">已确认</Tag>
                    : <Tag color="warning">待确认</Tag>}
                </div>
              ))}
            </div>
          </div>
        )}
      </Space>
    );
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Space>
          <BellOutlined style={{ fontSize: 20, color: '#1677FF' }} />
          <Typography.Title level={4} style={{ margin: 0 }}>通知公告</Typography.Title>
        </Space>
        <Space>
          {isAdmin && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>发布公告</Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={() => {
            loadMy();
            loadAdmin();
          }}>
            刷新
          </Button>
        </Space>
      </div>

      {isAdmin && (
        <Segmented
          value={view}
          onChange={(value) => setView(value as 'my' | 'admin')}
          options={[
            { label: '我的公告', value: 'my' },
            { label: '公告管理', value: 'admin' },
          ]}
          style={{ marginBottom: 20 }}
        />
      )}

      {view === 'my' && (
        <>
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <Select
              value={unconfirmedOnly ? 'unconfirmed' : 'all'}
              style={{ width: 160 }}
              onChange={(value) => {
                setUnconfirmedOnly(value === 'unconfirmed');
                setMyPage(1);
              }}
              options={[
                { label: '全部公告', value: 'all' },
                { label: '只看待确认', value: 'unconfirmed' },
              ]}
            />
          </div>
          <Table
            columns={myColumns}
            dataSource={myNotices}
            rowKey="id"
            loading={myLoading}
            onRow={(record) => ({ onClick: () => openDetail(record) })}
            pagination={{
              current: myPage,
              total: myTotal,
              pageSize: 10,
              onChange: setMyPage,
              showTotal: (total) => `共 ${total} 条`,
            }}
          />
        </>
      )}

      {view === 'admin' && (
        <>
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <Select
              allowClear
              placeholder="筛选状态"
              value={adminStatus}
              style={{ width: 160 }}
              onChange={(value) => {
                setAdminStatus(value);
                setAdminPage(1);
              }}
              options={[
                { label: '已发布', value: 'published' },
                { label: '已撤回', value: 'withdrawn' },
              ]}
            />
          </div>
          <Table
            columns={adminColumns}
            dataSource={adminNotices}
            rowKey="id"
            loading={adminLoading}
            onRow={(record) => ({ onClick: () => openDetail(record) })}
            pagination={{
              current: adminPage,
              total: adminTotal,
              pageSize: 10,
              onChange: setAdminPage,
              showTotal: (total) => `共 ${total} 条`,
            }}
          />
        </>
      )}

      <Modal
        title={null}
        width={560}
        centered
        destroyOnHidden
        open={detailOpen}
        closable={!isUnreadMyDetail}
        maskClosable={!isUnreadMyDetail}
        keyboard={!isUnreadMyDetail}
        zIndex={isUnreadMyDetail ? 3000 : 1800}
        onCancel={() => {
          if (!isUnreadMyDetail) {
            setDetailOpen(false);
          }
        }}
        footer={isUnreadMyDetail ? (
          <Button type="primary" icon={<CheckCircleOutlined />} onClick={handleConfirm}>
            确认已读
          </Button>
        ) : (
          <Button onClick={() => setDetailOpen(false)}>关闭</Button>
        )}
      >
        {renderDetail()}
      </Modal>

      <Modal
        title={editing ? '编辑公告' : '发布公告'}
        width={620}
        centered
        destroyOnHidden
        open={editorOpen}
        onCancel={() => setEditorOpen(false)}
        zIndex={1700}
        footer={[
          <Button key="cancel" onClick={() => setEditorOpen(false)}>取消</Button>,
          <Button key="save" type="primary" icon={<SendOutlined />} loading={saving} onClick={handleSave}>保存</Button>,
        ]}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="公告标题" rules={[{ required: true, message: '请输入公告标题' }]}>
            <Input maxLength={200} showCount placeholder="例：评优材料补充通知" />
          </Form.Item>
          <Form.Item name="targetType" label="接收范围" rules={[{ required: true }]}>
            <Segmented
              options={[
                { label: '全体用户', value: 'all' },
                { label: '指定用户', value: 'specified' },
              ]}
            />
          </Form.Item>
          {targetType === 'specified' && (
            <Form.Item
              name="recipientUserIds"
              label="接收用户"
              rules={[{ required: true, message: '请选择接收用户' }]}
            >
              <Select
                mode="multiple"
                showSearch
                filterOption={false}
                placeholder="搜索姓名、学号或工号"
                loading={userLoading}
                options={userSelectOptions}
                onSearch={searchUsers}
                onFocus={() => searchUsers()}
              />
            </Form.Item>
          )}
          <Form.Item name="content" label="公告内容" rules={[{ required: true, message: '请输入公告内容' }]}>
            <Input.TextArea rows={10} showCount maxLength={5000} placeholder="请输入需要通知的具体事项、时间要求和处理方式。" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default NoticeCenter;
