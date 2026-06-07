import React, { useEffect, useState } from "react";
import { Layout, Menu, Dropdown, Avatar, Space, Badge } from "antd";
import {
  HomeOutlined,
  FileTextOutlined,
  AuditOutlined,
  SettingOutlined,
  TrophyOutlined,
  TeamOutlined,
  FileSearchOutlined,
  BellOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from "@ant-design/icons";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import ProfileModal from "../components/ProfileModal";
import UnreadNoticeModal from "../components/UnreadNoticeModal";
import { getUnconfirmedNoticeCount } from "../api/notice";

const { Sider, Header, Content } = Layout;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [noticeCount, setNoticeCount] = useState(0);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuthStore();

  const loadNoticeCount = async () => {
    try {
      const res = await getUnconfirmedNoticeCount();
      setNoticeCount(res.data.data || 0);
    } catch {
      setNoticeCount(0);
    }
  };

  useEffect(() => {
    loadNoticeCount();
    window.addEventListener("notice-count-refresh", loadNoticeCount);
    return () =>
      window.removeEventListener("notice-count-refresh", loadNoticeCount);
  }, [user?.id]);

  const menuItems = React.useMemo(() => {
    const role = user?.role;
    const items: any[] = [
      { key: "/", icon: <HomeOutlined />, label: "首页概览" },
      {
        key: "/notices",
        icon: <BellOutlined />,
        label: (
          <Badge count={noticeCount} size="small" offset={[10, 0]}>
            <span>通知公告</span>
          </Badge>
        ),
      },
    ];

    if (role === "student") {
      items.push({
        key: "/declarations",
        icon: <FileTextOutlined />,
        label: "我的申报",
      });
    }
    if (role === "teacher" || role === "admin") {
      items.push({ key: "/audit", icon: <AuditOutlined />, label: "审核管理" });
    }
    if (role === "admin") {
      items.push(
        { key: "/batches", icon: <SettingOutlined />, label: "批次管理" },
        { key: "/awards", icon: <TrophyOutlined />, label: "奖项库" },
        { key: "/users", icon: <TeamOutlined />, label: "用户管理" },
        { key: "/operation-logs", icon: <FileSearchOutlined />, label: "操作记录" },
      );
    }
    return items;
  }, [user?.role, noticeCount]);

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const dropdownItems = {
    items: [
      { key: "profile", icon: <UserOutlined />, label: "个人信息" },
      { type: "divider" as const },
      {
        key: "logout",
        icon: <LogoutOutlined />,
        label: "退出登录",
        danger: true,
      },
    ],
    onClick: ({ key }: { key: string }) => {
      if (key === "profile") setProfileOpen(true);
      if (key === "logout") handleLogout();
    },
  };

  const roleLabels: Record<string, string> = {
    admin: "管理员",
    teacher: "教师",
    student: "学生",
  };

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={220}
        style={{
          background: "#fff",
          borderRight: "1px solid #F0F0F0",
          position: "fixed",
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
        }}
      >
        <div
          style={{
            height: 64,
            display: "flex",
            alignItems: "center",
            justifyContent: collapsed ? "center" : "flex-start",
            padding: collapsed ? 0 : "0 20px",
            borderBottom: "1px solid #F0F0F0",
          }}
        >
          <img
            src="/logo.jpg"
            alt="综合测评系统"
            style={{
              width: 32,
              height: 32,
              borderRadius: 8,
              display: "block",
              objectFit: "cover",
            }}
          />
          {!collapsed && (
            <span
              style={{
                marginLeft: 10,
                fontSize: 16,
                fontWeight: 600,
                color: "rgba(0,0,0,0.88)",
              }}
            >
              综合测评系统
            </span>
          )}
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ border: "none", marginTop: 8 }}
        />
      </Sider>
      <Layout
        style={{
          marginLeft: collapsed ? 64 : 220,
          transition: "margin-left 0.2s",
        }}
      >
        <Header
          style={{
            background: "#fff",
            padding: "0 32px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            borderBottom: "1px solid #F0F0F0",
            position: "sticky",
            top: 0,
            zIndex: 99,
            height: 64,
          }}
        >
          <div
            style={{ cursor: "pointer", fontSize: 18 }}
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </div>
          <Dropdown menu={dropdownItems}>
            <Space style={{ cursor: "pointer" }}>
              <Avatar style={{ background: "#1677FF" }} size={36}>
                {user?.name?.charAt(0)}
              </Avatar>
              <div style={{ lineHeight: 1.2 }}>
                <div style={{ fontSize: 14, fontWeight: 500 }}>
                  {user?.name}
                </div>
                <div style={{ fontSize: 12, color: "rgba(0,0,0,0.45)" }}>
                  {roleLabels[user?.role || ""]}
                </div>
              </div>
            </Space>
          </Dropdown>
        </Header>
        <Content
          style={{
            padding: 32,
            background: "#F7F8FA",
            minHeight: "calc(100vh - 64px)",
          }}
        >
          <Outlet />
        </Content>
      </Layout>
      <ProfileModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      <UnreadNoticeModal userId={user?.id} />
    </Layout>
  );
};

export default MainLayout;
