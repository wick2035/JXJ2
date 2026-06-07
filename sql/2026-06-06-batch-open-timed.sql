-- 批次状态扩展为四态：草稿 / 按日期开放 / 长期开放 / 已截止
-- open_timed = 按起止日期自动开放与截止；open = 长期开放（不按日期，发布即一直可提交）
-- 现有 open 行保持"长期开放"语义，无需数据修正。
ALTER TABLE `eval_batch`
  MODIFY `status` ENUM('draft','open_timed','open','closed') NOT NULL DEFAULT 'draft';
