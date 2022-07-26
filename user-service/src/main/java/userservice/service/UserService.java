package userservice.service;

import java.util.List;
import userservice.dto.UserDTO;

public interface UserService {

  List<UserDTO> getAllUsers();
}
