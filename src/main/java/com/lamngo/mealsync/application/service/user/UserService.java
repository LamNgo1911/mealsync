package com.lamngo.mealsync.application.service.user;

import com.lamngo.mealsync.application.dto.user.UserPreferenceReadDto;
import com.lamngo.mealsync.application.dto.user.UserPreferenceUpdateDto;
import com.lamngo.mealsync.application.dto.user.UserReadDto;
import com.lamngo.mealsync.application.dto.user.UserUpdateDto;
import com.lamngo.mealsync.application.mapper.user.UserMapper;
import com.lamngo.mealsync.application.mapper.user.UserPreferenceMapper;
import com.lamngo.mealsync.domain.model.user.User;
import com.lamngo.mealsync.domain.model.user.UserPreference;
import com.lamngo.mealsync.domain.repository.user.IUserRepo;
import com.lamngo.mealsync.presentation.error.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService implements IUserService {

    private final IUserRepo _iUserRepo;

    private final UserPreferenceMapper _userPreferenceMapper;

    @Autowired
    UserMapper _userMapper;

    public UserService(IUserRepo iUserRepo, UserPreferenceMapper userPreferenceMapper) {
        this._iUserRepo = iUserRepo;
        this._userPreferenceMapper = userPreferenceMapper;
    }


    @Override
    public List<UserReadDto> getAllUsers() {
        List<User> users = _iUserRepo.findAll();
        return users.stream()
                .map(_userMapper::toUserReadDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserReadDto findUserById(UUID id) {
        User user = _iUserRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public UserReadDto findUserByEmail(String email) {
        User user = _iUserRepo.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return _userMapper.toUserReadDto(user);
    }

    @Override
    public void deleteById(UUID id) {
        _iUserRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        _iUserRepo.deleteById(id);
    }

    @Override
    public UserReadDto updateUser(UUID id, UserUpdateDto userUpdateDto) {
        User user = _iUserRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        user.setName(userUpdateDto.getName());

        User updatedUser = _iUserRepo.save(user);
        return _userMapper.toUserReadDto(updatedUser);
    }

    @Override
    @Transactional
    public UserPreferenceReadDto updateUserPreferencesById(UUID userId, UserPreferenceUpdateDto preferenceUpdateDto) {
        User user = _iUserRepo.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserPreference userPreference = user.getUserPreference();
        if (userPreference == null) {
           throw new ResourceNotFoundException("User preference not found for user with id: " + userId);
        }
        _userPreferenceMapper.updateUserPreferenceFromDto(preferenceUpdateDto, userPreference);

        _iUserRepo.save(user); // Save the updated user to persist changes

        return _userPreferenceMapper.toUserPreferenceReadDto(userPreference);
    }
}
