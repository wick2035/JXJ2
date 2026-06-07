import React, { useEffect, useState } from 'react';
import { Button, Card, Divider, Space, Spin, Tag, Timeline, Typography } from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { getDeclaration } from '../../api/declaration';
import { STATUS_COLORS, STATUS_LABELS } from '../../types';
import type { DeclarationVO } from '../../types';
import { useCategories } from '../../hooks/useCategories';
import AttachmentList from '../../components/attachments/AttachmentList';

const DeclarationDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<DeclarationVO | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { categoryMap, getCategoryName, getCategoryColor } = useCategories();

  useEffect(() => {
    if (!id) return;
    getDeclaration(id)
      .then((res) => setData(res.data.data))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!data) return <div>未找到记录</div>;

  const actionIcons: Record<string, React.ReactNode> = {
    approve: <CheckCircleOutlined style={{ color: '#52C41A' }} />,
    reject: <CloseCircleOutlined style={{ color: '#FF4D4F' }} />,
    return: <ExclamationCircleOutlined style={{ color: '#FAAD14' }} />,
    correction_approve: <CheckCircleOutlined style={{ color: '#52C41A' }} />,
    correction_reject: <CloseCircleOutlined style={{ color: '#FF4D4F' }} />,
    correction_return: <ExclamationCircleOutlined style={{ color: '#FAAD14' }} />,
  };
  const actionLabels: Record<string, string> = {
    approve: '通过',
    reject: '驳回',
    return: '退回',
    correction_approve: '修正为通过',
    correction_reject: '修正为驳回',
    correction_return: '修正为退回',
  };
  const categoryCodes = Array.from(new Set([
    ...(data.items || []).map((item) => item.category),
    ...(data.basicItems || []).map((item) => item.category),
  ])).sort((a, b) => (categoryMap.get(a)?.sortOrder || 999) - (categoryMap.get(b)?.sortOrder || 999));
  const getRawScore = (category: string) =>
    data.categoryScores?.find((score) => score.category === category)?.rawScore
    || (data.items || []).filter((item) => item.category === category)
      .reduce((sum, item) => sum + (item.finalScore || 0), 0)
    + (data.basicItems || []).filter((item) => item.category === category)
      .reduce((sum, item) => sum + Number(item.finalScore ?? item.computedScore ?? 0), 0);

  return (
    <div>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} style={{ marginBottom: 16 }}>
        返回
      </Button>

      <Card style={{ borderRadius: 12, marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Typography.Title level={4} style={{ margin: 0 }}>{data.batchName}</Typography.Title>
            <Typography.Text type="secondary">{data.studentName} ({data.studentLoginId})</Typography.Text>
          </div>
          <Space size="large">
            <Tag color={STATUS_COLORS[data.status]} style={{ fontSize: 14, padding: '4px 12px' }}>
              {STATUS_LABELS[data.status]}
            </Tag>
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: '#1677FF' }}>{data.totalScore?.toFixed(1) || '--'}</div>
              <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>加权总分</div>
            </div>
          </Space>
        </div>

        <Divider />

        <div style={{ display: 'flex', gap: 48, flexWrap: 'wrap' }}>
          {categoryCodes.map((cat) => {
            const score = getRawScore(cat);
            return (
              <div key={cat} style={{ textAlign: 'center' }}>
                <div style={{ fontSize: 20, fontWeight: 600, color: getCategoryColor(cat) }}>{score?.toFixed(1) || '0.0'}</div>
                <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.45)' }}>{getCategoryName(cat)}</div>
              </div>
            );
          })}
        </div>
      </Card>

      {categoryCodes.map((cat) => {
        const catItems = (data.items || []).filter((item) => item.category === cat);
        const catBasicItems = (data.basicItems || []).filter((item) => item.category === cat);
        if (catItems.length === 0 && catBasicItems.length === 0) return null;
        return (
          <Card
            key={cat}
            style={{ borderRadius: 12, marginBottom: 16 }}
            title={(
              <Space>
                <div style={{ width: 4, height: 20, borderRadius: 2, background: getCategoryColor(cat) }} />
                {getCategoryName(cat)}
                <Tag>{catItems.length + catBasicItems.length} 项</Tag>
              </Space>
            )}
          >
            {catItems.map((item, idx) => (
              <div key={item.id} style={{
                padding: '12px 0',
                borderBottom: idx < catItems.length - 1 || catBasicItems.length > 0 ? '1px solid #F0F0F0' : 'none',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              }}>
                <div>
                  <Typography.Text strong>{item.awardName || item.customAwardName}</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 12 }}>
                    {item.levelName || item.customLevelName}
                    {item.useDowngrade ? ' (降级)' : ''}
                  </Typography.Text>
                  {item.description && (
                    <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.45)', marginTop: 2 }}>{item.description}</div>
                  )}
                  {item.attachments && item.attachments.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      <AttachmentList attachments={item.attachments} />
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 18, fontWeight: 600, color: '#1677FF' }}>
                  {item.finalScore?.toFixed(1) || '0.0'} 分
                </div>
              </div>
            ))}
            {catBasicItems.map((item, idx) => (
              <div key={item.awardId} style={{
                padding: '12px 0',
                borderBottom: idx < catBasicItems.length - 1 ? '1px solid #E6F4FF' : 'none',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              }}>
                <div>
                  <Typography.Text strong>{item.awardName}</Typography.Text>
                  <Tag color="blue" style={{ marginLeft: 8 }}>系统基础分</Tag>
                  <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.45)', marginTop: 2 }}>管理员导入，只读计分</div>
                </div>
                <div style={{ fontSize: 18, fontWeight: 600, color: '#1677FF' }}>
                  {Number(item.finalScore ?? item.computedScore ?? 0).toFixed(1)} 分
                </div>
              </div>
            ))}
          </Card>
        );
      })}

      {data.auditRecords && data.auditRecords.length > 0 && (
        <Card style={{ borderRadius: 12 }} title="审核记录">
          <Timeline items={data.auditRecords.map((record) => ({
            dot: actionIcons[record.action],
            children: (
              <div>
                <Typography.Text strong>{record.reviewerName}</Typography.Text>
                <Tag color={record.action.includes('approve') ? 'success' : record.action.includes('reject') ? 'error' : 'warning'} style={{ marginLeft: 8 }}>
                  {actionLabels[record.action]}
                </Tag>
                <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 13, marginLeft: 8 }}>
                  {new Date(record.createdAt).toLocaleString('zh-CN')}
                </span>
                {record.comment && <div style={{ marginTop: 4, color: 'rgba(0,0,0,0.65)' }}>{record.comment}</div>}
              </div>
            ),
          }))} />
        </Card>
      )}
    </div>
  );
};

export default DeclarationDetail;
