import React, { useState } from 'react';
import { Tooltip, Typography } from 'antd';
import { FileImageOutlined, FilePdfOutlined, FileUnknownOutlined } from '@ant-design/icons';
import type { AttachmentVO } from '../../types';
import { getAttachmentKind, getFileExtLabel, type AttachmentKind } from './attachmentUtils';

interface AttachmentCardProps {
  attachment: AttachmentVO;
  onOpen: () => void;
  size?: 'default' | 'large';
}

const KIND_STYLE: Record<AttachmentKind, { color: string; bg: string; icon: React.ReactNode }> = {
  image: { color: '#1677FF', bg: '#E6F4FF', icon: <FileImageOutlined /> },
  pdf: { color: '#FF4D4F', bg: '#FFF1F0', icon: <FilePdfOutlined /> },
  other: { color: '#8C8C8C', bg: '#F5F5F5', icon: <FileUnknownOutlined /> },
};

const AttachmentCard: React.FC<AttachmentCardProps> = ({ attachment, onOpen, size = 'default' }) => {
  const [hover, setHover] = useState(false);
  const kind = getAttachmentKind(attachment);
  const { color, bg, icon } = KIND_STYLE[kind];
  const isLarge = size === 'large';
  const iconBox = isLarge ? 48 : 40;

  return (
    <Tooltip title={attachment.fileName} mouseEnterDelay={0.4}>
      <div
        role="button"
        tabIndex={0}
        onClick={onOpen}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onOpen();
          }
        }}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          width: isLarge ? 260 : 220,
          maxWidth: '100%',
          padding: isLarge ? '12px 14px' : '10px 12px',
          borderRadius: 12,
          border: `1px solid ${hover ? color : '#F0F0F0'}`,
          background: '#FFFFFF',
          cursor: 'pointer',
          boxShadow: hover ? '0 6px 16px rgba(0,0,0,0.10)' : '0 1px 2px rgba(0,0,0,0.03)',
          transform: hover ? 'translateY(-2px)' : 'none',
          transition: 'all 0.2s ease',
        }}
      >
        <div
          style={{
            flexShrink: 0,
            width: iconBox,
            height: iconBox,
            borderRadius: 10,
            background: bg,
            color,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: isLarge ? 24 : 20,
          }}
        >
          {icon}
        </div>
        <div style={{ minWidth: 0, flex: 1 }}>
          <span
            style={{
              display: 'inline-block',
              fontSize: 11,
              fontWeight: 700,
              letterSpacing: 0.5,
              color,
              background: bg,
              padding: '1px 8px',
              borderRadius: 6,
              marginBottom: 4,
            }}
          >
            {getFileExtLabel(attachment.fileName)}
          </span>
          <Typography.Text
            ellipsis
            style={{ display: 'block', fontSize: isLarge ? 14 : 13, color: 'rgba(0,0,0,0.85)' }}
          >
            {attachment.fileName}
          </Typography.Text>
        </div>
      </div>
    </Tooltip>
  );
};

export default AttachmentCard;
