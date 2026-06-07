package com.eval.jxj.service;

import com.eval.jxj.common.exception.BizException;
import com.eval.jxj.entity.SysConfig;
import com.eval.jxj.mapper.SysConfigMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统统一二级密码：用于删除操作记录等高危动作的二次校验。
 * 哈希存储于 sys_config 表，key = secondary_password。
 * 若不存在则懒初始化为默认值 DEFAULT_SECONDARY_PASSWORD。
 */
@Service
public class SecondaryPasswordService {

    public static final String CONFIG_KEY = "secondary_password";
    public static final String DEFAULT_SECONDARY_PASSWORD = "Sec@123456";

    private final SysConfigMapper configMapper;
    private final PasswordEncoder passwordEncoder;

    public SecondaryPasswordService(SysConfigMapper configMapper, PasswordEncoder passwordEncoder) {
        this.configMapper = configMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 取当前二级密码哈希，若缺失则写入默认值后返回。 */
    private synchronized String getOrInitHash() {
        SysConfig config = configMapper.selectById(CONFIG_KEY);
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(CONFIG_KEY);
            config.setConfigValue(passwordEncoder.encode(DEFAULT_SECONDARY_PASSWORD));
            configMapper.insert(config);
        }
        return config.getConfigValue();
    }

    /** 校验二级密码，不匹配抛业务异常。 */
    public void verify(String raw) {
        if (!StringUtils.hasText(raw) || !passwordEncoder.matches(raw, getOrInitHash())) {
            throw new BizException("二级密码错误");
        }
    }

    /** 修改二级密码（需校验旧密码）。 */
    public void change(String oldRaw, String newRaw) {
        verify(oldRaw);
        if (!StringUtils.hasText(newRaw) || newRaw.length() < 6) {
            throw new BizException("新二级密码长度不能少于6位");
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(CONFIG_KEY);
        config.setConfigValue(passwordEncoder.encode(newRaw));
        configMapper.updateById(config);
    }
}
