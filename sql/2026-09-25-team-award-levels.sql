-- Add independent team options at half the current score for four awards.
-- Existing levels, scores, declarations, and other awards are left untouched.
-- Safe to run again: existing active team levels and scores are skipped.
USE `eval_system`;

START TRANSACTION;

CREATE TEMPORARY TABLE `team_award_source` AS
SELECT a.`id` AS `award_id`, d.`name` AS `level_name`,
       d.`sort_order`, s.`base_score`
FROM `award` a
JOIN `award_level_score` s ON s.`award_id` = a.`id` AND s.`is_deleted` = 0
JOIN `award_level_def` d ON d.`id` = s.`level_id` AND d.`is_deleted` = 0
WHERE a.`is_deleted` = 0
  AND a.`award_type` = 'normal'
  AND (
    (a.`category` = 'morality' AND a.`name` IN ('辩论/心理剧/手语比赛等', '通报表扬'))
    OR (a.`category` = 'ability' AND a.`name` = '学科竞赛')
    OR (a.`category` = 'sports' AND a.`name` = '体育活动与竞赛')
  )
  AND (d.`award_id` IS NULL OR d.`award_id` = a.`id`)
  AND d.`name` NOT LIKE '%（团队）';

INSERT INTO `award_level_def` (`id`, `code`, `name`, `award_id`, `sort_order`)
SELECT UUID(), CONCAT('team_', REPLACE(UUID(), '-', '')),
       CONCAT(src.`level_name`, '（团队）'), src.`award_id`, 1000 + src.`sort_order`
FROM `team_award_source` src
WHERE NOT EXISTS (
  SELECT 1 FROM `award_level_def` team
  WHERE team.`award_id` = src.`award_id`
    AND team.`name` = CONCAT(src.`level_name`, '（团队）')
    AND team.`is_deleted` = 0
);

INSERT INTO `award_level_score` (`id`, `award_id`, `level_id`, `base_score`)
SELECT UUID(), src.`award_id`, team.`id`, CAST(src.`base_score` / 2 AS DECIMAL(8,2))
FROM `team_award_source` src
JOIN `award_level_def` team
  ON team.`award_id` = src.`award_id`
  AND team.`name` = CONCAT(src.`level_name`, '（团队）')
  AND team.`is_deleted` = 0
WHERE NOT EXISTS (
  SELECT 1 FROM `award_level_score` score
  WHERE score.`award_id` = src.`award_id`
    AND score.`level_id` = team.`id`
    AND score.`is_deleted` = 0
);

COMMIT;
DROP TEMPORARY TABLE `team_award_source`;
