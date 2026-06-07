import React from 'react';
import { createBrowserRouter, Navigate, useLocation } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import LoginPage from '../pages/login/LoginPage';
import ForceChangePasswordPage from '../pages/account/ForceChangePasswordPage';
import Dashboard from '../pages/student/Dashboard';
import DeclarationList from '../pages/student/DeclarationList';
import DeclarationForm from '../pages/student/DeclarationForm';
import DeclarationDetail from '../pages/student/DeclarationDetail';
import ReviewQueue from '../pages/teacher/ReviewQueue';
import ReviewDetail from '../pages/teacher/ReviewDetail';
import BatchManagement from '../pages/admin/BatchManagement';
import AwardLibrary from '../pages/admin/AwardLibrary';
import UserManagement from '../pages/admin/UserManagement';
import OperationLog from '../pages/admin/OperationLog';
import NoticeCenter from '../pages/notice/NoticeCenter';
import { useAuthStore } from '../store/authStore';

const AuthGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const location = useLocation();
  if (!token) return <Navigate to="/login" replace />;
  const mustChangePassword = user?.forcePasswordChange === true || user?.forcePasswordChange === 1;
  if (mustChangePassword && location.pathname !== '/force-change-password') {
    return <Navigate to="/force-change-password" replace />;
  }
  if (!mustChangePassword && location.pathname === '/force-change-password') {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
};

const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/force-change-password',
    element: (
      <AuthGuard>
        <ForceChangePasswordPage />
      </AuthGuard>
    ),
  },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Dashboard /> },
      { path: 'declarations', element: <DeclarationList /> },
      { path: 'declarations/:id', element: <DeclarationDetail /> },
      { path: 'declare/:batchId', element: <DeclarationForm /> },
      { path: 'audit', element: <ReviewQueue /> },
      { path: 'audit/:id', element: <ReviewDetail /> },
      { path: 'notices', element: <NoticeCenter /> },
      { path: 'batches', element: <BatchManagement /> },
      { path: 'awards', element: <AwardLibrary /> },
      { path: 'users', element: <UserManagement /> },
      { path: 'operation-logs', element: <OperationLog /> },
    ],
  },
]);

export default router;
