import React, { useState } from 'react';
import { Typography } from 'antd';
import type { AttachmentVO } from '../../types';
import AttachmentCard from './AttachmentCard';
import AttachmentPreviewModal from './AttachmentPreviewModal';

interface AttachmentListProps {
  attachments?: AttachmentVO[];
  size?: 'default' | 'large';
  /** 无附件时显示的占位文案，不传则不渲染任何内容 */
  emptyText?: string;
}

/** 即插即用的附件列表：类型图标卡片 + 内嵌预览弹窗（自带状态） */
const AttachmentList: React.FC<AttachmentListProps> = ({ attachments, size, emptyText }) => {
  const [previewIndex, setPreviewIndex] = useState<number | null>(null);
  const list = attachments ?? [];

  if (list.length === 0) {
    return emptyText ? <Typography.Text type="secondary">{emptyText}</Typography.Text> : null;
  }

  return (
    <>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        {list.map((att, idx) => (
          <AttachmentCard key={att.id} attachment={att} size={size} onOpen={() => setPreviewIndex(idx)} />
        ))}
      </div>
      <AttachmentPreviewModal
        attachments={list}
        activeIndex={previewIndex ?? 0}
        open={previewIndex !== null}
        onClose={() => setPreviewIndex(null)}
        onChangeIndex={(index) => setPreviewIndex(index)}
      />
    </>
  );
};

export default AttachmentList;
