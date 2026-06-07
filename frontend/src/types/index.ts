export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
}

export interface UserVO {
  id: string;
  loginId: string;
  forcePasswordChange?: boolean | number;
  status?: number;
  failedAttempts?: number;
  name: string;
  role: 'admin' | 'teacher' | 'student';
  email?: string;
  phone?: string;
  college?: string;
  major?: string;
  className?: string;
  grade?: string;
}

export interface OperationLogVO {
  id: string;
  operatorId?: string;
  operatorLoginId?: string;
  operatorName?: string;
  operatorRole?: 'admin' | 'teacher' | 'student';
  module?: string;
  action?: string;
  method?: string;
  uri?: string;
  params?: string;
  ip?: string;
  success?: number;
  errorMsg?: string;
  createdAt: string;
}

export interface UserImportResult {
  successCount: number;
  skippedCount: number;
  failedCount: number;
  errors: UserImportRowError[];
}

export interface UserImportRowError {
  row: number;
  loginId?: string;
  reason: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  user: UserVO;
}

export interface BatchVO {
  id: string;
  name: string;
  status: 'draft' | 'open_timed' | 'open' | 'closed';
  targetType?: 'all' | 'specified';
  startDate: string;
  endDate: string;
  description?: string;
  categories: CategoryVO[];
  reviewers?: BatchReviewerVO[];
  targetClasses?: ClassTargetVO[];
  reviewerCount?: number;
  declarationCount: number;
  pendingAuditCount?: number;
  approvedCount?: number;
  submittedStudentCount?: number;
  eligibleStudentCount?: number;
  pendingReviewCount?: number;
}

export interface ClassTargetVO {
  grade?: string;
  className: string;
}

export interface ClassOptionVO {
  grade?: string;
  className: string;
  college?: string;
  major?: string;
  studentCount?: number;
}

export interface BatchReviewerVO {
  id: string;
  loginId: string;
  name: string;
}

export interface CategoryVO {
  id: string;
  category: string;
  weightPercent: number;
  maxScoreCap?: number;
}

export interface CategoryMeta {
  id: string;
  code: string;
  name: string;
  color: string;
  sortOrder: number;
}

export interface AwardLevelDef {
  id: string;
  code: string;
  name: string;
  sortOrder: number;
}

export interface AwardVO {
  id: string;
  category: string;
  name: string;
  awardType?: 'normal' | 'basic';
  description?: string;
  levelScores: LevelScoreVO[];
}

export interface LevelScoreVO {
  id: string;
  levelId: string;
  levelName: string;
  levelCode: string;
  sortOrder: number;
  baseScore: number;
}

export interface DeclarationVO {
  id: string;
  batchId: string;
  batchName: string;
  studentId: string;
  studentName: string;
  studentLoginId: string;
  status: 'draft' | 'submitted' | 'approved' | 'rejected' | 'returned';
  stage?: DeclarationStage;
  canWithdraw?: boolean;
  totalScore?: number;
  moralityScore?: number;
  abilityScore?: number;
  sportsScore?: number;
  categoryScores?: CategoryScoreVO[];
  submittedAt?: string;
  createdAt: string;
  classRank?: number;
  classRankTotal?: number;
  items?: DeclarationItemVO[];
  basicItems?: BasicItemVO[];
  auditRecords?: AuditRecordVO[];
}

export type DeclarationStage =
  | 'pending_submit'
  | 'submitted_unassigned'
  | 'assigned'
  | 'reviewing'
  | 'approved'
  | 'rejected';

export const STAGE_LABELS: Record<DeclarationStage, string> = {
  pending_submit: '待提交',
  submitted_unassigned: '已提交·等待分配审批人',
  assigned: '已分配审批人·待审核',
  reviewing: '正在审核中',
  approved: '已通过',
  rejected: '已驳回',
};

export const STAGE_STEPS = ['pending_submit', 'submitted_unassigned', 'assigned', 'reviewing', 'result'] as const;

export interface CategoryScoreVO {
  category: string;
  rawScore: number;
}

export interface DeclarationItemVO {
  id: string;
  category: string;
  awardId?: string;
  awardName?: string;
  levelId?: string;
  levelName?: string;
  customAwardName?: string;
  customLevelName?: string;
  customBaseScore?: number;
  useDowngrade: number;
  computedScore?: number;
  finalScore?: number;
  description?: string;
  sortOrder: number;
  attachments?: AttachmentVO[];
}

export interface BasicItemVO {
  awardId: string;
  awardName: string;
  category: string;
  computedScore?: number;
  finalScore?: number;
  source?: 'basic';
}

export interface AttachmentVO {
  id: string;
  fileName: string;
  filePath: string;
  fileSize?: number;
  mimeType?: string;
}

export interface DeclarationAttachmentForm {
  id?: string;
  localId?: string;
  fileName: string;
  filePath?: string;
  fileSize?: number;
  mimeType?: string;
  file?: File;
}

export interface AuditRecordVO {
  id: string;
  reviewerId: string;
  reviewerName: string;
  action: 'approve' | 'reject' | 'return' | 'correction_approve' | 'correction_reject' | 'correction_return';
  comment?: string;
  snapshotScores?: string;
  createdAt: string;
}

export interface BatchStatsVO {
  batchId: string;
  totalDeclarations: number;
  draftCount: number;
  submittedCount: number;
  approvedCount: number;
  rejectedCount: number;
  returnedCount: number;
  pendingAuditCount: number;
  finishedAuditCount: number;
  averageScore?: number;
  maxScore?: number;
  minScore?: number;
  auditProgress: number;
}

export interface BatchRankingVO {
  rank: number;
  declarationId: string;
  studentId: string;
  studentLoginId: string;
  studentName: string;
  totalScore?: number;
  moralityScore?: number;
  abilityScore?: number;
  sportsScore?: number;
  categoryScores?: Record<string, number>;
  submittedAt?: string;
}

export interface BatchEvaluationTableVO {
  categories: BatchEvaluationCategoryColumn[];
  rows: BatchEvaluationStudentRow[];
}

export interface BatchEvaluationCategoryColumn {
  code: string;
  name: string;
  color?: string;
  awards: BatchEvaluationAwardColumn[];
}

export interface BatchEvaluationAwardColumn {
  awardId: string;
  name: string;
  custom?: boolean;
}

export interface BatchEvaluationStudentRow {
  studentId?: string;
  studentLoginId: string;
  studentName: string;
  scores: Record<string, number>;
  subtotals: Record<string, number>;
}

export interface BatchDetailRowVO {
  declarationId: string;
  studentLoginId: string;
  studentName: string;
  status: string;
  category: string;
  awardName?: string;
  levelName?: string;
  source?: 'student' | 'basic';
  computedScore?: number;
  finalScore?: number;
  description?: string;
}

export interface BasicAwardImportResult {
  successCount: number;
  failedCount: number;
  projectCount: number;
  studentCount: number;
  errors: BasicAwardImportRowError[];
}

export interface BasicAwardImportRowError {
  row: number;
  loginId?: string;
  project?: string;
  reason: string;
}

export interface BatchBasicAwardVO {
  awardId: string;
  awardName: string;
  category: string;
  importedCount?: number;
  updatedAt?: string;
  scores?: BatchBasicAwardStudentScoreVO[];
}

export interface BatchBasicAwardStudentScoreVO {
  studentId: string;
  studentLoginId: string;
  studentName: string;
  score: number;
}

export interface AuditAssignmentVO {
  id: string;
  declarationId: string;
  studentLoginId: string;
  studentName: string;
  reviewerId: string;
  reviewerName: string;
  status: 'pending' | 'approved' | 'rejected' | 'returned' | 'cancelled';
  action?: string;
  comment?: string;
  reviewedAt?: string;
  createdAt?: string;
}

export interface BatchAssignmentGenerateVO {
  declarationCount: number;
  assignmentCount: number;
}

export interface BatchAssignmentGenerateRequest {
  replacePending?: boolean;
  reviewerId?: string;
  count?: number;
}

export interface AuditQueueStatsVO {
  totalSubmitted: number;
  myPending: number;
  assignedPending: number;
  unassigned: number;
  finishedTotal: number;
  finishedMine: number;
  finishedApproved: number;
  finishedRejected: number;
}

export interface NoticeRecipientVO {
  id: string;
  loginId?: string;
  name?: string;
  role?: 'admin' | 'teacher' | 'student';
  confirmed: number;
  confirmedAt?: string;
}

export interface NoticeVO {
  id: string;
  title: string;
  content: string;
  targetType: 'all' | 'specified';
  status: 'published' | 'withdrawn';
  createdBy: string;
  creatorName?: string;
  recipientCount: number;
  confirmedCount: number;
  unconfirmedCount: number;
  confirmed?: number;
  confirmedAt?: string;
  withdrawnAt?: string;
  createdAt: string;
  updatedAt: string;
  recipientUserIds?: string[];
  recipients?: NoticeRecipientVO[];
}

export interface NoticeSaveRequest {
  title: string;
  content: string;
  targetType: 'all' | 'specified';
  recipientUserIds?: string[];
}

export interface DeclarationItemForm {
  id?: string;
  tempId: string;
  category: string;
  isCustom: boolean;
  awardId?: string;
  levelId?: string;
  customAwardName?: string;
  customLevelName?: string;
  customBaseScore?: number;
  useDowngrade: number;
  description?: string;
  sortOrder: number;
  computedScore: number;
  attachments?: DeclarationAttachmentForm[];
}

export const STATUS_LABELS: Record<string, string> = {
  draft: '草稿',
  submitted: '已提交',
  approved: '已通过',
  rejected: '已驳回',
  returned: '已退回',
  open_timed: '定时开放',
  open: '手动开放',
  closed: '已截止',
};

export const STATUS_COLORS: Record<string, string> = {
  draft: 'default',
  submitted: 'processing',
  approved: 'success',
  rejected: 'error',
  returned: 'warning',
  open_timed: 'processing',
  open: 'processing',
  closed: 'default',
};
