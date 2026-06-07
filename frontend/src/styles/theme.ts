import type { ThemeConfig } from 'antd';

const theme: ThemeConfig = {
  token: {
    colorPrimary: '#1677FF',
    colorSuccess: '#52C41A',
    colorWarning: '#FAAD14',
    colorError: '#FF4D4F',
    colorBgLayout: '#F7F8FA',
    borderRadius: 8,
    borderRadiusLG: 12,
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif",
    fontSize: 14,
    controlHeight: 40,
    controlHeightLG: 44,
  },
  components: {
    Card: { paddingLG: 24, borderRadiusLG: 12 },
    Table: { headerBg: '#FAFAFA', borderColor: '#F0F0F0' },
    Menu: { itemBg: 'transparent', itemSelectedBg: '#E6F4FF', itemSelectedColor: '#1677FF' },
    Button: { borderRadius: 8, controlHeight: 40, controlHeightLG: 44 },
    Input: { borderRadius: 8, controlHeight: 40 },
    Select: { borderRadius: 8, controlHeight: 40 },
  },
};

export default theme;
