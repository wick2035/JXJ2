import React, { useEffect } from 'react';
import { Button, Modal, Space, Typography } from 'antd';
import { ExportOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import type { AttachmentVO } from '../../types';
import AttachmentViewer from './AttachmentViewer';
import { getFileExtLabel } from './attachmentUtils';
import { openAttachmentInNewTab } from '../../api/attachment';

interface AttachmentPreviewModalProps {
  attachments: AttachmentVO[];
  activeIndex: number;
  open: boolean;
  onClose: () => void;
  onChangeIndex: (index: number) => void;
}

const AttachmentPreviewModal: React.FC<AttachmentPreviewModalProps> = ({
  attachments,
  activeIndex,
  open,
  onClose,
  onChangeIndex,
}) => {
  const total = attachments.length;
  const current = attachments[activeIndex];
  const hasMultiple = total > 1;

  const goPrev = () => onChangeIndex((activeIndex - 1 + total) % total);
  const goNext = () => onChangeIndex((activeIndex + 1) % total);

  useEffect(() => {
    if (!open || !hasMultiple) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, hasMultiple, activeIndex, total]);

  if (!current) return null;

  const openExternal = () => openAttachmentInNewTab(current.id);

  const arrowStyle: React.CSSProperties = {
    position: 'absolute',
    top: '50%',
    transform: 'translateY(-50%)',
    zIndex: 5,
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      width="82vw"
      destroyOnHidden
      styles={{ body: { height: '78vh', display: 'flex', flexDirection: 'column', padding: 16 } }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingRight: 40 }}>
          <span
            style={{
              fontSize: 11,
              fontWeight: 700,
              color: '#1677FF',
              background: '#E6F4FF',
              padding: '1px 8px',
              borderRadius: 6,
            }}
          >
            {getFileExtLabel(current.fileName)}
          </span>
          <Typography.Text strong ellipsis style={{ flex: 1, minWidth: 0 }}>
            {current.fileName}
          </Typography.Text>
          {hasMultiple && (
            <Typography.Text type="secondary" style={{ flexShrink: 0 }}>
              {activeIndex + 1} / {total}
            </Typography.Text>
          )}
          <Button icon={<ExportOutlined />} onClick={openExternal} style={{ flexShrink: 0 }}>
            外部打开
          </Button>
        </div>
      }
    >
      <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
        {hasMultiple && (
          <>
            <Button shape="circle" icon={<LeftOutlined />} onClick={goPrev} style={{ ...arrowStyle, left: 12 }} />
            <Button shape="circle" icon={<RightOutlined />} onClick={goNext} style={{ ...arrowStyle, right: 12 }} />
          </>
        )}
        <AttachmentViewer attachment={current} style={{ height: '100%' }} />
      </div>

      {hasMultiple && (
        <div style={{ marginTop: 12, display: 'flex', justifyContent: 'center' }}>
          <Space wrap size={6}>
            {attachments.map((att, idx) => (
              <Button
                key={att.id}
                size="small"
                type={idx === activeIndex ? 'primary' : 'default'}
                onClick={() => onChangeIndex(idx)}
                style={{ maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis' }}
              >
                {att.fileName}
              </Button>
            ))}
          </Space>
        </div>
      )}
    </Modal>
  );
};

export default AttachmentPreviewModal;
