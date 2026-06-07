import React, { useEffect, useState } from 'react';
import { App, Row, Col, Card, Tag, Typography, Statistic, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';
import { getBatches } from '../../api/batch';
import { getDeclarations } from '../../api/declaration';
import { getAuditQueueStats } from '../../api/audit';
import { useAuthStore } from '../../store/authStore';
import type { AuditQueueStatsVO, BatchVO, DeclarationVO } from '../../types';
import { useCategories } from '../../hooks/useCategories';
import { getBatchPhase, canStudentSubmit, PHASE_LABELS, PHASE_COLORS } from '../../utils/batchWindow';

const Dashboard: React.FC = () => {
  const [batches, setBatches] = useState<BatchVO[]>([]);
  const [declarations, setDeclarations] = useState<DeclarationVO[]>([]);
  const [auditStats, setAuditStats] = useState<AuditQueueStatsVO | null>(null);
  const [loading, setLoading] = useState(true);
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const { modal } = App.useApp();
  const { categoryMap, getCategoryName } = useCategories();

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        if (user?.role === 'student') {
          const [bRes, dRes] = await Promise.all([
            getBatches({ page: 1, size: 50 }),
            getDeclarations({ page: 1, size: 50 }),
          ]);
          setBatches(bRes.data.data.records);
          setDeclarations(dRes.data.data.records);
          setAuditStats(null);
        } else {
          const [bRes, dRes, aRes] = await Promise.all([
            getBatches({ page: 1, size: 50 }),
            getDeclarations({ page: 1, size: 50 }),
            getAuditQueueStats({}),
          ]);
          setBatches(bRes.data.data.records);
          setDeclarations(dRes.data.data.records);
          setAuditStats(aRes.data.data);
        }
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [user?.role]);

  const getDeclForBatch = (batchId: string) =>
    declarations.find((d) => d.batchId === batchId);

  const getCategoryStatus = (decl: DeclarationVO | undefined, cat: string) => {
    if (!decl?.items) return 'empty';
    const items = decl.items.filter((i) => i.category === cat);
    return items.length > 0 ? 'done' : 'empty';
  };

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const isStudent = user?.role === 'student';
  const isTeacher = user?.role === 'teacher';

  return (
    <div>
      <div style={{ marginBottom: 32 }}>
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          你好，{user?.name}
        </Typography.Title>
        <Typography.Text type="secondary">
          {isStudent ? '这是你的综合素质评价概览' : '欢迎使用综合测评管理系统'}
        </Typography.Text>
      </div>

      {!isStudent && (
        <Row gutter={[24, 24]} style={{ marginBottom: 32 }}>
          <Col span={6}>
            <Card>
              <Statistic title="进行中批次" value={batches.filter((b) => getBatchPhase(b) === 'open').length} valueStyle={{ color: '#1677FF' }} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic
                title={isTeacher ? '我的待审' : '全部待审'}
                value={isTeacher ? auditStats?.myPending || 0 : auditStats?.totalSubmitted || 0}
                valueStyle={{ color: '#FAAD14' }}
              />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="已分配待审" value={auditStats?.assignedPending || 0} valueStyle={{ color: '#52C41A' }} />
            </Card>
          </Col>
          <Col span={6}>
            <Card>
              <Statistic title="未分配待审" value={auditStats?.unassigned || 0} />
            </Card>
          </Col>
        </Row>
      )}

      <Row gutter={[24, 24]}>
        {batches.map((batch) => {
          const decl = getDeclForBatch(batch.id);
          const phase = getBatchPhase(batch);
          return (
            <Col key={batch.id} xs={24} sm={12} lg={8}>
              <Card
                hoverable
                style={{
                  borderRadius: 12,
                  cursor: 'pointer',
                  transition: 'all 0.3s',
                  position: 'relative',
                  overflow: 'hidden',
                }}
                onClick={() => {
                  if (isStudent) {
                    if (decl && ['submitted', 'approved', 'rejected'].includes(decl.status)) {
                      modal.confirm({
                        centered: true,
                        icon: null,
                        title: '当前批次您已有提交的申报',
                        okText: '查看',
                        cancelText: '取消',
                        onOk: () => navigate(`/declarations/${decl.id}`),
                      });
                    } else if (canStudentSubmit(phase, decl?.status)) {
                      navigate(`/declare/${batch.id}`);
                    } else if (phase === 'not_started') {
                      modal.info({
                        centered: true,
                        title: '申报尚未开始',
                        content: `本批次将于 ${batch.startDate} 开始，开始后即可填报。`,
                      });
                    } else {
                      // ended / closed / draft：已截止或未开放
                      modal.info({
                        centered: true,
                        title: '申报已截止',
                        content: `本批次已于 ${batch.endDate} 截止，不能再提交。`,
                        okText: decl ? '查看我的申报' : '知道了',
                        onOk: () => {
                          if (decl) navigate(`/declarations/${decl.id}`);
                        },
                      });
                    }
                  } else {
                    navigate(`/audit?batchId=${batch.id}`);
                  }
                }}
              >
                {/* 校徽印章水印：放大横版标识并定位，使左侧圆形印章落在右下角，右侧字标溢出被裁切 */}
                <img
                  src="/wzut.svg"
                  aria-hidden
                  alt=""
                  style={{
                    position: 'absolute',
                    height: 200,
                    right: -718,
                    bottom: -55,
                    opacity: 0.06,
                    pointerEvents: 'none',
                    userSelect: 'none',
                    zIndex: 0,
                  }}
                />
                <div style={{ position: 'relative', zIndex: 1 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography.Text strong style={{ fontSize: 16 }}>{batch.name}</Typography.Text>
                  <Tag color={PHASE_COLORS[phase]}>{PHASE_LABELS[phase]}</Tag>
                </div>

                <div style={{ marginTop: 8, fontSize: 13, color: 'rgba(0,0,0,0.45)' }}>
                  开始 {batch.startDate} · 截止 {batch.endDate}
                </div>

                {isStudent && (
                  <div style={{ display: 'flex', gap: 16, marginTop: 20 }}>
                    {(batch.categories || [])
                      .map((category) => category.category)
                      .sort((a, b) => (categoryMap.get(a)?.sortOrder || 999) - (categoryMap.get(b)?.sortOrder || 999))
                      .map((cat) => {
                        const status = getCategoryStatus(decl, cat);
                        return (
                          <div key={cat} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                            <div
                              style={{
                                width: 12,
                                height: 12,
                                borderRadius: '50%',
                                background: status === 'done' ? '#52C41A' : '#F0F0F0',
                                transition: 'background 0.3s',
                              }}
                            />
                            <span style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>
                              {getCategoryName(cat)}
                            </span>
                          </div>
                        );
                      })}
                  </div>
                )}

                {!isStudent && (
                  <>
                    <div style={{ borderTop: '1px solid #F0F0F0', margin: '16px 0' }} />
                    <div style={{ display: 'flex', justifyContent: 'space-around' }}>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 22, fontWeight: 600, color: '#52C41A' }}>
                          {batch.submittedStudentCount ?? 0}
                        </div>
                        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>提交人数</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 22, fontWeight: 600, color: 'rgba(0,0,0,0.65)' }}>
                          {Math.max(0, (batch.eligibleStudentCount ?? 0) - (batch.submittedStudentCount ?? 0))}
                        </div>
                        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>未提交人数</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 22, fontWeight: 600, color: '#FAAD14' }}>
                          {batch.pendingReviewCount ?? 0}
                        </div>
                        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>待审核份数</div>
                      </div>
                    </div>
                  </>
                )}

                {isStudent && (
                  <>
                    <div style={{ borderTop: '1px solid #F0F0F0', margin: '16px 0' }} />
                    <div style={{ display: 'flex', justifyContent: 'space-around' }}>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 24, fontWeight: 600, color: '#1677FF' }}>
                          {decl?.totalScore?.toFixed(1) || '--'}
                        </div>
                        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>当前总分</div>
                      </div>
                      <div style={{ textAlign: 'center' }}>
                        <div style={{ fontSize: 24, fontWeight: 600, color: 'rgba(0,0,0,0.65)' }}>
                          {decl?.classRank != null ? decl.classRank : '--'}
                          {decl?.classRank != null && decl.classRankTotal != null && (
                            <span style={{ fontSize: 14, fontWeight: 400, color: 'rgba(0,0,0,0.45)' }}>
                              {' / '}{decl.classRankTotal}
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: 12, color: 'rgba(0,0,0,0.45)' }}>班级排名</div>
                      </div>
                    </div>
                  </>
                )}
                </div>
              </Card>
            </Col>
          );
        })}
      </Row>
    </div>
  );
};

export default Dashboard;
