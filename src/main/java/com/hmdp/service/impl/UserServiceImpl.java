package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        // 2. 不符合，返回错误信息
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式错误");

        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码到session
        session.setAttribute("code", code);
        // 5. 发送验证码
        log.debug("发送验证码成功，{}",code);

        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 检验手机号和验证码
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式错误");

        Object cachecode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cachecode != null && cachecode.equals(code)){
            // 一致，根据手机号查询用户 select * from tb_user where phone = ?
            User user = query().eq("phone", phone).one();
            if(user == null){
                // 不存在，创建新用户
                user = createUserWithPhone(phone);
            }
            // 用户存在
            // 保存用户到session
            session.setAttribute("user", user);
        }else{
            // 不一致，报错
            return Result.fail("手机验证码错误");
        }

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User().setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
