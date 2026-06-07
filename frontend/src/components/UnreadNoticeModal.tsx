import React, { useCallback, useEffect, useState } from 'react';
import { App, Button, Modal, Space, Spin, Tag, Typography } from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';
import { confirmMyNotice, getMyNotice, getMyNotices } from '../api/notice';
import type { NoticeVO } from '../types';

const formatTime = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN') : '-');

const isEditedNotice = (notice: NoticeVO) => {
  if (!notice.createdAt || !notice.updatedAt) return false;
  return Math.abs(new Date(notice.updatedAt).getTime() - new Date(notice.createdAt).getTime()) > 1000;
};

type UnreadNoticeModalProps = {
  userId?: string;
};

const UnreadNoticeModal: React.FC<UnreadNoticeModalProps> = ({ userId }) => {
  const { message } = App.useApp();
  const [notice, setNotice] = useState<NoticeVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const loadNextUnreadNotice = useCallback(async () => {
    if (!userId) {
      setNotice(null);
      return;
    }
    setLoading(true);
    try {
      const listRes = await getMyNotices({ page: 1, size: 1, unconfirmedOnly: true });
      const first = listRes.data.data.records[0];
      if (!first) {
        setNotice(null);
        return;
      }
      const detailRes = await getMyNotice(first.id);
      setNotice(detailRes.data.data);
    } catch {
      setNotice(null);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    loadNextUnreadNotice();
    window.addEventListener('notice-count-refresh', loadNextUnreadNotice);
    return () => window.removeEventListener('notice-count-refresh', loadNextUnreadNotice);
  }, [loadNextUnreadNotice]);

  const handleConfirm = async () => {
    if (!notice) return;
    setConfirming(true);
    try {
      await confirmMyNotice(notice.id);
      message.success('已确认阅读');
      setNotice(null);
      window.dispatchEvent(new Event('notice-count-refresh'));
      await loadNextUnreadNotice();
    } finally {
      setConfirming(false);
    }
  };

  return (
    <Modal
      title={null}
      width={620}
      centered
      open={!!notice || loading}
      closable={false}
      maskClosable={false}
      keyboard={false}
      zIndex={4000}
      footer={notice ? (
        <Button type="primary" icon={<CheckCircleOutlined />} loading={confirming} onClick={handleConfirm}>
          确认已读
        </Button>
      ) : null}
    >
      {loading && !notice ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
          <Spin />
        </div>
      ) : notice ? (
        <Space direction="vertical" size={24} style={{ width: '100%' }}>
          <div>
            <Typography.Title
              level={2}
              style={{ margin: 0, lineHeight: 1.2, letterSpacing: 0, overflowWrap: 'anywhere' }}
            >
              {notice.title}
            </Typography.Title>
            <Space size={10} wrap style={{ marginTop: 10 }}>
              <Typography.Text type="secondary">
                {notice.creatorName || '系统'} · {formatTime(notice.updatedAt)}
                {isEditedNotice(notice) ? ' · 已编辑' : ''}
              </Typography.Text>
              <Tag color="warning">待确认</Tag>
            </Space>
          </div>
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 15, lineHeight: 1.9, margin: 0 }}>
            {notice.content}
          </Typography.Paragraph>
        </Space>
      ) : null}
    </Modal>
  );
};

export default UnreadNoticeModal;
