package com.dev.mhub.services;

import java.util.List;
import org.springframework.stereotype.Service;
import com.dev.mhub.dto.user.UserRequest;
import com.dev.mhub.dto.user.UserResponse;
import com.dev.mhub.entity.User;
import com.dev.mhub.exception.user.EmailAlreadyExistsException;
import com.dev.mhub.exception.user.UserNotFoundException;
import com.dev.mhub.repository.UserRepository;
    
@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse createUser(UserRequest user) {
        if(userRepository.existsByEmail(user.email())){
            throw new EmailAlreadyExistsException(user.email());
        }
        User newUser = new User(user.name(), user.email(), user.password());
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
                user.isStatus(), 
                user.isAdmin(),
                user.getCreatedAt()
        );
    }

   


}
