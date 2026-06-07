import React, { useEffect, useState } from 'react';
import { Button, Image, Spin, Typography } from 'antd';
import { ExportOutlined, FileUnknownOutlined } from '@ant-design/icons';
import type { AttachmentVO } from '../../types';
import { getAttachmentKind } from './attachmentUtils';
import { fetchAttachmentObjectUrl, openAttachmentInNewTab } from '../../api/attachment';

interface AttachmentViewerProps {
  attachment: AttachmentVO;
  /** 应用到最外层容器的样式（用于控制高度等） */
  style?: React.CSSProperties;
}

const baseContainer: React.CSSProperties = {
  width: '100%',
  height: '100%',
  minHeight: 240,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: '#F5F5F5',
  borderRadius: 10,
  overflow: 'hidden',
};

/**
 * 经鉴权接口（GET /api/attachments/{id}/file，axios 自动带 Token）把附件拉成同源 blob 地址，
 * 卸载/切换时释放。未登录无法访问该接口，因此文件链接不再能匿名查看。
 * 另外：后端对响应默认带 X-Frame-Options: DENY，直接 <iframe src=后端URL> 会被浏览器拦截，
 * 用 blob: 地址内嵌可稳定预览 PDF。
 */
const useAttachmentBlobUrl = (id: string, enabled: boolean) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    let objectUrl: string | null = null;
    setStatus('loading');
    setBlobUrl(null);

    fetchAttachmentObjectUrl(id)
      .then((url) => {
        if (cancelled) {
          URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setBlobUrl(url);
        setStatus('ready');
      })
      .catch(() => {
        if (!cancelled) setStatus('error');
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [id, enabled]);

  return { blobUrl, status };
};

/** 单个附件的内嵌预览内容：图片可缩放，PDF 内嵌阅读器，其它给出提示 */
const AttachmentViewer: React.FC<AttachmentViewerProps> = ({ attachment, style }) => {
  const kind = getAttachmentKind(attachment);
  const container: React.CSSProperties = { ...baseContainer, ...style };
  const { blobUrl, status } = useAttachmentBlobUrl(attachment.id, kind !== 'other');

  if (kind === 'other') {
    return (
      <div style={{ ...container, flexDirection: 'column', gap: 12, color: 'rgba(0,0,0,0.45)' }}>
        <FileUnknownOutlined style={{ fontSize: 48 }} />
        <Typography.Text type="secondary">该文件不支持预览，请点击「外部打开」查看</Typography.Text>
        <Button icon={<ExportOutlined />} onClick={() => openAttachmentInNewTab(attachment.id)}>
          外部打开
        </Button>
      </div>
    );
  }

  if (status === 'loading') {
    return (
      <div style={{ ...container, background: '#FFFFFF' }}>
        <Spin tip="正在加载…">
          <div style={{ width: 120, height: 80 }} />
        </Spin>
      </div>
    );
  }

  if (status === 'error' || !blobUrl) {
    return (
      <div style={{ ...container, flexDirection: 'column', gap: 12, color: 'rgba(0,0,0,0.45)' }}>
        <FileUnknownOutlined style={{ fontSize: 48 }} />
        <Typography.Text type="secondary">加载失败，可尝试外部打开</Typography.Text>
        <Button icon={<ExportOutlined />} onClick={() => openAttachmentInNewTab(attachment.id)}>
          外部打开
        </Button>
      </div>
    );
  }

  if (kind === 'image') {
    return (
      <div style={container}>
        <Image
          src={blobUrl}
          alt={attachment.fileName}
          style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
          wrapperStyle={{ maxWidth: '100%', maxHeight: '100%', display: 'flex' }}
        />
      </div>
    );
  }

  return (
    <div style={{ ...container, background: '#FFFFFF' }}>
      <iframe src={blobUrl} title={attachment.fileName} style={{ width: '100%', height: '100%', border: 'none' }} />
    </div>
  );
};

export default AttachmentViewer;
