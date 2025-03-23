package com.lamngo.mealsync.application.service;

import com.lamngo.mealsync.application.dto.user.UserCreateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.domain.model.User;
import com.lamngo.mealsync.domain.repository.IUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService implements IUserService {

    @Autowired
    private IUserRepo _iUserRepo;

    @Autowired
    UserMapper _userMapper;

    @Override
    public UserReadDto register(UserCreateDto userCreateDto) {
        User user = _userMapper.toUser(userCreateDto);
        User savedUser = _iUserRepo.save(user);
        return _userMapper.toUserReadDto(savedUser);
    }

    @Override
    public UserReadDto login(String email, String password) {
        return null;
    }

    @Override
    public List<UserReadDto> getAllUsers() {
        List<User> users = _iUserRepo.findAll();
        return users.stream()
                .map(_userMapper::toUserReadDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserReadDto findUserById(String id) {
        User user = _iUserRepo.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public UserReadDto findUserByEmail(String email) {
        User user = _iUserRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public void deleteById(String id) {
        _iUserRepo.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        _iUserRepo.deleteById(id);
    }
}
