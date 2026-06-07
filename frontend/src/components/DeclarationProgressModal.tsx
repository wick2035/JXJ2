import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { App, Button, Modal, Space, Spin, Tag, Typography } from 'antd';
import { ReloadOutlined, RollbackOutlined } from '@ant-design/icons';
import { getDeclaration, withdrawDeclaration } from '../api/declaration';
import {
  STAGE_LABELS,
  STATUS_COLORS,
  STATUS_LABELS,
} from '../types';
import type { DeclarationStage, DeclarationVO } from '../types';

type DeclarationProgressModalProps = {
  open: boolean;
  declarationId?: string | null;
  onClose: () => void;
  onChanged?: () => void | Promise<void>;
};

const fallbackStage = (status: DeclarationVO['status']): DeclarationStage => {
  if (status === 'draft') return 'pending_submit';
  if (status === 'submitted') return 'submitted_unassigned';
  if (status === 'approved') return 'approved';
  return 'rejected';
};

const getCurrentIndex = (stage: DeclarationStage) => {
  if (stage === 'submitted_unassigned') return 1;
  if (stage === 'assigned') return 2;
  if (stage === 'reviewing' || stage === 'approved' || stage === 'rejected') return 3;
  return 0;
};

const FLOW_STEPS = [
  {
    key: 'pending_submit',
    pendingTitle: '待提交',
    currentTitle: '待提交',
    doneTitle: '已提交',
    pendingDescription: '完成材料填写后提交审核',
    doneDescription: '申报材料已进入流程',
  },
  {
    key: 'submitted_unassigned',
    pendingTitle: '待分配审批人',
    currentTitle: '待分配审批人',
    doneTitle: '已分配审批人',
    pendingDescription: '等待系统或管理员分配审核任务',
    doneDescription: '审核任务已派发',
  },
  {
    key: 'assigned',
    pendingTitle: '待审核',
    currentTitle: '待审核',
    doneTitle: '已进入审核',
    pendingDescription: '等待审批人处理',
    doneDescription: '审批人已开始处理',
  },
  {
    key: 'result',
    pendingTitle: '待出结果',
    currentTitle: '待出结果',
    doneTitle: '已通过',
    rejectedTitle: '已驳回',
    pendingDescription: '审核完成后生成最终结果',
    currentDescription: '审核已进入最终确认，等待生成结果',
    doneDescription: '申报已通过审核',
    rejectedDescription: '申报未通过或被退回',
  },
] as const;

const getFlowStepState = (index: number, currentIndex: number, stage: DeclarationStage) => {
  if (index === FLOW_STEPS.length - 1 && stage === 'rejected') return 'error';
  if (index < currentIndex || (index === FLOW_STEPS.length - 1 && stage === 'approved')) return 'done';
  if (index === currentIndex) return 'current';
  return 'wait';
};

const DeclarationProgressModal: React.FC<DeclarationProgressModalProps> = ({
  open,
  declarationId,
  onClose,
  onChanged,
}) => {
  const { message, modal } = App.useApp();
  const [data, setData] = useState<DeclarationVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);

  const loadDetail = useCallback(async () => {
    if (!declarationId) return;
    setLoading(true);
    try {
      const res = await getDeclaration(declarationId);
      setData(res.data.data);
    } finally {
      setLoading(false);
    }
  }, [declarationId]);

  useEffect(() => {
    if (open && declarationId) {
      void loadDetail();
    } else if (!open) {
      setData(null);
    }
  }, [open, declarationId, loadDetail]);

  const stage = data ? data.stage || fallbackStage(data.status) : 'pending_submit';
  const currentIndex = getCurrentIndex(stage);

  const flowItems = useMemo(() => FLOW_STEPS.map((step, index) => {
    const state = getFlowStepState(index, currentIndex, stage);
    const isResult = step.key === 'result';
    const title = state === 'done'
      ? step.doneTitle
      : state === 'error' && isResult
        ? step.rejectedTitle
        : state === 'current'
          ? step.currentTitle
          : step.pendingTitle;
    const description = state === 'done'
      ? step.doneDescription
      : state === 'error' && isResult
        ? step.rejectedDescription
        : state === 'current' && isResult
          ? step.currentDescription
          : step.pendingDescription;

    return { ...step, state, title, description };
  }), [currentIndex, stage]);

  const handleWithdraw = async () => {
    if (!data) return;
    setWithdrawing(true);
    try {
      await withdrawDeclaration(data.id);
      message.success('申报已撤回');
      await loadDetail();
      await onChanged?.();
    } finally {
      setWithdrawing(false);
    }
  };

  const confirmWithdraw = () => {
    modal.confirm({
      title: '确认撤回申报？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: handleWithdraw,
    });
  };

  return (
    <Modal
      title="申报流程"
      open={open}
      onCancel={onClose}
      footer={null}
      width={600}
      className="declaration-progress-modal"
    >
      <div className="declaration-progress-body">
        {loading && !data ? (
          <Spin size="large" style={{ display: 'block', margin: '96px auto' }} />
        ) : (
          <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <div className="declaration-progress-summary">
              <div className="declaration-progress-main">
                <Typography.Title level={5} className="declaration-progress-batch">
                  {data?.batchName || '申报记录'}
                </Typography.Title>
                <Space size={6} wrap className="declaration-progress-tags">
                  {data && <Tag color={STATUS_COLORS[data.status]}>{STATUS_LABELS[data.status]}</Tag>}
                  <Tag color={stage === 'approved' ? 'success' : stage === 'rejected' ? 'error' : 'processing'}>
                    {STAGE_LABELS[stage]}
                  </Tag>
                </Space>
              </div>

              <Space className="declaration-progress-actions">
                <Button icon={<ReloadOutlined />} onClick={loadDetail} loading={loading}>
                  刷新
                </Button>
                {data?.canWithdraw && (
                  <Button danger icon={<RollbackOutlined />} loading={withdrawing} onClick={confirmWithdraw}>
                    撤回申报
                  </Button>
                )}
              </Space>
            </div>

            <div className="declaration-flow" aria-label="申报流程进度">
              {flowItems.map((item, index) => (
                <div key={item.key} className={`declaration-flow-item is-${item.state}`}>
                  <div className="declaration-flow-rail" aria-hidden="true">
                    <div className="declaration-flow-dot">
                      {item.state === 'done' ? '✓' : item.state === 'error' ? '!' : index + 1}
                    </div>
                    {index < flowItems.length - 1 && <div className="declaration-flow-line" />}
                  </div>
                  <div className="declaration-flow-content">
                    <div className="declaration-flow-title-row">
                      <Typography.Text strong className="declaration-flow-title">
                        {item.title}
                      </Typography.Text>
                      {item.state === 'current' && <span className="declaration-flow-pill">当前流程</span>}
                    </div>
                    <Typography.Text type="secondary" className="declaration-flow-description">
                      {item.description}
                    </Typography.Text>
                  </div>
                </div>
              ))}
            </div>
          </Space>
        )}
      </div>
    </Modal>
  );
};

export default DeclarationProgressModal;
