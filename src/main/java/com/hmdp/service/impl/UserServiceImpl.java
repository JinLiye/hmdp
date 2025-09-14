package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        // 2. 不符合，返回错误信息
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("手机号格式错误");

        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码到redis  // set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

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

        // 从redis获取验证码
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cachecode != null && cachecode.equals(code)){
            // 一致，根据手机号查询用户 select * from tb_user where phone = ?
            User user = query().eq("phone", phone).one();
            if(user == null){
                // 不存在，创建新用户
                user = createUserWithPhone(phone);
            }
            // 用户存在
            // 保存用户到redis
            // - 生成一个token所谓登陆令牌
            String token = UUID.randomUUID().toString(true);
            // - 将User对象转换为Hash存储
            UserDTO userDTO = new UserDTO();
            BeanUtil.copyProperties(user, userDTO);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
            Object id = userMap.get("id");
            if (id != null) {
                userMap.put("id", id.toString());
            }
            // - 存储
            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
            // 设置有效期
            stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.SECONDS);
            return Result.ok(token);
        }else{
            // 不一致，报错
            return Result.fail("手机验证码错误");
        }


    }

    private User createUserWithPhone(String phone) {
        User user = new User().setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomNumbers(10));
        save(user);
        return user;
    }
}
