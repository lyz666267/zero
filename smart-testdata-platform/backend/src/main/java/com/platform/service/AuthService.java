package com.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.dto.LoginRequest;
import com.platform.dto.LoginResponse;
import com.platform.dto.RegisterRequest;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.mapper.UserMapper;
import com.platform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     */
    public LoginResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setEnabled(true);
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return new LoginResponse(token, user.getUsername(), user.getNickname());
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            throw new BusinessException("账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return new LoginResponse(token, user.getUsername(), user.getNickname());
    }

    /**
     * 根据 ID 获取用户信息
     */
    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }
}
