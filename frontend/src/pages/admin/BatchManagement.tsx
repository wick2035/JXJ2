import React, { useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Radio,
  Row,
  Col,
  Select,
  Slider,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  RetweetOutlined,
  StopOutlined,
  TeamOutlined,
  UploadOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  createBatch,
  deleteBatch,
  exportBatch,
  generateBatchAssignments,
  getBatchAssignments,
  getBatchBasicAwards,
  getBatchEvaluationTable,
  getBatchRanking,
  getBatches,
  getBatchStats,
  importBatchBasicAwards,
  recalculateBatch,
  reopenBatch,
  updateBatch,
  updateBatchStatus,
} from '../../api/batch';
import { getClassOptions, getUsers } from '../../api/user';
import { getDeclarations, deleteDeclaration } from '../../api/declaration';
import { useNavigate } from 'react-router-dom';
import {
  STATUS_COLORS,
  STATUS_LABELS,
} from '../../types';
import type {
  AuditAssignmentVO,
  BasicAwardImportResult,
  BatchBasicAwardVO,
  BatchEvaluationTableVO,
  BatchRankingVO,
  BatchStatsVO,
  BatchVO,
  ClassOptionVO,
  DeclarationVO,
  UserVO,
} from '../../types';
import { useCategories } from '../../hooks/useCategories';

const assignmentStatusLabels: Record<string, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已驳回',
  returned: '已退回',
  cancelled: '已取消',
};

const assignmentStatusColors: Record<string, string> = {
  pending: 'processing',
  approved: 'success',
  rejected: 'error',
  returned: 'warning',
  cancelled: 'default',
};

const classComboValue = (grade: string | undefined | null, className: string) => `${grade ?? ''}||${className}`;

const splitClassCombo = (value: string): { grade?: string; className: string } => {
  const idx = value.indexOf('||');
  const grade = value.slice(0, idx);
  const className = value.slice(idx + 2);
  return { grade: grade || undefined, className };
};

const classOptionLabel = (option: ClassOptionVO) => {
  let label = option.grade ? `${option.grade} ${option.className}` : option.className;
  if (option.major) label += ` · ${option.major}`;
  if (option.studentCount != null) label += `（${option.studentCount}人）`;
  return label;
};

const BatchManagement: React.FC = () => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [data, setData] = useState<BatchVO[]>([]);
  const [declarations, setDeclarations] = useState<DeclarationVO[]>([]);
  const [teachers, setTeachers] = useState<UserVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reopenOpen, setReopenOpen] = useState(false);
  const [specifiedAssignmentOpen, setSpecifiedAssignmentOpen] = useState(false);
  const [specifiedAssignmentSubmitting, setSpecifiedAssignmentSubmitting] = useState(false);
  const [editing, setEditing] = useState<BatchVO | null>(null);
  const [activeBatch, setActiveBatch] = useState<BatchVO | null>(null);
  const [stats, setStats] = useState<BatchStatsVO | null>(null);
  const [ranking, setRanking] = useState<BatchRankingVO[]>([]);
  const [evaluationTable, setEvaluationTable] = useState<BatchEvaluationTableVO | null>(null);
  const [assignments, setAssignments] = useState<AuditAssignmentVO[]>([]);
  const [basicAwards, setBasicAwards] = useState<BatchBasicAwardVO[]>([]);
  const [basicImportCategory, setBasicImportCategory] = useState<string>();
  const [basicImportFile, setBasicImportFile] = useState<File | null>(null);
  const [basicImportResult, setBasicImportResult] = useState<BasicAwardImportResult | null>(null);
  const [basicImporting, setBasicImporting] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [form] = Form.useForm();
  const [reopenForm] = Form.useForm();
  const [specifiedAssignmentForm] = Form.useForm();
  const { categories: categoryMetas, categoryMap, getCategoryName, getCategoryColor } = useCategories();
  const [configuredCategoryCodes, setConfiguredCategoryCodes] = useState<string[]>([]);
  const [weights, setWeights] = useState<Record<string, number>>({});
  const [caps, setCaps] = useState<Record<string, number | undefined>>({});
  const [classOptions, setClassOptions] = useState<ClassOptionVO[]>([]);
  const [targetType, setTargetType] = useState<'all' | 'specified'>('all');
  const [targetClasses, setTargetClasses] = useState<string[]>([]);

  const weightTotal = useMemo(
    () => configuredCategoryCodes.reduce((sum, code) => sum + (weights[code] || 0), 0),
    [configuredCategoryCodes, weights]
  );

  const buildDefaultWeights = () => {
    if (categoryMetas.length === 0) return {};
    const base = Math.floor(100 / categoryMetas.length);
    const remainder = 100 - base * categoryMetas.length;
    return Object.fromEntries(categoryMetas.map((category, index) => [
      category.code,
      base + (index === 0 ? remainder : 0),
    ]));
  };

  const load = async () => {
    setLoading(true);
    try {
      const [batchRes, teacherRes, classRes] = await Promise.all([
        getBatches({ page: 1, size: 100 }),
        getUsers({ page: 1, size: 500, role: 'teacher' }),
        getClassOptions(),
      ]);
      setData(batchRes.data.data.records);
      setTeachers(teacherRes.data.data.records);
      setClassOptions(classRes.data.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const loadDetail = async (batch: BatchVO) => {
    setActiveBatch(batch);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const [statsRes, rankingRes, evaluationRes, assignmentsRes, declarationsRes] = await Promise.all([
        getBatchStats(batch.id),
        getBatchRanking(batch.id),
        getBatchEvaluationTable(batch.id),
        getBatchAssignments(batch.id),
        getDeclarations({ batchId: batch.id, page: 1, size: 100 }),
      ]);
      const basicRes = await getBatchBasicAwards(batch.id);
      setStats(statsRes.data.data);
      setRanking(rankingRes.data.data);
      setEvaluationTable(evaluationRes.data.data);
      setAssignments(assignmentsRes.data.data);
      setDeclarations(declarationsRes.data.data.records || []);
      setBasicAwards(basicRes.data.data || []);
      setBasicImportCategory((current) => current || batch.categories?.[0]?.category);
    } finally {
      setDetailLoading(false);
    }
  };

  const refreshActiveDetail = async () => {
    if (activeBatch) {
      const latest = data.find((item) => item.id === activeBatch.id) || activeBatch;
      await loadDetail(latest);
    }
  };

  const confirmDeleteDeclaration = (record: DeclarationVO) => {
    modal.confirm({
      title: '确认删除该学生申报？',
      content: `将删除「${record.studentName || record.studentLoginId}」在本批次的申报及其明细、附件，操作不可恢复。`,
      centered: true,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteDeclaration(record.id);
          message.success('申报已删除');
          await load();
          await refreshActiveDetail();
        } catch (e: any) {
          message.error(e.response?.data?.message || '删除失败');
        }
      },
    });
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ reviewerCount: 1 });
    setConfiguredCategoryCodes(categoryMetas.map((category) => category.code));
    setWeights(buildDefaultWeights());
    setCaps({});
    setTargetType('all');
    setTargetClasses([]);
    setDrawerOpen(true);
  };

  const openEdit = (batch: BatchVO) => {
    setEditing(batch);
    form.setFieldsValue({
      name: batch.name,
      dates: [dayjs(batch.startDate), dayjs(batch.endDate)],
      description: batch.description,
      reviewerIds: batch.reviewers?.map((reviewer) => reviewer.id) || [],
      reviewerCount: batch.reviewerCount || 1,
    });
    const nextWeights: Record<string, number> = {};
    const nextCaps: Record<string, number | undefined> = {};
    const nextCodes: string[] = [];
    batch.categories?.forEach((cat) => {
      nextCodes.push(cat.category);
      nextWeights[cat.category] = Number(cat.weightPercent);
      nextCaps[cat.category] = cat.maxScoreCap;
    });
    setConfiguredCategoryCodes(nextCodes);
    setWeights(nextWeights);
    setCaps(nextCaps);
    setTargetType(batch.targetType === 'specified' ? 'specified' : 'all');
    setTargetClasses((batch.targetClasses || []).map((tc) => classComboValue(tc.grade, tc.className)));
    setDrawerOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (weightTotal !== 100) {
      message.error('类别权重合计必须等于 100%');
      return;
    }
    const reviewerIds: string[] = values.reviewerIds || [];
    const reviewerCount = values.reviewerCount || 1;
    if (reviewerIds.length > 0 && reviewerCount > reviewerIds.length) {
      message.error('每份申报审核人数不能超过已选审核人数');
      return;
    }
    if (targetType === 'specified' && targetClasses.length === 0) {
      message.error('指定班级发布时请至少选择一个班级');
      return;
    }

    const payload = {
      name: values.name,
      startDate: values.dates[0].format('YYYY-MM-DD'),
      endDate: values.dates[1].format('YYYY-MM-DD'),
      description: values.description,
      reviewerIds,
      reviewerCount,
      targetType,
      targetClasses: targetType === 'specified' ? targetClasses.map(splitClassCombo) : [],
      categories: configuredCategoryCodes.map((cat) => ({
        category: cat,
        weightPercent: weights[cat],
        maxScoreCap: caps[cat] || null,
      })),
    };

    try {
      if (editing) {
        await updateBatch(editing.id, payload);
        message.success('批次已更新');
      } else {
        await createBatch(payload);
        message.success('批次已创建');
      }
      setDrawerOpen(false);
      await load();
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    }
  };

  const handleDelete = async (id: string) => {
    await deleteBatch(id);
    message.success('批次已删除');
    await load();
  };

  const confirmDelete = (id: string) => {
    modal.confirm({
      title: '确认删除该批次？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDelete(id),
    });
  };

  const handleStatusChange = async (batch: BatchVO, status: 'open_timed' | 'open' | 'closed') => {
    await updateBatchStatus(batch.id, status);
    const tip =
      status === 'open_timed' ? '批次已设为定时开放' : status === 'open' ? '批次已设为手动开放' : '批次已截止';
    message.success(tip);
    await load();
  };

  const openReopen = (batch: BatchVO) => {
    setActiveBatch(batch);
    reopenForm.setFieldsValue({ endDate: dayjs(batch.endDate).add(7, 'day') });
    setReopenOpen(true);
  };

  const handleReopen = async () => {
    if (!activeBatch) return;
    const values = await reopenForm.validateFields();
    await reopenBatch(activeBatch.id, values.endDate.format('YYYY-MM-DD'));
    message.success('批次已重新开放');
    setReopenOpen(false);
    await load();
  };

  const handleRecalc = async (id: string) => {
    await recalculateBatch(id, '管理员手动触发');
    message.success('分数已重算');
  };

  const handleGenerateAssignments = async (replacePending: boolean) => {
    if (!activeBatch) return;
    const res = await generateBatchAssignments(activeBatch.id, replacePending);
    const result = res.data.data;
    message.success(`已处理 ${result.declarationCount} 份申报，生成 ${result.assignmentCount} 个审核任务`);
    await load();
    await refreshActiveDetail();
  };

  const openSpecifiedAssignment = () => {
    specifiedAssignmentForm.resetFields();
    setSpecifiedAssignmentOpen(true);
  };

  const handleSpecifiedAssignment = async () => {
    if (!activeBatch) return;
    const values = await specifiedAssignmentForm.validateFields();
    setSpecifiedAssignmentSubmitting(true);
    try {
      const res = await generateBatchAssignments(activeBatch.id, {
        reviewerId: values.reviewerId,
        count: values.count,
      });
      const result = res.data.data;
      message.success(`已指定分配 ${result.assignmentCount} 份待审任务`);
      setSpecifiedAssignmentOpen(false);
      await load();
      await refreshActiveDetail();
    } catch (e: any) {
      message.error(e.response?.data?.message || '指定分配失败');
    } finally {
      setSpecifiedAssignmentSubmitting(false);
    }
  };

  const handleExport = async (batch: BatchVO) => {
    const res = await exportBatch(batch.id);
    const url = URL.createObjectURL(res.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${batch.name}-批次数据.xlsx`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const columns = [
    {
      title: '批次',
      dataIndex: 'name',
      width: 260,
      render: (_: string, record: BatchVO) => (
        <Space direction="vertical" size={2}>
          <Typography.Text strong>{record.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.startDate} 至 {record.endDate}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status: string) => <Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status] || status}</Tag>,
    },
    {
      title: '发布范围',
      width: 130,
      render: (_: unknown, record: BatchVO) =>
        record.targetType === 'specified'
          ? <Tag color="blue">{record.targetClasses?.length || 0} 个班级</Tag>
          : <Tag>全部学生</Tag>,
    },
    {
      title: '审核配置',
      width: 180,
      render: (_: unknown, record: BatchVO) => (
        <Space direction="vertical" size={2}>
          <Typography.Text>{record.reviewers?.length || 0} 位审核人</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            每份 {record.reviewerCount || 1} 人审核
          </Typography.Text>
        </Space>
      ),
    },
    { title: '申报数', dataIndex: 'declarationCount', width: 90 },
    { title: '待审任务', dataIndex: 'pendingAuditCount', width: 100 },
    { title: '已通过', dataIndex: 'approvedCount', width: 90 },
    {
      title: '操作',
      width: 360,
      render: (_: unknown, record: BatchVO) => (
        <Space wrap>
          <Tooltip title="查看统计、排名、细则和分配">
            <Button size="small" icon={<EyeOutlined />} onClick={() => loadDetail(record)}>详情</Button>
          </Tooltip>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          {record.status === 'draft' && (
            <>
              <Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleStatusChange(record, 'open_timed')}>定时开放</Button>
              <Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleStatusChange(record, 'open')}>手动开放</Button>
            </>
          )}
          {record.status === 'open_timed' && (
            <>
              <Tooltip title="改为手动开放：不按日期，一直可提交，由管理员手动截止">
                <Button size="small" icon={<RetweetOutlined />} onClick={() => handleStatusChange(record, 'open')}>转手动</Button>
              </Tooltip>
              <Button size="small" icon={<StopOutlined />} onClick={() => handleStatusChange(record, 'closed')}>截止</Button>
            </>
          )}
          {record.status === 'open' && (
            <>
              <Tooltip title="改为定时开放：按批次起止日期自动开放与截止">
                <Button size="small" icon={<RetweetOutlined />} onClick={() => handleStatusChange(record, 'open_timed')}>转定时</Button>
              </Tooltip>
              <Button size="small" icon={<StopOutlined />} onClick={() => handleStatusChange(record, 'closed')}>截止</Button>
            </>
          )}
          {record.status === 'closed' && (
            <Button size="small" icon={<PlayCircleOutlined />} onClick={() => openReopen(record)}>重开</Button>
          )}
          <Button size="small" icon={<DownloadOutlined />} onClick={() => handleExport(record)}>导出</Button>
          <Button size="small" icon={<ReloadOutlined />} onClick={() => handleRecalc(record.id)}>重算</Button>
          <Tooltip title={(record.declarationCount ?? 0) > 0 ? '该批次下已有申报，无法删除' : ''}>
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              disabled={(record.declarationCount ?? 0) > 0}
              onClick={() => confirmDelete(record.id)}
            >
              删除
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ];

  const formatScore = (value?: number | string | null) => {
    if (value === undefined || value === null || value === '') return '';
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric.toFixed(2) : '';
  };

  const activeBatchCategories = useMemo(() => {
    const cats = activeBatch?.categories || [];
    return [...cats].sort((a, b) => {
      const aOrder = categoryMap.get(a.category)?.sortOrder ?? Number.MAX_SAFE_INTEGER;
      const bOrder = categoryMap.get(b.category)?.sortOrder ?? Number.MAX_SAFE_INTEGER;
      if (aOrder !== bOrder) return aOrder - bOrder;
      return a.category.localeCompare(b.category);
    });
  }, [activeBatch?.categories, categoryMap]);

  const rankingColumns = useMemo(() => [
    { title: '排名', dataIndex: 'rank', width: 80 },
    { title: '学号', dataIndex: 'studentLoginId', width: 140 },
    { title: '姓名', dataIndex: 'studentName', width: 120 },
    { title: '总分', dataIndex: 'totalScore', width: 100, render: (v?: number) => formatScore(v) || '-' },
    ...activeBatchCategories.map((category) => ({
      title: (
        <Space size={6}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: getCategoryColor(category.category), display: 'inline-block' }} />
          {getCategoryName(category.category)}
        </Space>
      ),
      key: `category-${category.category}`,
      width: 110,
      render: (_: unknown, record: BatchRankingVO) => formatScore(record.categoryScores?.[category.category]) || '-',
    })),
  ], [activeBatchCategories, getCategoryColor, getCategoryName]);

  const verticalAwardTitle = (name: string) => (
    <span style={{
      display: 'inline-block',
      minHeight: 96,
      lineHeight: 1.15,
      writingMode: 'vertical-rl',
      textOrientation: 'mixed',
      whiteSpace: 'normal',
    }}>
      {name}
    </span>
  );

  const evaluationColumns = useMemo(() => {
    const columns: any[] = [
      { title: '学号', dataIndex: 'studentLoginId', width: 140, fixed: 'left' },
      { title: '姓名', dataIndex: 'studentName', width: 110, fixed: 'left' },
    ];
    (evaluationTable?.categories || []).forEach((category) => {
      columns.push({
        title: <Tag color={category.color || getCategoryColor(category.code)}>{category.name || getCategoryName(category.code)}</Tag>,
        key: `category-${category.code}`,
        children: [
          ...category.awards.map((award) => ({
            title: award.custom
              ? <span style={{ color: '#fa8c16' }} title="自定义奖项">{verticalAwardTitle(award.name)}</span>
              : verticalAwardTitle(award.name),
            key: `award-${award.awardId}`,
            width: 72,
            align: 'center',
            render: (_: unknown, record: BatchEvaluationTableVO['rows'][number]) => formatScore(record.scores?.[award.awardId]),
          })),
          {
            title: verticalAwardTitle('小计(分)'),
            key: `subtotal-${category.code}`,
            width: 78,
            align: 'center',
            render: (_: unknown, record: BatchEvaluationTableVO['rows'][number]) => formatScore(record.subtotals?.[category.code]),
          },
        ],
      });
    });
    columns.push({
      title: '签名',
      key: 'signature',
      width: 120,
      align: 'center',
      render: () => '',
    });
    return columns;
  }, [evaluationTable, getCategoryColor, getCategoryName]);

  const assignmentColumns = [
    { title: '学生', dataIndex: 'studentName', width: 120 },
    { title: '学号', dataIndex: 'studentLoginId', width: 130 },
    { title: '审核人', dataIndex: 'reviewerName', width: 120 },
    {
      title: '任务状态',
      dataIndex: 'status',
      width: 110,
      render: (status: string) => <Tag color={assignmentStatusColors[status]}>{assignmentStatusLabels[status] || status}</Tag>,
    },
    { title: '意见', dataIndex: 'comment', ellipsis: true },
    { title: '处理时间', dataIndex: 'reviewedAt', width: 180, render: (v?: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
  ];

  const handleBasicImport = async () => {
    if (!activeBatch || !basicImportCategory) {
      message.warning('请先选择批次类别');
      return;
    }
    if (!basicImportFile) {
      message.warning('请先选择 Excel 文件');
      return;
    }
    setBasicImporting(true);
    try {
      const res = await importBatchBasicAwards(activeBatch.id, basicImportCategory, basicImportFile);
      setBasicImportResult(res.data.data);
      setBasicImportFile(null);
      const [basicRes, evaluationRes, statsRes, rankingRes] = await Promise.all([
        getBatchBasicAwards(activeBatch.id),
        getBatchEvaluationTable(activeBatch.id),
        getBatchStats(activeBatch.id),
        getBatchRanking(activeBatch.id),
      ]);
      setBasicAwards(basicRes.data.data || []);
      setEvaluationTable(evaluationRes.data.data);
      setStats(statsRes.data.data);
      setRanking(rankingRes.data.data);
      message.success('基础分导入完成');
    } catch (e: any) {
      message.error(e.response?.data?.message || '基础分导入失败');
    } finally {
      setBasicImporting(false);
    }
  };

  const renderBasicAwardsTab = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space direction="vertical" style={{ width: 360 }}>
            <Typography.Text strong>导入基础分</Typography.Text>
            <Select
              style={{ width: '100%' }}
              placeholder="选择类别"
              value={basicImportCategory}
              onChange={(value) => {
                setBasicImportCategory(value);
                setBasicImportResult(null);
              }}
              options={(activeBatch?.categories || []).map((category) => ({
                value: category.category,
                label: getCategoryName(category.category),
              }))}
            />
          </Space>
          <Button
            type="primary"
            icon={<UploadOutlined />}
            loading={basicImporting}
            onClick={handleBasicImport}
          >
            开始导入
          </Button>
        </Space>
        <Upload.Dragger
          accept=".xls,.xlsx"
          maxCount={1}
          beforeUpload={(file) => {
            setBasicImportFile(file);
            setBasicImportResult(null);
            return false;
          }}
          onRemove={() => {
            setBasicImportFile(null);
            setBasicImportResult(null);
          }}
          fileList={basicImportFile ? [{ uid: 'basic-file', name: basicImportFile.name, status: 'done' }] : []}
          style={{ marginTop: 16 }}
        >
          <p className="ant-upload-drag-icon"><UploadOutlined /></p>
          <p className="ant-upload-text">点击或拖拽基础分 Excel 到此处</p>
          <p className="ant-upload-hint">表头为：学号、项目、分数；项目必须是奖项库中同类别的基础奖项。</p>
        </Upload.Dragger>
      </Card>

      {basicImportResult && (
        <Card size="small">
          <Row gutter={12}>
            <Col span={6}><Statistic title="成功行数" value={basicImportResult.successCount} /></Col>
            <Col span={6}><Statistic title="失败行数" value={basicImportResult.failedCount} /></Col>
            <Col span={6}><Statistic title="项目数" value={basicImportResult.projectCount} /></Col>
            <Col span={6}><Statistic title="学生数" value={basicImportResult.studentCount} /></Col>
          </Row>
          {basicImportResult.errors?.length > 0 && (
            <Table
              size="small"
              style={{ marginTop: 16 }}
              rowKey={(row) => `${row.row}-${row.loginId}-${row.project}`}
              dataSource={basicImportResult.errors}
              pagination={{ pageSize: 5 }}
              columns={[
                { title: '行号', dataIndex: 'row', width: 80 },
                { title: '学号', dataIndex: 'loginId', width: 130 },
                { title: '项目', dataIndex: 'project', width: 180 },
                { title: '原因', dataIndex: 'reason' },
              ]}
            />
          )}
        </Card>
      )}

      <Table
        rowKey="awardId"
        loading={detailLoading}
        dataSource={basicAwards}
        pagination={{ pageSize: 8 }}
        columns={[
          {
            title: '类别',
            dataIndex: 'category',
            width: 120,
            render: (cat: string) => <Tag color={getCategoryColor(cat)}>{getCategoryName(cat)}</Tag>,
          },
          { title: '基础项目', dataIndex: 'awardName', width: 220 },
          { title: '导入人数', dataIndex: 'importedCount', width: 110, render: (v?: number) => v ?? 0 },
          { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (v?: string) => v ? new Date(v).toLocaleString('zh-CN') : '-' },
        ]}
        expandable={{
          expandedRowRender: (record) => (
            <Table
              size="small"
              rowKey="studentId"
              dataSource={record.scores || []}
              pagination={{ pageSize: 8 }}
              columns={[
                { title: '学号', dataIndex: 'studentLoginId', width: 140 },
                { title: '姓名', dataIndex: 'studentName', width: 140 },
                { title: '分数', dataIndex: 'score', width: 120, render: (v: number) => Number(v || 0).toFixed(2) },
              ]}
            />
          ),
        }}
      />
    </Space>
  );

  const renderDetailTabs = () => (
    <Tabs
      items={[
        {
          key: 'stats',
          label: '统计',
          children: stats ? (
            <Space direction="vertical" size={20} style={{ width: '100%' }}>
              <Row gutter={[16, 16]}>
                <Col span={6}><Card><Statistic title="申报总数" value={stats.totalDeclarations} /></Card></Col>
                <Col span={6}><Card><Statistic title="待审核" value={stats.submittedCount} /></Card></Col>
                <Col span={6}><Card><Statistic title="已通过" value={stats.approvedCount} /></Card></Col>
                <Col span={6}><Card><Statistic title="待审任务" value={stats.pendingAuditCount} /></Card></Col>
                <Col span={6}><Card><Statistic title="平均分" value={stats.averageScore || 0} precision={2} /></Card></Col>
                <Col span={6}><Card><Statistic title="最高分" value={stats.maxScore || 0} precision={2} /></Card></Col>
                <Col span={6}><Card><Statistic title="最低分" value={stats.minScore || 0} precision={2} /></Card></Col>
                <Col span={6}><Card><Statistic title="已处理任务" value={stats.finishedAuditCount} /></Card></Col>
              </Row>
              <Card>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Typography.Text strong>审核进度</Typography.Text>
                  <Progress percent={Number(stats.auditProgress || 0)} />
                </Space>
              </Card>
            </Space>
          ) : <Empty />,
        },
        {
          key: 'ranking',
          label: '排名',
          children: <Table columns={rankingColumns} dataSource={ranking} rowKey="declarationId" loading={detailLoading} pagination={{ pageSize: 10 }} />,
        },
        {
          key: 'declarations',
          label: '申报记录',
          children: (
            <Table
              rowKey="id"
              loading={detailLoading}
              dataSource={declarations}
              pagination={{ pageSize: 10 }}
              columns={[
                { title: '学号', dataIndex: 'studentLoginId', width: 140 },
                { title: '姓名', dataIndex: 'studentName', width: 120 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (s: string) => <Tag color={STATUS_COLORS[s]}>{STATUS_LABELS[s] || s}</Tag>,
                },
                { title: '总分', dataIndex: 'totalScore', width: 100, render: (v?: number) => (v != null ? v.toFixed(1) : '--') },
                { title: '提交时间', dataIndex: 'submittedAt', width: 180, render: (v?: string) => (v ? new Date(v).toLocaleString('zh-CN') : '-') },
                {
                  title: '操作',
                  width: 160,
                  render: (_: unknown, record: DeclarationVO) => (
                    <Space>
                      <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/declarations/${record.id}`)}>查看</Button>
                      <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteDeclaration(record)}>删除</Button>
                    </Space>
                  ),
                },
              ]}
            />
          ),
        },
        {
          key: 'details',
          label: '细则',
          children: (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Typography.Text type="secondary">
                审核通过的自定义奖项以橙色单独成列并计入类别小计；签名列保留为空，便于导出打印签字。
              </Typography.Text>
              <Table
                bordered
                size="small"
                columns={evaluationColumns}
                dataSource={evaluationTable?.rows || []}
                rowKey={(row) => row.studentId || row.studentLoginId}
                loading={detailLoading}
                pagination={{ pageSize: 20 }}
                scroll={{ x: 'max-content' }}
              />
            </Space>
          ),
        },
        {
          key: 'basic-awards',
          label: '基础分',
          children: renderBasicAwardsTab(),
        },
        {
          key: 'assignments',
          label: '审核分配',
          children: <Table columns={assignmentColumns} dataSource={assignments} rowKey="id" loading={detailLoading} pagination={{ pageSize: 10 }} />,
        },
      ]}
    />
  );

  const renderDetailView = () => {
    if (!activeBatch) return null;
    return (
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Card styles={{ body: { padding: 16 } }}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }} align="center">
            <Space size={12}>
              <Button icon={<ArrowLeftOutlined />} onClick={() => setDetailOpen(false)}>返回</Button>
              <Space direction="vertical" size={0}>
                <Typography.Title level={4} style={{ margin: 0 }}>{activeBatch.name}</Typography.Title>
                <Typography.Text type="secondary">批次详情</Typography.Text>
              </Space>
            </Space>
            <Space wrap>
              <Button icon={<RetweetOutlined />} onClick={() => handleGenerateAssignments(false)}>随机平均补分配</Button>
              <Button icon={<TeamOutlined />} onClick={() => handleGenerateAssignments(true)}>随机重分配待审</Button>
              <Button icon={<UserAddOutlined />} onClick={openSpecifiedAssignment}>指定分配</Button>
              <Button icon={<DownloadOutlined />} onClick={() => handleExport(activeBatch)}>导出</Button>
            </Space>
          </Space>
        </Card>
        <Card styles={{ body: { padding: 16 } }}>
          {renderDetailTabs()}
        </Card>
      </Space>
    );
  };

  return (
    <div>
      {detailOpen && activeBatch ? renderDetailView() : (
        <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <Space direction="vertical" size={2}>
          <Typography.Title level={4} style={{ margin: 0 }}>批次管理</Typography.Title>
          <Typography.Text type="secondary">配置开放周期、审核分配、排名统计和归档导出</Typography.Text>
        </Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建批次</Button>
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table columns={columns} dataSource={data} rowKey="id" loading={loading} pagination={false} />
      </Card>
        </>
      )}

      <Drawer
        title={editing ? '编辑批次' : '新建批次'}
        width={680}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={<Button type="primary" onClick={handleSave}>保存</Button>}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="批次名称" rules={[{ required: true, message: '请输入批次名称' }]}>
            <Input placeholder="例如：2025-2026 学年第二学期综合测评" />
          </Form.Item>
          <Form.Item name="dates" label="起止日期" rules={[{ required: true, message: '请选择起止日期' }]}>
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Divider>审核分配</Divider>
          <Row gutter={16}>
            <Col span={16}>
              <Form.Item name="reviewerIds" label="审核人">
                <Select
                  mode="multiple"
                  placeholder="选择教师作为审核人"
                  options={teachers.map((teacher) => ({
                    label: `${teacher.name} (${teacher.loginId})`,
                    value: teacher.id,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="reviewerCount" label="每份申报审核人数" rules={[{ required: true }]}>
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Divider>发布范围</Divider>
          <Radio.Group
            value={targetType}
            onChange={(e) => setTargetType(e.target.value)}
            style={{ marginBottom: targetType === 'specified' ? 12 : 0 }}
          >
            <Radio value="all">全部学生</Radio>
            <Radio value="specified">指定班级</Radio>
          </Radio.Group>
          {targetType === 'specified' && (
            <Select
              mode="multiple"
              allowClear
              style={{ width: '100%' }}
              placeholder="选择发布的班级（按年级 + 班级匹配）"
              value={targetClasses}
              onChange={setTargetClasses}
              optionFilterProp="label"
              options={classOptions.map((option) => ({
                label: classOptionLabel(option),
                value: classComboValue(option.grade, option.className),
              }))}
            />
          )}

          <Divider>类别权重</Divider>
          <div style={{ background: '#F7F8FA', padding: 20, borderRadius: 8 }}>
            {configuredCategoryCodes.map((cat) => (
              <div key={cat} style={{ display: 'grid', gridTemplateColumns: '110px 1fr 86px 92px', gap: 12, alignItems: 'center', marginBottom: 14 }}>
                <Space>
                  <span style={{ width: 8, height: 8, borderRadius: 4, background: getCategoryColor(cat), display: 'inline-block' }} />
                  <span>{getCategoryName(cat)}</span>
                </Space>
                <Slider min={0} max={100} step={5} value={weights[cat] || 0} onChange={(v) => setWeights((prev) => ({ ...prev, [cat]: v }))} />
                <InputNumber
                  min={0}
                  max={100}
                  value={weights[cat] || 0}
                  onChange={(v) => setWeights((prev) => ({ ...prev, [cat]: v || 0 }))}
                  formatter={(v) => `${v}%`}
                  parser={(v) => Number(v?.replace('%', '')) || 0}
                />
                <InputNumber
                  placeholder="上限"
                  min={0}
                  value={caps[cat]}
                  onChange={(v) => setCaps((prev) => ({ ...prev, [cat]: v || undefined }))}
                />
              </div>
            ))}
            <Typography.Text strong style={{ color: weightTotal === 100 ? '#52C41A' : '#FF4D4F' }}>
              合计：{weightTotal}%
            </Typography.Text>
          </div>
        </Form>
      </Drawer>

      <Modal
        title="指定分配待审"
        open={specifiedAssignmentOpen}
        onOk={handleSpecifiedAssignment}
        onCancel={() => setSpecifiedAssignmentOpen(false)}
        okText="确认分配"
        confirmLoading={specifiedAssignmentSubmitting}
      >
        <Form form={specifiedAssignmentForm} layout="vertical">
          <Form.Item
            name="reviewerId"
            label="审核人"
            rules={[{ required: true, message: '请选择审核人' }]}
          >
            <Select
              placeholder="选择当前批次审核人"
              options={(activeBatch?.reviewers || []).map((reviewer) => ({
                label: `${reviewer.name} (${reviewer.loginId})`,
                value: reviewer.id,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="count"
            label="分配份数"
            rules={[{ required: true, message: '请输入分配份数' }]}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Typography.Text type="secondary">
            系统会从仍可分配的待审申报中随机抽取，候选不足时按实际可分配数量生成任务。
          </Typography.Text>
        </Form>
      </Modal>

      <Modal
        title="重新开放批次"
        open={reopenOpen}
        onOk={handleReopen}
        onCancel={() => setReopenOpen(false)}
        okText="重新开放"
      >
        <Form form={reopenForm} layout="vertical">
          <Form.Item name="endDate" label="新的截止日期" rules={[{ required: true, message: '请选择新的截止日期' }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default BatchManagement;
