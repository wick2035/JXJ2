import React, { useEffect, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import {
  App,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Row,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  TagsOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { createAward, deleteAward, getAwards, getLevels, updateAward } from '../../api/award';
import { createCategory, deleteCategory, updateCategory } from '../../api/category';
import { useCategories } from '../../hooks/useCategories';
import type { AwardLevelDef, AwardVO, CategoryMeta } from '../../types';

type ScoreMatrixItem = {
  key: string;
  levelId?: string;
  levelName?: string;
  baseScore: number;
  custom: boolean;
};

let temporaryLevelRowNumber = 0;
const temporaryLevelRowKey = () => `new-level-${++temporaryLevelRowNumber}`;

const presetRows = (levels: AwardLevelDef[], award?: AwardVO): ScoreMatrixItem[] =>
  levels.map((level) => ({
    key: level.id,
    levelId: level.id,
    baseScore: award?.levelScores.find((score) => score.levelId === level.id)?.baseScore ?? 0,
    custom: false,
  }));

const customRows = (award: AwardVO, levels: AwardLevelDef[], clone: boolean): ScoreMatrixItem[] =>
  award.levelScores
    .filter((score) => !levels.some((level) => level.id === score.levelId))
    .map((score) => ({
      key: clone ? temporaryLevelRowKey() : score.levelId,
      levelId: clone ? undefined : score.levelId,
      levelName: score.levelName,
      baseScore: score.baseScore,
      custom: true,
    }));

const requestErrorMessage = (error: unknown, fallback: string): string =>
  isAxiosError<{ message?: string }>(error) ? error.response?.data?.message || fallback : fallback;

const AwardLibrary: React.FC = () => {
  const { message, modal } = App.useApp();
  const [awards, setAwards] = useState<AwardVO[]>([]);
  const [levels, setLevels] = useState<AwardLevelDef[]>([]);
  const [selectedCat, setSelectedCat] = useState<string>();
  const [awardModalOpen, setAwardModalOpen] = useState(false);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [editingAward, setEditingAward] = useState<AwardVO | null>(null);
  const [editingCategory, setEditingCategory] = useState<CategoryMeta | null>(null);
  const [scoreMatrix, setScoreMatrix] = useState<ScoreMatrixItem[]>([]);
  const [reuseAwardId, setReuseAwardId] = useState<string>();
  const [awardSaving, setAwardSaving] = useState(false);
  const [awardForm] = Form.useForm();
  const [categoryForm] = Form.useForm();
  const awardTypeWatch = Form.useWatch('awardType', awardForm) || 'normal';
  const { categories, loadCategories, getCategoryName, getCategoryColor } = useCategories();

  const loadAwards = async () => {
    const [awardRes, levelRes] = await Promise.all([getAwards(), getLevels()]);
    setAwards(awardRes.data.data || []);
    setLevels(levelRes.data.data || []);
  };

  useEffect(() => {
    loadAwards();
  }, []);

  useEffect(() => {
    if (!selectedCat && categories.length > 0) {
      setSelectedCat(categories[0].code);
    }
  }, [categories, selectedCat]);

  const categoryOptions = categories.map((category) => ({
    value: category.code,
    label: category.name,
  }));

  const selectedCategory = categories.find((category) => category.code === selectedCat);
  const filteredAwards = awards.filter((award) => award.category === selectedCat);
  const awardTemplateOptions = awards
    .filter((award) => (award.awardType || 'normal') === 'normal')
    .map((award) => ({
      value: award.id,
      label: `${getCategoryName(award.category)} / ${award.name}`,
    }));

  const awardCountByCategory = useMemo(() => {
    const counts: Record<string, number> = {};
    awards.forEach((award) => {
      counts[award.category] = (counts[award.category] || 0) + 1;
    });
    return counts;
  }, [awards]);

  const openCreateAward = () => {
    setEditingAward(null);
    setReuseAwardId(undefined);
    awardForm.resetFields();
    awardForm.setFieldsValue({
      category: selectedCat || categories[0]?.code,
      awardType: 'normal',
    });
    setScoreMatrix(presetRows(levels));
    setAwardModalOpen(true);
  };

  const openEditAward = (award: AwardVO) => {
    setEditingAward(award);
    setReuseAwardId(undefined);
    awardForm.setFieldsValue({
      name: award.name,
      category: award.category,
      awardType: award.awardType || 'normal',
      description: award.description,
    });
    setScoreMatrix([...presetRows(levels, award), ...customRows(award, levels, false)]);
    setAwardModalOpen(true);
  };

  const openCreateCategory = () => {
    setEditingCategory(null);
    categoryForm.resetFields();
    categoryForm.setFieldsValue({
      color: '#1677FF',
      sortOrder: categories.length + 1,
    });
    setCategoryModalOpen(true);
  };

  const openEditCategory = (category: CategoryMeta) => {
    setEditingCategory(category);
    categoryForm.setFieldsValue(category);
    setCategoryModalOpen(true);
  };

  const handleReuseAwardChange = (awardId?: string) => {
    setReuseAwardId(awardId);
    const template = awards.find((award) => award.id === awardId);
    setScoreMatrix(template
      ? [...presetRows(levels, template), ...customRows(template, levels, true)]
      : presetRows(levels));
  };

  const handleSaveAward = async () => {
    try {
      const values = await awardForm.validateFields();
      const awardType = values.awardType || 'normal';
      if (awardType !== 'basic') {
        const seen = new Set(levels.map((level) => level.name.trim().toLocaleLowerCase()));
        for (const score of scoreMatrix) {
          if (!Number.isFinite(score.baseScore) || score.baseScore < 0 || score.baseScore > 999999.99
            || Math.abs(score.baseScore * 100 - Math.round(score.baseScore * 100)) > 1e-6) {
            message.error('级别分值须在 0 到 999999.99 之间，最多两位小数');
            return;
          }
          if (!score.custom) continue;
          const name = score.levelName?.trim() || '';
          if (!name || name.length > 100 || score.baseScore <= 0) {
            message.error('自定义级别须填写名称及大于 0 的分值，名称不超过 100 字');
            return;
          }
          const key = name.toLocaleLowerCase();
          if (seen.has(key)) {
            message.error(`级别名称重复：${name}`);
            return;
          }
          seen.add(key);
        }
      }
      const payload = {
        ...values,
        awardType,
        levelScores: awardType === 'basic' ? [] : scoreMatrix
          .filter((score) => score.baseScore > 0)
          .map(({ levelId, levelName, baseScore }) => ({ levelId, levelName, baseScore })),
      };
      setAwardSaving(true);
      if (editingAward) {
        await updateAward(editingAward.id, payload);
        message.success('奖项已更新');
      } else {
        await createAward(payload);
        message.success('奖项已创建');
      }
      setAwardModalOpen(false);
      await loadAwards();
    } catch (error: unknown) {
      if (!(typeof error === 'object' && error !== null && 'errorFields' in error)) {
        message.error(requestErrorMessage(error, '操作失败'));
      }
    } finally {
      setAwardSaving(false);
    }
  };

  const handleDeleteAward = async (id: string) => {
    await deleteAward(id);
    message.success('奖项已删除');
    loadAwards();
  };

  const handleSaveCategory = async () => {
    const values = await categoryForm.validateFields();
    try {
      if (editingCategory) {
        await updateCategory(editingCategory.id, values);
        message.success('类别已更新');
      } else {
        await createCategory(values);
        message.success('类别已创建');
      }
      setCategoryModalOpen(false);
      await loadCategories();
    } catch (error: unknown) {
      message.error(requestErrorMessage(error, '类别保存失败'));
    }
  };

  const handleDeleteCategory = async (category: CategoryMeta) => {
    try {
      await deleteCategory(category.id);
      message.success('类别已删除');
      await loadCategories();
      if (selectedCat === category.code) {
        setSelectedCat(undefined);
      }
    } catch (error: unknown) {
      message.error(requestErrorMessage(error, '类别删除失败'));
    }
  };

  const confirmDeleteCategory = (category: CategoryMeta) => {
    modal.confirm({
      title: '确认删除该类别？',
      content: '已被使用的类别会被系统拒绝删除。',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDeleteCategory(category),
    });
  };

  const confirmDeleteAward = (id: string) => {
    modal.confirm({
      title: '确认删除该奖项？',
      centered: true,
      okText: '确认',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleDeleteAward(id),
    });
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>奖项库管理</Typography.Title>
          <Typography.Text type="secondary">普通奖项由学生填报；基础奖项由管理员在批次中导入分数。</Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateAward} disabled={categories.length === 0}>
          新增奖项
        </Button>
      </div>

      <Row gutter={24}>
        <Col span={7}>
          <Card
            title={<Space><TagsOutlined />类别</Space>}
            extra={<Button size="small" icon={<PlusOutlined />} onClick={openCreateCategory}>新增</Button>}
            style={{ height: 'calc(100vh - 200px)', overflow: 'auto' }}
          >
            <List
              dataSource={categories}
              locale={{ emptyText: '暂无类别' }}
              renderItem={(category) => (
                <List.Item
                  style={{
                    padding: '12px 8px',
                    borderRadius: 8,
                    background: selectedCat === category.code ? '#F0F7FF' : 'transparent',
                    cursor: 'pointer',
                  }}
                  onClick={() => setSelectedCat(category.code)}
                  actions={[
                    <Button key="edit" type="text" size="small" icon={<EditOutlined />} onClick={(e) => {
                      e.stopPropagation();
                      openEditCategory(category);
                    }} />,
                    <Button
                      key="delete"
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        confirmDeleteCategory(category);
                      }}
                    />,
                  ]}
                >
                  <List.Item.Meta
                    avatar={<span style={{ width: 10, height: 10, borderRadius: '50%', background: category.color, display: 'inline-block', marginTop: 10 }} />}
                    title={<Space><span>{category.name}</span><Tag>{awardCountByCategory[category.code] || 0}</Tag></Space>}
                    description={<span>{category.code} · 排序 {category.sortOrder}</span>}
                  />
                </List.Item>
              )}
            />
          </Card>
        </Col>

        <Col span={17}>
          <Card>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <Space>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: selectedCat ? getCategoryColor(selectedCat) : '#D9D9D9' }} />
                <Typography.Text strong style={{ fontSize: 16 }}>{selectedCategory?.name || '未选择类别'}</Typography.Text>
                <Tag>{filteredAwards.length} 个奖项</Tag>
              </Space>
              <Button type="dashed" icon={<PlusOutlined />} onClick={openCreateAward} disabled={!selectedCat}>在此类别新增</Button>
            </div>

            <List
              dataSource={filteredAwards}
              locale={{ emptyText: selectedCat ? '该类别暂无奖项' : '请先选择类别' }}
              renderItem={(award) => (
                <List.Item
                  style={{ border: '1px solid #F0F0F0', borderRadius: 8, padding: 16, marginBottom: 12 }}
                  actions={[
                    <Button key="edit" type="text" icon={<EditOutlined />} onClick={() => openEditAward(award)}>编辑</Button>,
                    <Button key="delete" type="text" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAward(award.id)}>
                      删除
                    </Button>,
                  ]}
                >
                  <List.Item.Meta
                    avatar={<TrophyOutlined style={{ color: getCategoryColor(award.category), fontSize: 20 }} />}
                    title={(
                      <Space wrap>
                        <span>{award.name}</span>
                        <Tag color={getCategoryColor(award.category)}>{getCategoryName(award.category)}</Tag>
                        {(award.awardType || 'normal') === 'basic' && <Tag color="blue">基础奖项</Tag>}
                      </Space>
                    )}
                    description={award.description}
                  />
                  {(award.awardType || 'normal') === 'basic' ? (
                    <Tag color="blue" style={{ padding: '6px 10px' }}>批次 Excel 导入</Tag>
                  ) : (
                    <Table
                      size="small"
                      bordered
                      pagination={false}
                      dataSource={award.levelScores || []}
                      rowKey="levelId"
                      columns={[
                        { title: '级别', dataIndex: 'levelName', width: 120 },
                        { title: '分值', dataIndex: 'baseScore', width: 100, render: (value: number) => `${value} 分` },
                      ]}
                      style={{ width: 260 }}
                    />
                  )}
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title={editingAward ? '编辑奖项' : '新增奖项'}
        open={awardModalOpen}
        onCancel={() => setAwardModalOpen(false)}
        onOk={handleSaveAward}
        confirmLoading={awardSaving}
        width={760}
      >
        <Form form={awardForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="奖项名称" rules={[{ required: true, message: '请输入奖项名称' }]}>
            <Input placeholder="例如：上课出勤" />
          </Form.Item>
          <Form.Item name="category" label="所属类别" rules={[{ required: true, message: '请选择类别' }]}>
            <Select options={categoryOptions} />
          </Form.Item>
          <Form.Item name="awardType" label="奖项类型" rules={[{ required: true }]}>
            <Segmented
              block
              options={[
                { label: '普通奖项', value: 'normal' },
                { label: '基础奖项', value: 'basic' },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          {!editingAward && awardTypeWatch !== 'basic' && (
            <Form.Item label="复用分数构成">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="选择已有普通奖项复制分数构成"
                value={reuseAwardId}
                options={awardTemplateOptions}
                onChange={handleReuseAwardChange}
              />
            </Form.Item>
          )}
        </Form>

        {awardTypeWatch === 'basic' ? (
          <Card size="small" style={{ marginTop: 16, background: '#F6FAFF', borderColor: '#D6E4FF' }}>
            <Typography.Text type="secondary">
              基础奖项不配置等级分。管理员会在批次详情里通过 Excel 导入每个学生的项目分数。
            </Typography.Text>
          </Card>
        ) : (
          <>
            <Divider />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <div>
                <Typography.Text strong>级别与分值</Typography.Text>
                <div><Typography.Text type="secondary">只有分值大于 0 的级别会提供给学生选择。</Typography.Text></div>
              </div>
              <Space wrap>
                <Select
                  value={undefined}
                  style={{ width: 150 }}
                  placeholder="添加默认级别"
                  options={levels
                    .filter((level) => !scoreMatrix.some((score) => score.levelId === level.id))
                    .map((level) => ({ value: level.id, label: level.name }))}
                  disabled={levels.every((level) => scoreMatrix.some((score) => score.levelId === level.id))}
                  onChange={(id: string) => setScoreMatrix((prev) => [
                    ...prev,
                    { key: id, levelId: id, baseScore: 0, custom: false },
                  ])}
                />
                <Button icon={<PlusOutlined />} onClick={() => setScoreMatrix((prev) => [
                  ...prev,
                  { key: temporaryLevelRowKey(), levelName: '', baseScore: 0, custom: true },
                ])}>
                  添加自定义级别
                </Button>
              </Space>
            </div>
            <Table
              size="small"
              bordered
              pagination={false}
              style={{ marginTop: 12 }}
              dataSource={scoreMatrix}
              rowKey="key"
              columns={[
                {
                  title: '级别',
                  dataIndex: 'levelName',
                  render: (_: string, record: ScoreMatrixItem) => record.custom ? (
                    <Input
                      value={record.levelName}
                      maxLength={100}
                      placeholder="如：国家级一等奖"
                      onChange={(event) => setScoreMatrix((prev) => prev.map((score) => (
                        score.key === record.key ? { ...score, levelName: event.target.value } : score
                      )))}
                    />
                  ) : levels.find((level) => level.id === record.levelId)?.name || record.levelId,
                },
                {
                  title: '基础分值',
                  dataIndex: 'baseScore',
                  width: 165,
                  render: (_: number, record: ScoreMatrixItem) => (
                    <InputNumber
                      min={0}
                      max={999999.99}
                      step={0.5}
                      precision={2}
                      value={record.baseScore}
                      onChange={(value) => {
                        setScoreMatrix((prev) => prev.map((score) => (
                          score.key === record.key ? { ...score, baseScore: value || 0 } : score
                        )));
                      }}
                    />
                  ),
                },
                {
                  title: '操作',
                  width: 70,
                  render: (_: unknown, record: ScoreMatrixItem) => (
                    <Button
                      type="text"
                      danger
                      aria-label={`移除${record.custom ? record.levelName || '自定义级别' : levels.find((level) => level.id === record.levelId)?.name || '级别'}`}
                      icon={<DeleteOutlined />}
                      onClick={() => setScoreMatrix((prev) => prev.filter((score) => score.key !== record.key))}
                    />
                  ),
                },
              ]}
            />
          </>
        )}
      </Modal>

      <Modal
        title={editingCategory ? '编辑类别' : '新增类别'}
        open={categoryModalOpen}
        onCancel={() => setCategoryModalOpen(false)}
        onOk={handleSaveCategory}
        width={520}
      >
        <Form form={categoryForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="code" label="类别编码" rules={[{ required: true, message: '请输入类别编码' }]}>
            <Input disabled={!!editingCategory} placeholder="例如：innovation" />
          </Form.Item>
          <Form.Item name="name" label="类别名称" rules={[{ required: true, message: '请输入类别名称' }]}>
            <Input placeholder="例如：创新发展" />
          </Form.Item>
          <Form.Item name="color" label="展示颜色" rules={[{ required: true, message: '请选择展示颜色' }]}>
            <Input type="color" style={{ width: 72, padding: 4 }} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序" rules={[{ required: true, message: '请输入排序' }]}>
            <InputNumber min={0} step={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AwardLibrary;
