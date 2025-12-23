package user.microservice.pets.application.services;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LogoutService {
    private final Set<String> invalidToken= ConcurrentHashMap.newKeySet();

    public void logout(String token){
        invalidToken.add(token);
    }

    public boolean isTokenInvalid(String token){
        return invalidToken.contains(token);
    }
}
