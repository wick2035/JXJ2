import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Avatar,
  Button,
  Card,
  Divider,
  Input,
  InputNumber,
  Segmented,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined,
  CheckOutlined,
  CloseOutlined,
  EditOutlined,
  ExportOutlined,
  LeftOutlined,
  ProfileOutlined,
  RightOutlined,
  RollbackOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { getDeclaration } from '../../api/declaration';
import { auditDeclaration, correctAuditDeclaration } from '../../api/audit';
import { STATUS_COLORS } from '../../types';
import type { AuditRecordVO, DeclarationItemVO, DeclarationVO } from '../../types';
import { useCategories } from '../../hooks/useCategories';
import { useAuthStore } from '../../store/authStore';
import AttachmentList from '../../components/attachments/AttachmentList';
import AttachmentViewer from '../../components/attachments/AttachmentViewer';
import { openAttachmentInNewTab } from '../../api/attachment';

const statusLabels: Record<string, string> = {
  draft: '草稿',
  submitted: '已提交',
  approved: '已通过',
  rejected: '已驳回',
  returned: '已退回',
};

const actionLabels: Record<string, string> = {
  approve: '通过',
  reject: '驳回',
  return: '退回重填',
  correction_approve: '修正为通过',
  correction_reject: '修正为驳回',
  correction_return: '修正为退回',
};

const getErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || fallback;
  }
  return fallback;
};

const formatDateTime = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN') : '-');

const parseSnapshot = (record: AuditRecordVO) => {
  if (!record.snapshotScores) return null;
  try {
    return JSON.parse(record.snapshotScores) as {
      before?: { total?: number; morality?: number; ability?: number; sports?: number };
      after?: { total?: number; morality?: number; ability?: number; sports?: number };
      total?: number;
      morality?: number;
      ability?: number;
      sports?: number;
    };
  } catch {
    return null;
  }
};

const scoreText = (value?: number) => (typeof value === 'number' ? value.toFixed(1) : '--');

type ViewMode = 'list' | 'item';

const ReviewDetail: React.FC = () => {
  const { message, modal } = App.useApp();
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const [data, setData] = useState<DeclarationVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [scoreDraft, setScoreDraft] = useState<Record<string, number>>({});
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [activeItemIndex, setActiveItemIndex] = useState(0);
  const [activeAttIndex, setActiveAttIndex] = useState(0);
  const navigate = useNavigate();
  const { categoryMap, getCategoryName, getCategoryColor } = useCategories();
  const user = useAuthStore((s) => s.user);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const res = await getDeclaration(id);
      const declaration = res.data.data;
      setData(declaration);
      setScoreDraft(Object.fromEntries((declaration.items || []).map((item) => [item.id, item.finalScore || 0])));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // 切换审阅项时，附件预览索引重置到第一张
  useEffect(() => {
    setActiveAttIndex(0);
  }, [activeItemIndex]);

  const isCorrectionMode = useMemo(() => {
    const stateMode = (location.state as { mode?: string } | null)?.mode;
    const hasMyAuditRecord = !!data?.auditRecords?.some((record) => record.reviewerId === user?.id);
    return stateMode === 'finished' || hasMyAuditRecord || (data ? data.status !== 'submitted' : false);
  }, [data, location.state, user?.id]);

  const handleAudit = async (action: 'approve' | 'reject' | 'return') => {
    if (!id || !data) return;
    const labels = { approve: '通过', reject: '驳回', return: '退回重填' };
    const itemScoreAdjustments = (data.items || []).map((item) => ({
      itemId: item.id,
      finalScore: Number(scoreDraft[item.id] ?? item.finalScore ?? 0),
    }));
    modal.confirm({
      title: `确认${labels[action]}该申报？`,
      content: comment ? `审核意见：${comment}` : undefined,
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: action !== 'approve' },
      onOk: async () => {
        setSubmitting(true);
        try {
          await auditDeclaration(id, { action, comment, itemScoreAdjustments });
          message.success(`已${labels[action]}`);
          navigate('/audit');
        } catch (error: unknown) {
          message.error(getErrorMessage(error, '操作失败'));
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  const handleCorrection = async (action: 'approve' | 'reject' | 'return') => {
    if (!id || !data) return;
    const labels = { approve: '修正为通过', reject: '修正为驳回', return: '修正为退回' };
    const itemScoreAdjustments = (data.items || []).map((item) => ({
      itemId: item.id,
      finalScore: Number(scoreDraft[item.id] ?? 0),
    }));
    modal.confirm({
      title: `${labels[action]}？`,
      content: comment ? `修正意见：${comment}` : undefined,
      centered: true,
      okText: '提交修正',
      cancelText: '取消',
      okButtonProps: { danger: action !== 'approve' },
      onOk: async () => {
        setSubmitting(true);
        try {
          await correctAuditDeclaration(id, { action, comment, itemScoreAdjustments });
          message.success('修正审核已记录');
          setComment('');
          await load();
        } catch (error: unknown) {
          message.error(getErrorMessage(error, '修正失败'));
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!data) return <div>未找到记录</div>;

  const categoryCodes = Array.from(new Set([
    ...(data.items || []).map((item) => item.category),
    ...(data.basicItems || []).map((item) => item.category),
  ]))
    .sort((a, b) => (categoryMap.get(a)?.sortOrder || 999) - (categoryMap.get(b)?.sortOrder || 999));
  const getRawScore = (category: string) =>
    (data.items || []).filter((item) => item.category === category)
      .reduce((sum, item) => sum + Number(scoreDraft[item.id] ?? item.finalScore ?? 0), 0)
      + (data.basicItems || []).filter((item) => item.category === category)
        .reduce((sum, item) => sum + Number(item.finalScore ?? item.computedScore ?? 0), 0);

  // 按「分类排序 → 项内顺序」拍平所有明细，用于逐项审阅
  const flatItems = categoryCodes.flatMap((cat) =>
    (data.items || []).filter((item) => item.category === cat).map((item) => ({ item, category: cat })));
  const safeIndex = Math.min(activeItemIndex, Math.max(0, flatItems.length - 1));

  const renderItemTitle = (item: DeclarationItemVO, large = false) => (
    <Typography.Text strong style={{ fontSize: large ? 22 : 15, lineHeight: 1.35 }}>
      {item.awardName || item.customAwardName}
      {!item.awardId && (
        <Tag color="orange" style={{ marginLeft: 8, fontSize: large ? 12 : 11 }}>自定义</Tag>
      )}
    </Typography.Text>
  );

  const renderItemMeta = (item: DeclarationItemVO, large = false) => (
    <>
      <div style={{ fontSize: large ? 16 : 13, color: large ? 'rgba(0,0,0,0.65)' : 'rgba(0,0,0,0.45)', marginTop: large ? 10 : 4 }}>
        级别：{item.levelName || item.customLevelName || '-'}
        {item.useDowngrade ? ' | 降级计分' : ''}
      </div>
      {item.description && (
        <div style={{ fontSize: large ? 14 : 13, color: 'rgba(0,0,0,0.55)', marginTop: large ? 8 : 2 }}>{item.description}</div>
      )}
    </>
  );

  // 首审与修正模式均可编辑评分；首审走 handleAudit、修正走 handleCorrection 持久化
  const renderScoreControl = (item: DeclarationItemVO, large = false) => (
    <InputNumber
      min={0}
      precision={1}
      size={large ? 'large' : 'middle'}
      value={scoreDraft[item.id]}
      onChange={(value) => setScoreDraft((prev) => ({ ...prev, [item.id]: Number(value || 0) }))}
      addonAfter="分"
      style={{ width: large ? 200 : 140 }}
    />
  );

  const renderListView = () =>
    categoryCodes.map((cat) => {
      const catItems = (data.items || []).filter((item) => item.category === cat);
      const catBasicItems = (data.basicItems || []).filter((item) => item.category === cat);
      if (catItems.length === 0 && catBasicItems.length === 0) return null;

      return (
        <div key={cat} style={{ marginBottom: 32 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
            <div style={{ width: 4, height: 24, borderRadius: 2, background: getCategoryColor(cat) }} />
            <Typography.Text strong style={{ fontSize: 18 }}>{getCategoryName(cat)}</Typography.Text>
            <Typography.Text type="secondary" style={{ marginLeft: 'auto' }}>
              小计：{getRawScore(cat).toFixed(1)} 分
            </Typography.Text>
          </div>

          {catItems.map((item) => (
            <Card key={item.id} style={{ borderRadius: 10, marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 20, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <div style={{ flex: '1 1 220px', minWidth: 180 }}>
                  {renderItemTitle(item)}
                  {renderItemMeta(item)}
                </div>
                <div style={{ flex: '2 1 320px', minWidth: 0 }}>
                  <AttachmentList attachments={item.attachments} emptyText="无附件" />
                </div>
                <div style={{ flexShrink: 0, minWidth: 100 }}>
                  {renderScoreControl(item)}
                </div>
              </div>
            </Card>
          ))}
          {catBasicItems.map((item) => (
            <Card key={item.awardId} style={{ borderRadius: 10, marginBottom: 12, background: '#F8FCFF', borderColor: '#D6E4FF' }} styles={{ body: { padding: 16 } }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 20, alignItems: 'center', flexWrap: 'wrap' }}>
                <div style={{ flex: '1 1 220px', minWidth: 180 }}>
                  <Typography.Text strong>{item.awardName}</Typography.Text>
                  <Tag color="blue" style={{ marginLeft: 8 }}>系统基础分</Tag>
                  <div style={{ fontSize: 13, color: 'rgba(0,0,0,0.45)', marginTop: 4 }}>管理员导入，只读计分</div>
                </div>
                <InputNumber
                  value={Number(item.finalScore ?? item.computedScore ?? 0)}
                  disabled
                  precision={1}
                  addonAfter="分"
                  style={{ width: 140 }}
                />
              </div>
            </Card>
          ))}
        </div>
      );
    });

  const renderItemView = () => {
    if (flatItems.length === 0) {
      return (
        <Card style={{ borderRadius: 12, marginBottom: 24, textAlign: 'center', padding: 48 }}>
          <Typography.Text type="secondary">暂无申报明细</Typography.Text>
        </Card>
      );
    }

    const { item, category } = flatItems[safeIndex];
    const atts = item.attachments || [];
    const safeAttIndex = Math.min(activeAttIndex, Math.max(0, atts.length - 1));
    const activeAtt = atts[safeAttIndex];

    return (
      <Card style={{ borderRadius: 12, marginBottom: 24 }} styles={{ body: { padding: 0 } }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, padding: '14px 20px', borderBottom: '1px solid #F0F0F0' }}>
          <Button icon={<LeftOutlined />} disabled={safeIndex === 0} onClick={() => setActiveItemIndex(safeIndex - 1)}>
            上一项
          </Button>
          <Space size={8} style={{ justifyContent: 'center', minWidth: 0 }}>
            <div style={{ width: 4, height: 18, borderRadius: 2, background: getCategoryColor(category) }} />
            <Typography.Text type="secondary" style={{ fontSize: 15 }}>
              第 {safeIndex + 1} / {flatItems.length} 项 · {getCategoryName(category)}
            </Typography.Text>
          </Space>
          <Button disabled={safeIndex === flatItems.length - 1} onClick={() => setActiveItemIndex(safeIndex + 1)}>
            下一项 <RightOutlined />
          </Button>
        </div>

        <div style={{ display: 'flex', minHeight: '60vh', flexWrap: 'wrap' }}>
          <div style={{ flex: '1 1 480px', minWidth: 0, padding: 20, display: 'flex', flexDirection: 'column', gap: 12, borderRight: '1px solid #F0F0F0' }}>
            {atts.length > 0 ? (
              <>
                <div style={{ flex: 1, minHeight: 320 }}>
                  <AttachmentViewer attachment={activeAtt} style={{ height: '100%' }} />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                  <Space wrap size={6}>
                    {atts.map((att, idx) => (
                      <Button
                        key={att.id}
                        size="small"
                        type={idx === safeAttIndex ? 'primary' : 'default'}
                        onClick={() => setActiveAttIndex(idx)}
                        title={att.fileName}
                        style={{ maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      >
                        {att.fileName}
                      </Button>
                    ))}
                  </Space>
                  <Button
                    icon={<ExportOutlined />}
                    onClick={() => openAttachmentInNewTab(activeAtt.id)}
                  >
                    外部打开
                  </Button>
                </div>
              </>
            ) : (
              <div style={{ flex: 1, minHeight: 320, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#FAFAFA', borderRadius: 10, color: 'rgba(0,0,0,0.45)' }}>
                该明细未上传附件
              </div>
            )}
          </div>

          <div style={{ flex: '0 1 360px', minWidth: 300, padding: 28, display: 'flex', flexDirection: 'column', gap: 18 }}>
            <div>
              {renderItemTitle(item, true)}
              {renderItemMeta(item, true)}
            </div>
            <Divider style={{ margin: '4px 0' }} />
            <div>
              <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 10, fontSize: 15 }}>
                审核评分
              </Typography.Text>
              {renderScoreControl(item, true)}
            </div>
          </div>
        </div>
      </Card>
    );
  };

  return (
    <div>
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} style={{ marginBottom: 16 }}>
        返回
      </Button>

      <Card style={{ borderRadius: 12, marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <Avatar size={56} style={{ background: '#E6F4FF', color: '#1677FF', fontSize: 22 }}>
              {data.studentName?.charAt(0)}
            </Avatar>
            <div>
              <Typography.Title level={4} style={{ margin: 0 }}>{data.studentName}</Typography.Title>
              <Typography.Text type="secondary">{data.studentLoginId} | {data.batchName}</Typography.Text>
            </div>
          </div>
          <Space size="large">
            <div style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: '#1677FF' }}>{scoreText(data.totalScore)}</div>
              <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>加权总分</div>
            </div>
            <Tag color={STATUS_COLORS[data.status]} style={{ fontSize: 14, padding: '4px 16px' }}>
              {statusLabels[data.status]}
            </Tag>
          </Space>
        </div>
      </Card>

      {isCorrectionMode ? (
        <Alert
          showIcon
          type="info"
          message="已审核记录"
          description="可修正审核结论，并对申报明细分进行手动调整；所有操作会写入审核记录。"
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      ) : (
        <Alert
          showIcon
          type="info"
          message="待审核"
          description="核对申报材料后选择通过、驳回或退回重填。"
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 20 }}>
        <Segmented
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          options={[
            { label: '列表查看', value: 'list', icon: <UnorderedListOutlined /> },
            { label: '逐项查看', value: 'item', icon: <ProfileOutlined /> },
          ]}
        />
      </div>

      {viewMode === 'list' ? renderListView() : renderItemView()}

      {data.auditRecords && data.auditRecords.length > 0 && (
        <Card style={{ borderRadius: 12, marginBottom: 24 }}>
          <Typography.Title level={5} style={{ marginTop: 0 }}>审核记录</Typography.Title>
          <Timeline
            items={data.auditRecords.map((record) => {
              const snapshot = parseSnapshot(record);
              const before = snapshot?.before;
              const after = snapshot?.after;
              return {
                children: (
                  <div>
                    <Space wrap>
                      <Tag color={record.action.startsWith('correction') ? 'purple' : 'blue'}>
                        {actionLabels[record.action] || record.action}
                      </Tag>
                      <Typography.Text strong>{record.reviewerName}</Typography.Text>
                      <Typography.Text type="secondary">{formatDateTime(record.createdAt)}</Typography.Text>
                    </Space>
                    {record.comment && <div style={{ marginTop: 6 }}>{record.comment}</div>}
                    {before && after && (
                      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 6 }}>
                        总分 {scoreText(before.total)} → {scoreText(after.total)}
                      </Typography.Text>
                    )}
                  </div>
                ),
              };
            })}
          />
        </Card>
      )}

      <Card
        style={{
          position: 'sticky',
          bottom: 0,
          zIndex: 10,
          borderRadius: '12px 12px 0 0',
          boxShadow: '0 -4px 12px rgba(0,0,0,0.06)',
        }}
        styles={{ body: { padding: '16px 24px' } }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
          <Input.TextArea
            placeholder={isCorrectionMode ? '修正意见（选填）' : '审核意见（选填）'}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            autoSize={{ minRows: 1, maxRows: 3 }}
            style={{ flex: 1, minWidth: 280, maxWidth: 560 }}
          />
          <Space wrap>
            {isCorrectionMode && (
              <Typography.Text type="secondary">
                <EditOutlined /> 修正审核
              </Typography.Text>
            )}
            <Divider type="vertical" />
            <Button
              size="large"
              danger
              icon={<RollbackOutlined />}
              onClick={() => (isCorrectionMode ? handleCorrection('return') : handleAudit('return'))}
              loading={submitting}
            >
              退回重填
            </Button>
            <Button
              size="large"
              danger
              icon={<CloseOutlined />}
              onClick={() => (isCorrectionMode ? handleCorrection('reject') : handleAudit('reject'))}
              loading={submitting}
            >
              驳回
            </Button>
            <Button
              size="large"
              type="primary"
              icon={<CheckOutlined />}
              onClick={() => (isCorrectionMode ? handleCorrection('approve') : handleAudit('approve'))}
              loading={submitting}
            >
              通过
            </Button>
          </Space>
        </div>
      </Card>
    </div>
  );
};

export default ReviewDetail;
