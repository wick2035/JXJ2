import React, { useEffect, useState } from 'react';
import { Table, Card, Tag, Button, Space, Select, Typography, App } from 'antd';
import { ApartmentOutlined, EyeOutlined, EditOutlined, DeleteOutlined, RollbackOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { getDeclarations, deleteDeclaration, withdrawDeclaration } from '../../api/declaration';
import { getBatches } from '../../api/batch';
import { STATUS_LABELS, STATUS_COLORS } from '../../types';
import type { DeclarationVO, BatchVO } from '../../types';
import DeclarationProgressModal from '../../components/DeclarationProgressModal';

const DeclarationList: React.FC = () => {
  const { message, modal } = App.useApp();
  const [data, setData] = useState<DeclarationVO[]>([]);
  const [batches, setBatches] = useState<BatchVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [filterBatch, setFilterBatch] = useState<string>();
  const [filterStatus, setFilterStatus] = useState<string>();
  const [progressOpen, setProgressOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    try {
      const res = await getDeclarations({ batchId: filterBatch, status: filterStatus, page, size: 10 });
      setData(res.data.data.records);
      setTotal(res.data.data.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [page, filterBatch, filterStatus]);

  useEffect(() => {
    getBatches({ page: 1, size: 100 }).then((res) => setBatches(res.data.data.records));
  }, []);

  const handleDelete = async (id: string) => {
    await deleteDeclaration(id);
    message.success('删除成功');
    load();
  };


  const handleWithdraw = async (id: string) => {
    await withdrawDeclaration(id);
    message.success('申报已撤回');
    load();
  };

  const confirmWithdraw = (id: string) => {
    modal.confirm({
      title: '确认撤回申报？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleWithdraw(id),
    });
  };

  const confirmDelete = (id: string) => {
    modal.confirm({
      title: '确认删除？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDelete(id),
    });
  };


  const columns = [
    { title: '批次名称', dataIndex: 'batchName', width: 200, ellipsis: true },
    {
      title: '总分',
      dataIndex: 'totalScore',
      width: 100,
      render: (v: number) => <span style={{ fontWeight: 600, color: '#1677FF' }}>{v?.toFixed(1) || '--'}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={STATUS_COLORS[s]}>{STATUS_LABELS[s]}</Tag>,
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      width: 170,
      render: (v: string) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      width: 320,
      render: (_: any, record: DeclarationVO) => (
        <Space wrap>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate(`/declarations/${record.id}`)}>
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<ApartmentOutlined />}
            onClick={() => {
              setSelectedId(record.id);
              setProgressOpen(true);
            }}
          >
            查看流程
          </Button>
          {(record.status === 'draft' || record.status === 'returned') && (
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => navigate(`/declare/${record.batchId}`)}>
              编辑
            </Button>
          )}
          {record.canWithdraw && (
            <Button type="link" size="small" danger icon={<RollbackOutlined />} onClick={() => confirmWithdraw(record.id)}>
              撤回
            </Button>
          )}
          {(record.status === 'draft' || record.status === 'submitted') && (
            <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(record.id)}>
              删除
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>我的申报记录</Typography.Title>
      </div>

      <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
        <Select
          placeholder="筛选批次"
          allowClear
          style={{ width: 220 }}
          value={filterBatch}
          onChange={setFilterBatch}
          options={batches.map((b) => ({ label: b.name, value: b.id }))}
        />
        <Select
          placeholder="筛选状态"
          allowClear
          style={{ width: 150 }}
          value={filterStatus}
          onChange={setFilterStatus}
          options={[
            { label: '草稿', value: 'draft' },
            { label: '已提交', value: 'submitted' },
            { label: '已通过', value: 'approved' },
            { label: '已驳回', value: 'rejected' },
            { label: '已退回', value: 'returned' },
          ]}
        />
      </div>

      <Card styles={{ body: { padding: 0 } }}>
        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{
            current: page,
            total,
            pageSize: 10,
            onChange: setPage,
            showTotal: (t) => `共 ${t} 条`,
          }}
        />
      </Card>
      <DeclarationProgressModal
        open={progressOpen}
        declarationId={selectedId}
        onClose={() => setProgressOpen(false)}
        onChanged={load}
      />
    </div>
  );
};

export default DeclarationList;
