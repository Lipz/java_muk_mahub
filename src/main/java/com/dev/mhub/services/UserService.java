package com.dev.mhub.services;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.dev.mhub.dto.user.UserRegisterRequest;
import com.dev.mhub.dto.user.UserResponse;
import com.dev.mhub.entity.User;
import com.dev.mhub.entity.UserStatus;
import com.dev.mhub.exception.user.EmailAlreadyExistsException;
import com.dev.mhub.exception.user.UserNotFoundException;
import com.dev.mhub.repository.UserRepository;
    
@Service
public class UserService {
    private final UserRepository userRepository;
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public UserResponse createUser(UserRegisterRequest user) {
        if(userRepository.existsByEmailIgnoreCase(user.email())){
            throw new EmailAlreadyExistsException(user.email());
        }
        User newUser = new User(
                        user.name(), user.email(), 
                        encoder.encode(user.password()),
                        false, 
                        UserStatus.ACTIVE);

        User savedUser  = userRepository.save(newUser);
        return DataResponse(savedUser);
    }

    public List<UserResponse> getUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::DataResponse).toList();
    }

    public UserResponse getUser(Long id) {

        User user = userRepository.findById(id)
                                .orElseThrow(() -> new UserNotFoundException(id));

        return DataResponse(user);
    }

    private UserResponse DataResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getStatus(), 
                user.isAdmin()
        );
    }

   


}
