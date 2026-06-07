import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Avatar,
  Button,
  Empty,
  Input,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  AuditOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  ReloadOutlined,
  RetweetOutlined,
  SearchOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { generateBatchAssignments, getBatches } from '../../api/batch';
import { getAuditQueueStats, getFinishedAudits, getPendingAudits } from '../../api/audit';
import type { AuditQueueScope } from '../../api/audit';
import { useAuthStore } from '../../store/authStore';
import type { AuditQueueStatsVO, BatchVO, DeclarationVO, DeclarationStage } from '../../types';

type ReviewViewMode = 'pending' | 'finished';

const scopeLabels: Record<AuditQueueScope, string> = {
  mine: '我的记录',
  all: '全部记录',
  assigned: '已分配',
  unassigned: '未分配',
};

const stageMeta: Record<DeclarationStage, { label: string; color: string; tone: string }> = {
  pending_submit: { label: '待提交', color: 'default', tone: 'muted' },
  submitted_unassigned: { label: '未分配', color: 'warning', tone: 'warning' },
  assigned: { label: '待审核', color: 'processing', tone: 'ready' },
  reviewing: { label: '审核中', color: 'purple', tone: 'active' },
  approved: { label: '已通过', color: 'success', tone: 'done' },
  rejected: { label: '已驳回', color: 'error', tone: 'danger' },
};

const EMPTY_STATS: AuditQueueStatsVO = {
  totalSubmitted: 0,
  myPending: 0,
  assignedPending: 0,
  unassigned: 0,
  finishedTotal: 0,
  finishedMine: 0,
  finishedApproved: 0,
  finishedRejected: 0,
};

const formatDateTime = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN') : '-');

const getErrorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || fallback;
  }
  return fallback;
};

const ReviewQueue: React.FC = () => {
  const { message } = App.useApp();
  const [records, setRecords] = useState<DeclarationVO[]>([]);
  const [batches, setBatches] = useState<BatchVO[]>([]);
  const [stats, setStats] = useState<AuditQueueStatsVO>(EMPTY_STATS);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [searchParams] = useSearchParams();
  const [filterBatch, setFilterBatch] = useState<string | undefined>(() => searchParams.get('batchId') || undefined);
  const [searchText, setSearchText] = useState('');
  const [keyword, setKeyword] = useState('');
  const [scope, setScope] = useState<AuditQueueScope>('all');
  const [viewMode, setViewMode] = useState<ReviewViewMode>('pending');
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();

  const isAdmin = user?.role === 'admin';
  const effectiveScope: AuditQueueScope = isAdmin ? scope : 'mine';

  const scopeOptions = useMemo(() => {
    if (viewMode === 'finished') {
      return [
        { label: '全部已审', value: 'all' },
        { label: '我审核的', value: 'mine' },
      ];
    }
    return [
      { label: `全部待审 ${stats.totalSubmitted}`, value: 'all' },
      { label: `已分配 ${stats.assignedPending}`, value: 'assigned' },
      { label: `未分配 ${stats.unassigned}`, value: 'unassigned' },
    ];
  }, [stats, viewMode]);

  const metrics = useMemo<{ label: string; value: number; tone?: 'success' | 'warning' }[]>(() => {
    if (viewMode === 'finished') {
      if (!isAdmin) {
        return [
          { label: '我的已审', value: stats.finishedMine },
          { label: '我通过的', value: stats.finishedApproved, tone: 'success' as const },
          { label: '我驳回的', value: stats.finishedRejected, tone: 'warning' as const },
        ];
      }
      return [
        { label: '全部已审', value: stats.finishedTotal },
        { label: '我审核的', value: stats.finishedMine },
        { label: '已通过', value: stats.finishedApproved, tone: 'success' as const },
        { label: '已驳回', value: stats.finishedRejected, tone: 'warning' as const },
      ];
    }
    if (!isAdmin) {
      return [{ label: '我的待审', value: stats.myPending }];
    }
    return [
      { label: '全部待审', value: stats.totalSubmitted },
      { label: '我的待审', value: stats.myPending },
      { label: '已分配', value: stats.assignedPending },
      { label: '未分配', value: stats.unassigned, tone: 'warning' as const },
    ];
  }, [stats, viewMode, isAdmin]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const listRequest = viewMode === 'finished' ? getFinishedAudits : getPendingAudits;
      const [listRes, batchRes, statsRes] = await Promise.all([
        listRequest({ scope: effectiveScope, batchId: filterBatch, keyword, page: 1, size: 100 }),
        getBatches({ page: 1, size: 100 }),
        getAuditQueueStats({ batchId: filterBatch, keyword }),
      ]);
      setRecords(listRes.data.data.records);
      setBatches(batchRes.data.data.records);
      setStats({ ...EMPTY_STATS, ...statsRes.data.data });
    } finally {
      setLoading(false);
    }
  }, [effectiveScope, filterBatch, keyword, viewMode]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (viewMode === 'finished' && !['all', 'mine'].includes(scope)) {
      setScope('all');
    }
  }, [scope, viewMode]);

  const handleGenerateAssignments = async () => {
    if (!filterBatch) return;
    setGenerating(true);
    try {
      const res = await generateBatchAssignments(filterBatch, false);
      const result = res.data.data;
      message.success(`已处理 ${result.declarationCount} 份申报，生成 ${result.assignmentCount} 个审核任务`);
      await load();
    } catch (error: unknown) {
      message.error(getErrorMessage(error, '补分配失败'));
    } finally {
      setGenerating(false);
    }
  };

  const selectedBatch = batches.find((batch) => batch.id === filterBatch);

  return (
    <div className="review-workbench">
      <section className="review-hero">
        <div>
          <Typography.Title level={3} className="review-title">
            审核管理
          </Typography.Title>
          <Typography.Text className="review-subtitle">
            {viewMode === 'finished'
              ? '查看已完成审核的申报，并在需要时发起修正审核或手动调分。'
              : isAdmin
                ? '按任务状态拆分审核队列，及时发现未分配申报。'
                : '这里显示已分配给你的待审核任务。'}
          </Typography.Text>
        </div>
        <Space wrap>
          <Segmented
            value={viewMode}
            onChange={(value) => setViewMode(value as ReviewViewMode)}
            options={[
              { label: '待审核', value: 'pending' },
              { label: '已审核', value: 'finished' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            刷新
          </Button>
          {viewMode === 'pending' && isAdmin && effectiveScope === 'unassigned' && (
            <Tooltip title={filterBatch ? '为当前批次补齐审核任务' : '请先选择批次'}>
              <Button
                type="primary"
                icon={<RetweetOutlined />}
                disabled={!filterBatch}
                loading={generating}
                onClick={handleGenerateAssignments}
              >
                补分配
              </Button>
            </Tooltip>
          )}
        </Space>
      </section>

      <section className="review-metrics">
        {metrics.map((metric) => (
          <div
            key={metric.label}
            className={`review-metric${metric.tone ? ` is-${metric.tone}` : ''}`}
          >
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
          </div>
        ))}
      </section>

      <section className="review-toolbar">
        {isAdmin ? (
          <Segmented
            value={scope}
            onChange={(value) => setScope(value as AuditQueueScope)}
            options={scopeOptions}
          />
        ) : (
          <Tag icon={viewMode === 'finished' ? <CheckCircleOutlined /> : <AuditOutlined />} color="processing" className="review-scope-tag">
            {viewMode === 'finished' ? '我的已审记录' : `我的待审 ${stats.myPending}`}
          </Tag>
        )}
        <Select
          placeholder="筛选批次"
          allowClear
          className="review-batch-select"
          value={filterBatch}
          onChange={setFilterBatch}
          options={batches.map((batch) => ({ label: batch.name, value: batch.id }))}
        />
        <Input.Search
          allowClear
          enterButton={<SearchOutlined />}
          placeholder="搜索学生、学号或批次"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          onSearch={(value) => setKeyword(value.trim())}
          className="review-search"
        />
      </section>

      {selectedBatch && viewMode === 'pending' && effectiveScope === 'unassigned' && isAdmin && (
        <div className="review-alert">
          <WarningOutlined />
          <span>{selectedBatch.name} 存在未分配待审申报，可点击右上角“补分配”生成审核任务。</span>
        </div>
      )}

      {loading ? (
        <Spin size="large" className="review-loading" />
      ) : records.length === 0 ? (
        <div className="review-empty">
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={`${scopeLabels[effectiveScope]}暂无记录`}
          />
        </div>
      ) : (
        <div className="review-list">
          {records.map((decl) => {
            const stage = (decl.stage && stageMeta[decl.stage]) || stageMeta.assigned;
            return (
              <article key={decl.id} className={`review-row is-${stage.tone}`}>
                <div className="review-student">
                  <Avatar size={42} icon={<UserOutlined />} className="review-avatar">
                    {decl.studentName?.charAt(0)}
                  </Avatar>
                  <div>
                    <strong>{decl.studentName || '未知学生'}</strong>
                    <span>{decl.studentLoginId || '-'}</span>
                  </div>
                </div>

                <div className="review-main">
                  <div className="review-batch-line">
                    <FileSearchOutlined />
                    <Typography.Text ellipsis>{decl.batchName}</Typography.Text>
                  </div>
                  <div className="review-meta-line">
                    <span><CalendarOutlined /> {formatDateTime(decl.submittedAt)}</span>
                    <Tag color={stage.color}>{stage.label}</Tag>
                  </div>
                </div>

                <div className="review-score">
                  <span>申报总分</span>
                  <strong>{decl.totalScore?.toFixed(1) || '--'}</strong>
                </div>

                <div className="review-action">
                  <Button
                    type="primary"
                    icon={viewMode === 'finished' ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
                    onClick={() => navigate(`/audit/${decl.id}`, { state: { mode: viewMode } })}
                  >
                    {viewMode === 'finished' ? '修正审核' : '查看审核'}
                  </Button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default ReviewQueue;
