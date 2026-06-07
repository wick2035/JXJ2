import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Divider, Empty, Form, Input, InputNumber, Select, Space, Tag, Typography, Upload } from 'antd';
import type { UploadFile } from 'antd';
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  LeftOutlined,
  PlusOutlined,
  RightOutlined,
  SaveOutlined,
  SendOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { getBatch, getMyBatchBasicItems } from '../../api/batch';
import { getBatchAwards } from '../../api/award';
import { getDeclaration, getDeclarations, saveDeclaration, submitDeclarationPayload } from '../../api/declaration';
import { openAttachmentInNewTab } from '../../api/attachment';
import type { AttachmentVO, AwardVO, BasicItemVO, BatchVO } from '../../types';
import { useCategories } from '../../hooks/useCategories';
import { getBatchPhase, canStudentSubmit } from '../../utils/batchWindow';

type ItemFormValue = {
  clientId?: string;
  id?: string;
  category: string;
  isCustom?: boolean;
  awardId?: string;
  levelId?: string;
  customAwardName?: string;
  customLevelName?: string;
  customBaseScore?: number;
  useDowngrade?: number;
  description?: string;
};

type AttachmentPayload = { id: string } | { fileIndex: number };

const hexToRgba = (hex: string, alpha: number) => {
  const h = (hex || '').replace('#', '');
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  const n = Number.parseInt(full, 16);
  if (Number.isNaN(n) || full.length !== 6) return `rgba(22, 119, 255, ${alpha})`;
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
};

const isAllowedAttachment = (file: File) => {
  const ext = file.name.split('.').pop()?.toLowerCase();
  return file.type.startsWith('image/') || file.type === 'application/pdf' || ext === 'pdf';
};

const createClientId = () => `item-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const getItemKey = (item?: Pick<ItemFormValue, 'id' | 'clientId'>, fallback?: number | string) =>
  item?.id || item?.clientId || `item-${fallback ?? 'unknown'}`;

const toExistingUploadFiles = (attachments?: AttachmentVO[]): UploadFile[] =>
  (attachments || []).map((att) => ({
    uid: att.id,
    name: att.fileName,
    status: 'done',
    size: att.fileSize,
    type: att.mimeType,
  }));

/** 预览上传项：新选未上传文件用本地 blob，已保存附件（uid 即附件 id）经鉴权接口打开 */
const previewUploadFile = (file: UploadFile) => {
  if (file.originFileObj) {
    window.open(URL.createObjectURL(file.originFileObj), '_blank', 'noopener,noreferrer');
  } else if (file.uid) {
    openAttachmentInNewTab(String(file.uid));
  }
};

const toAttachmentPayload = (uploadFile: UploadFile, files: File[], itemIndex: number): AttachmentPayload | null => {
  if (!uploadFile.originFileObj) {
    const attachmentId = String(uploadFile.uid || '');
    return attachmentId ? { id: attachmentId } : null;
  }

  const file = uploadFile.originFileObj as File | undefined;
  if (!file) return null;
  if (!isAllowedAttachment(file)) {
    throw new Error(`第 ${itemIndex + 1} 条明细仅支持图片或 PDF 附件`);
  }
  files.push(file);
  return { fileIndex: files.length - 1 };
};

const DeclarationForm: React.FC = () => {
  const { batchId } = useParams();
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const [form] = Form.useForm();
  const [batch, setBatch] = useState<BatchVO | null>(null);
  const [declStatus, setDeclStatus] = useState<string>();
  const [awards, setAwards] = useState<AwardVO[]>([]);
  const [basicItems, setBasicItems] = useState<BasicItemVO[]>([]);
  const [initializing, setInitializing] = useState(true);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [filesByItemKey, setFilesByItemKey] = useState<Record<string, UploadFile[]>>({});
  const [activeCategory, setActiveCategory] = useState<string>();
  const [hoverCategory, setHoverCategory] = useState<string>();
  const [indexByCategory, setIndexByCategory] = useState<Record<string, number>>({});
  const [, setTick] = useState(0);
  const refresh = () => setTick((t) => t + 1);
  const { categoryMap, getCategoryName, getCategoryColor } = useCategories();

  const itemsWatch = (Form.useWatch('items', { form, preserve: true }) || []) as ItemFormValue[];

  const configuredCategories = (batch?.categories || [])
    .map((category) => category.category)
    .sort((a, b) => (categoryMap.get(a)?.sortOrder || 999) - (categoryMap.get(b)?.sortOrder || 999));
  const defaultCategory = configuredCategories[0];
  const hasConfiguredCategories = configuredCategories.length > 0;

  const resolveItemCategory = useCallback(
    (item?: Partial<ItemFormValue>, _index?: number) => item?.category || activeCategory || defaultCategory,
    [activeCategory, defaultCategory]
  );

  const awardsByCategory = useMemo(() => {
    const map = new Map<string, AwardVO[]>();
    awards.forEach((award) => {
      const list = map.get(award.category) || [];
      list.push(award);
      map.set(award.category, list);
    });
    return map;
  }, [awards]);

  const getAwardById = (awardId?: string) => awards.find((a) => a.id === awardId);
  const getLevelScore = (awardId?: string, levelId?: string) =>
    getAwardById(awardId)?.levelScores.find((ls) => ls.levelId === levelId)?.baseScore;

  useEffect(() => {
    if (!batchId) {
      setInitializing(false);
      return;
    }
    let alive = true;
    const load = async () => {
      setInitializing(true);
      try {
        const [batchRes, awardsRes, declarationRes, basicRes] = await Promise.all([
          getBatch(batchId),
          getBatchAwards(batchId),
          getDeclarations({ batchId, page: 1, size: 1 }),
          getMyBatchBasicItems(batchId),
        ]);
        if (!alive) return;
        setBatch(batchRes.data.data);
        setAwards(awardsRes.data.data || []);
        setBasicItems(basicRes.data.data || []);

        const existing = declarationRes.data.data.records[0];
        setDeclStatus(existing?.status);
        if (!existing) {
          form.setFieldsValue({ items: [] });
          setFilesByItemKey({});
          return;
        }

        const detail = await getDeclaration(existing.id);
        if (!alive) return;
        setBasicItems(detail.data.data.basicItems || basicRes.data.data || []);
        const nextFiles: Record<string, UploadFile[]> = {};
        const nextItems = (detail.data.data.items || []).map((item, index) => {
          const itemKey = getItemKey(item);
          const uploadFiles = toExistingUploadFiles(item.attachments);
          nextFiles[itemKey] = uploadFiles;
          nextFiles[getItemKey(undefined, index)] = uploadFiles;
          const isCustom = !item.awardId;
          return {
            clientId: itemKey,
            id: item.id,
            category: item.category,
            isCustom,
            awardId: item.awardId,
            levelId: item.levelId,
            customAwardName: isCustom ? item.customAwardName || item.awardName : undefined,
            customLevelName: isCustom ? item.customLevelName || item.levelName : undefined,
            customBaseScore: isCustom ? item.customBaseScore ?? item.finalScore ?? item.computedScore : undefined,
            useDowngrade: item.useDowngrade,
            description: item.description,
          } as ItemFormValue;
        });
        form.setFieldsValue({ items: nextItems });
        setFilesByItemKey(nextFiles);
      } finally {
        if (alive) setInitializing(false);
      }
    };
    load();
    return () => {
      alive = false;
    };
  }, [batchId, form]);

  useEffect(() => {
    if (!activeCategory && defaultCategory) {
      setActiveCategory(defaultCategory);
    }
  }, [defaultCategory, activeCategory]);

  useEffect(() => {
    if (initializing) return;
    const items = form.getFieldValue('items') || [];
    const category = resolveItemCategory();
    if (category && items.length === 0) {
      form.setFieldsValue({
        items: [{ clientId: createClientId(), category, isCustom: false, useDowngrade: 0 }],
      });
    }
  }, [form, initializing, resolveItemCategory]);

  const countByCategory = (cat: string) =>
    itemsWatch.filter((item) => item?.category === cat).length;

  const activeBasicItems = basicItems.filter((item) => item.category === activeCategory);

  const renderBasicItems = () => (
    <div
      style={{
        marginTop: 20,
        border: '1px solid #E6F4FF',
        borderRadius: 8,
        background: '#F8FCFF',
        padding: 16,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <Space>
          <span
            style={{
              width: 8,
              height: 8,
              borderRadius: 4,
              background: getCategoryColor(activeCategory || ''),
              display: 'inline-block',
            }}
          />
          <Typography.Text strong>系统基础分</Typography.Text>
        </Space>
        <Tag color="blue">{activeBasicItems.length} 项</Tag>
      </div>
      {activeBasicItems.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前类别暂无系统基础分" />
      ) : (
        <div style={{ display: 'grid', gap: 10 }}>
          {activeBasicItems.map((item) => (
            <div
              key={item.awardId}
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 120px',
                gap: 12,
                alignItems: 'center',
                padding: '10px 12px',
                border: '1px solid #D6E4FF',
                borderRadius: 8,
                background: '#fff',
              }}
            >
              <div>
                <Typography.Text strong>{item.awardName}</Typography.Text>
                <div style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>管理员导入，只读计分</div>
              </div>
              <InputNumber
                value={Number(item.finalScore ?? item.computedScore ?? 0)}
                precision={1}
                disabled
                addonAfter="分"
                style={{ width: '100%' }}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );

  const getFilesForItem = (item: Partial<ItemFormValue> | undefined, fallback: number | string) => {
    const itemKey = getItemKey(item, fallback);
    const fallbackKey = getItemKey(undefined, fallback);
    return filesByItemKey[itemKey] || filesByItemKey[fallbackKey] || [];
  };

  const setFilesForItem = (item: Partial<ItemFormValue> | undefined, fallback: number | string, fileList: UploadFile[]) => {
    const itemKey = getItemKey(item, fallback);
    const fallbackKey = getItemKey(undefined, fallback);
    setFilesByItemKey((prev) => ({
      ...prev,
      [itemKey]: fileList,
      [fallbackKey]: fileList,
    }));
  };

  const setItemField = (name: number, key: keyof ItemFormValue, value: unknown) => {
    form.setFieldValue(['items', name, key], value);
  };

  const switchToCustom = (name: number) => {
    setItemField(name, 'isCustom', true);
    setItemField(name, 'awardId', undefined);
    setItemField(name, 'levelId', undefined);
    refresh();
  };

  const switchToLibrary = (name: number) => {
    setItemField(name, 'isCustom', false);
    setItemField(name, 'customAwardName', undefined);
    setItemField(name, 'customLevelName', undefined);
    setItemField(name, 'customBaseScore', undefined);
    refresh();
  };

  const buildPayload = (requireAttachments: boolean) => {
    const values = form.getFieldsValue(true);
    const files: File[] = [];
    const items = ((values.items || []) as Array<ItemFormValue | undefined>).map((rawItem, index) => {
      const item = (rawItem || {}) as ItemFormValue;
      const attachments = getFilesForItem(item, index)
        .map((uploadFile) => toAttachmentPayload(uploadFile, files, index))
        .filter((attachment): attachment is AttachmentPayload => Boolean(attachment));
      if (requireAttachments && attachments.length === 0) {
        throw new Error(`第 ${index + 1} 条明细需要上传附件`);
      }
      const isCustom = !!item.isCustom;
      const category = resolveItemCategory(item, index);
      return {
        id: item.id,
        category,
        awardId: isCustom ? undefined : item.awardId,
        levelId: isCustom ? undefined : item.levelId,
        customAwardName: isCustom ? item.customAwardName : undefined,
        customLevelName: isCustom ? item.customLevelName : undefined,
        customBaseScore: isCustom ? item.customBaseScore : undefined,
        useDowngrade: item.useDowngrade ?? 0,
        description: item.description,
        sortOrder: index,
        attachments,
      };
    });
    return { payload: { batchId, items }, files };
  };

  const validateItems = () => {
    const items = ((form.getFieldsValue(true).items || []) as Array<ItemFormValue | undefined>);
    if (items.length === 0) {
      throw new Error('请至少添加一条申报明细');
    }
    items.forEach((rawItem, i) => {
      const item = (rawItem || {}) as ItemFormValue;
      const itemCategory = resolveItemCategory(item, i);
      const focus = () => {
        if (itemCategory) {
          setActiveCategory(itemCategory);
          const peers = items.filter((it, index) => resolveItemCategory(it, index) === itemCategory);
          setIndexByCategory((prev) => ({ ...prev, [itemCategory]: Math.max(0, peers.indexOf(item)) }));
        }
      };
      if (!itemCategory) {
        focus();
        throw new Error(`第 ${i + 1} 条明细缺少类别`);
      }
      if (item.isCustom) {
        if (!item.customAwardName) {
          focus();
          throw new Error(`第 ${i + 1} 条明细请填写奖项/成果名称`);
        }
        if (item.customBaseScore == null) {
          focus();
          throw new Error(`第 ${i + 1} 条明细请填写申报分值`);
        }
      } else {
        if (!item.awardId) {
          focus();
          throw new Error(`第 ${i + 1} 条明细请选择奖项`);
        }
        if (!item.levelId) {
          focus();
          throw new Error(`第 ${i + 1} 条明细请选择等级`);
        }
      }
    });
  };

  const handleSave = async () => {
    setLoading(true);
    try {
      validateItems();
      const { payload, files } = buildPayload(false);
      await saveDeclaration(payload, files);
      message.success('草稿已保存');
      navigate('/declarations');
    } catch (e: any) {
      message.error(e.response?.data?.message || e.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async () => {
    const phase = batch ? getBatchPhase(batch) : 'draft';
    if (!canStudentSubmit(phase, declStatus)) {
      modal.info({
        centered: true,
        title: phase === 'not_started' ? '申报尚未开始' : '申报已截止',
        content:
          phase === 'not_started'
            ? `本批次将于 ${batch?.startDate} 开始，开始后即可提交。`
            : `本批次已于 ${batch?.endDate} 截止，不能再提交。如需修改，请等待审核人退回。`,
      });
      return;
    }
    setSubmitting(true);
    try {
      validateItems();
      const { payload, files } = buildPayload(true);
      await submitDeclarationPayload(payload, files);
      message.success('申报已提交');
      navigate('/declarations');
    } catch (e: any) {
      message.error(e.response?.data?.message || e.message || '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const renderCategorySwitcher = () => (
    <div
      style={{
        display: 'flex',
        gap: 10,
        flexWrap: 'wrap',
        marginBottom: 24,
        paddingBottom: 20,
        borderBottom: '1px solid #F5F5F5',
      }}
    >
      {configuredCategories.map((cat) => {
        const active = activeCategory === cat;
        const hovered = hoverCategory === cat && !active;
        const color = getCategoryColor(cat);
        const count = countByCategory(cat);
        return (
          <button
            key={cat}
            type="button"
            onClick={() => setActiveCategory(cat)}
            onMouseEnter={() => setHoverCategory(cat)}
            onMouseLeave={() => setHoverCategory(undefined)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              padding: '9px 18px',
              borderRadius: 999,
              border: active ? `1px solid ${hexToRgba(color, 0.35)}` : '1px solid #ECECEC',
              background: active ? hexToRgba(color, 0.1) : hovered ? '#F5F5F5' : '#FCFCFC',
              color: active ? color : '#595959',
              fontSize: 14,
              fontWeight: active ? 600 : 500,
              cursor: 'pointer',
              outline: 'none',
              transform: active ? 'translateY(-1px)' : 'none',
              transition: 'all .25s cubic-bezier(.4,0,.2,1)',
            }}
          >
            <span
              style={{
                width: 9,
                height: 9,
                borderRadius: '50%',
                background: color,
                display: 'inline-block',
              }}
            />
            <span>{getCategoryName(cat)}</span>
            <span
              style={{
                minWidth: 20,
                height: 20,
                lineHeight: '20px',
                padding: '0 7px',
                borderRadius: 10,
                fontSize: 12,
                fontWeight: 600,
                textAlign: 'center',
                background: active ? color : '#ECECEC',
                color: active ? '#fff' : '#8C8C8C',
                transition: 'all .25s ease',
              }}
            >
              {count}
            </span>
          </button>
        );
      })}
    </div>
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/declarations')} />
          <div>
            <Typography.Title level={4} style={{ margin: 0, letterSpacing: 0 }}>
              学生申报
            </Typography.Title>
            <Typography.Text type="secondary">
              {batch ? `${batch.name}　开始 ${batch.startDate} · 截止 ${batch.endDate}` : '加载批次信息中'}
            </Typography.Text>
          </div>
        </Space>
        <Space>
          <Button icon={<SaveOutlined />} loading={loading} onClick={handleSave} disabled={initializing || !hasConfiguredCategories}>
            保存草稿
          </Button>
          <Button type="primary" icon={<SendOutlined />} loading={submitting} onClick={handleSubmit} disabled={initializing || !hasConfiguredCategories}>
            提交审核
          </Button>
        </Space>
      </div>

      <Card styles={{ body: { padding: 24 } }}>
        {configuredCategories.length > 0 && renderCategorySwitcher()}

        <Form form={form} layout="vertical" initialValues={{ items: [] }}>
          <Form.List name="items">
            {(fields, { add, remove }) => {
              const visibleFields = fields.filter(
                (f) => resolveItemCategory(form.getFieldValue(['items', f.name]), f.name) === activeCategory
              );
              const total = visibleFields.length;
              const pos = Math.min(Math.max(0, indexByCategory[activeCategory || ''] ?? 0), Math.max(0, total - 1));
              const currentField = visibleFields[pos];

              const handleAdd = () => {
                const category = resolveItemCategory();
                if (!category) return;
                add({ clientId: createClientId(), category, isCustom: false, useDowngrade: 0 });
                setIndexByCategory((prev) => ({ ...prev, [category]: total }));
              };
              const handleRemove = () => {
                if (!currentField) return;
                const currentItem = (itemsWatch[currentField.name] || {}) as ItemFormValue;
                const currentItemKey = getItemKey(currentItem, currentField.name);
                remove(currentField.name);
                setFilesByItemKey((prev) => {
                  const next = { ...prev };
                  delete next[currentItemKey];
                  delete next[getItemKey(undefined, currentField.name)];
                  return next;
                });
                setIndexByCategory((prev) => ({ ...prev, [activeCategory || '']: Math.max(0, pos - 1) }));
              };
              const goPrev = () =>
                setIndexByCategory((prev) => ({ ...prev, [activeCategory || '']: Math.max(0, pos - 1) }));
              const goNext = () =>
                setIndexByCategory((prev) => ({ ...prev, [activeCategory || '']: Math.min(total - 1, pos + 1) }));

              if (total === 0) {
                return (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={`${activeCategory ? getCategoryName(activeCategory) : '该类别'}暂无申报明细`}
                    style={{ padding: '32px 0' }}
                  >
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd} disabled={!hasConfiguredCategories}>
                      新增奖项
                    </Button>
                  </Empty>
                );
              }

              const name = currentField.name;
              const item = (itemsWatch[name] || {}) as ItemFormValue;
              const itemKey = getItemKey(item, name);
              const currentFiles = getFilesForItem(item, name);
              const isCustom = !!form.getFieldValue(['items', name, 'isCustom']);
              const award = getAwardById(item.awardId);
              const score = getLevelScore(item.awardId, item.levelId);
              const awardOptions = (awardsByCategory.get(activeCategory || '') || []).map((a) => ({
                value: a.id,
                label: a.name,
              }));
              const levelOptions = (award?.levelScores || []).map((ls) => ({
                value: ls.levelId,
                label: `${ls.levelName}（${ls.baseScore} 分）`,
              }));

              return (
                <div key={itemKey}>
                  <Form.Item name={[name, 'id']} hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name={[name, 'clientId']} hidden>
                    <Input />
                  </Form.Item>
                  <Form.Item name={[name, 'category']} hidden>
                    <Input />
                  </Form.Item>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: 14,
                    }}
                  >
                    <Space>
                      <span
                        style={{
                          width: 8,
                          height: 8,
                          borderRadius: 4,
                          background: getCategoryColor(activeCategory || ''),
                          display: 'inline-block',
                        }}
                      />
                      <Typography.Text strong>
                        申报明细 {pos + 1} / {total}
                      </Typography.Text>
                    </Space>
                    <Space>
                      <Button icon={<LeftOutlined />} disabled={pos === 0} onClick={goPrev} />
                      <Button icon={<RightOutlined />} disabled={pos >= total - 1} onClick={goNext} />
                      <Button type="dashed" icon={<PlusOutlined />} onClick={handleAdd} disabled={!hasConfiguredCategories}>
                        新增奖项
                      </Button>
                      <Button danger icon={<DeleteOutlined />} onClick={handleRemove}>
                        删除
                      </Button>
                    </Space>
                  </div>

                  <div
                    style={{
                      border: '1px solid #F0F0F0',
                      borderRadius: 8,
                      padding: 18,
                      background: '#fff',
                    }}
                  >
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 160px', gap: 16 }}>
                      <Form.Item label="奖项/成果名称" required>
                        {isCustom ? (
                          <div>
                            <Form.Item name={[name, 'customAwardName']} noStyle>
                              <Input placeholder="请输入奖项或成果名称" />
                            </Form.Item>
                            <Button
                              type="link"
                              size="small"
                              style={{ padding: 0, marginTop: 4 }}
                              onClick={() => switchToLibrary(name)}
                            >
                              选择已有奖项
                            </Button>
                          </div>
                        ) : (
                          <Form.Item name={[name, 'awardId']} noStyle>
                            <Select
                              showSearch
                              optionFilterProp="label"
                              placeholder="选择已有奖项"
                              options={awardOptions}
                              notFoundContent="该类别暂无奖项库，可新增自定义"
                              onChange={() => {
                                setItemField(name, 'levelId', undefined);
                                refresh();
                              }}
                              popupRender={(menu) => (
                                <>
                                  {menu}
                                  <Divider style={{ margin: '4px 0' }} />
                                  <Button
                                    type="text"
                                    icon={<PlusOutlined />}
                                    block
                                    style={{ textAlign: 'left' }}
                                    onMouseDown={(e) => e.preventDefault()}
                                    onClick={() =>
                                      modal.confirm({
                                        centered: true,
                                        title: '警告',
                                        content: '请先与管理员确认后再使用此功能！未经许可的提交将被自动驳回。',
                                        okText: '我已知晓，继续',
                                        cancelText: '取消',
                                        onOk: () => switchToCustom(name),
                                      })
                                    }
                                  >
                                    新增自定义
                                  </Button>
                                </>
                              )}
                            />
                          </Form.Item>
                        )}
                      </Form.Item>

                      <Form.Item label="等级" required={!isCustom}>
                        {isCustom ? (
                          <Form.Item name={[name, 'customLevelName']} noStyle>
                            <Input placeholder="如：校级一等奖" />
                          </Form.Item>
                        ) : (
                          <Form.Item name={[name, 'levelId']} noStyle>
                            <Select
                              placeholder={item.awardId ? '请选择等级' : '请先选择奖项'}
                              disabled={!item.awardId}
                              options={levelOptions}
                              onChange={refresh}
                            />
                          </Form.Item>
                        )}
                      </Form.Item>

                      <Form.Item label="申报分值" required>
                        {isCustom ? (
                          <Form.Item name={[name, 'customBaseScore']} noStyle>
                            <InputNumber min={0} precision={1} style={{ width: '100%' }} />
                          </Form.Item>
                        ) : (
                          <InputNumber
                            value={score}
                            disabled
                            precision={1}
                            style={{ width: '100%' }}
                            placeholder="选择等级后自动带出"
                          />
                        )}
                      </Form.Item>
                    </div>

                    <Form.Item name={[name, 'description']} label="说明">
                      <Input.TextArea rows={2} placeholder="补充说明材料内容、获奖时间或证明信息" />
                    </Form.Item>

                    <Form.Item label="附件">
                      <Upload
                        multiple
                        accept="image/*,.pdf,application/pdf"
                        showUploadList={false}
                        beforeUpload={(file) => {
                          if (!isAllowedAttachment(file as File)) {
                            message.error('仅支持图片或 PDF 附件');
                            return Upload.LIST_IGNORE;
                          }
                          return false;
                        }}
                        fileList={currentFiles}
                        onChange={({ fileList }) => setFilesForItem(item, name, fileList)}
                        onPreview={previewUploadFile}
                      >
                        <Button icon={<UploadOutlined />}>选择附件</Button>
                      </Upload>
                      {currentFiles.length > 0 && (
                        <div style={{ marginTop: 12, display: 'grid', gap: 8 }}>
                          {currentFiles.map((file) => (
                            <div
                              key={file.uid}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                gap: 12,
                                padding: '8px 10px',
                                border: '1px solid #F0F0F0',
                                borderRadius: 8,
                                background: '#FAFAFA',
                              }}
                            >
                              <a
                                onClick={() => previewUploadFile(file)}
                                title={file.name}
                                style={{
                                  cursor: 'pointer',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 6,
                                  minWidth: 0,
                                  maxWidth: 'calc(100% - 44px)',
                                  height: 24,
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                <UploadOutlined />
                                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{file.name}</span>
                              </a>
                              <Button
                                type="text"
                                size="small"
                                danger
                                icon={<DeleteOutlined />}
                                onClick={() =>
                                  setFilesForItem(
                                    item,
                                    name,
                                    currentFiles.filter((itemFile) => itemFile.uid !== file.uid)
                                  )
                                }
                              />
                            </div>
                          ))}
                        </div>
                      )}
                    </Form.Item>
                  </div>
                </div>
              );
            }}
          </Form.List>
        </Form>
        {renderBasicItems()}
      </Card>
    </div>
  );
};

export default DeclarationForm;
