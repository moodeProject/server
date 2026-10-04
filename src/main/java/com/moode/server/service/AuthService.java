package com.moode.server.service;

import com.moode.server.dto.request.LoginRequest;
import com.moode.server.dto.request.SignupRequest;
import com.moode.server.dto.response.LoginResponse;
import com.moode.server.dto.response.MeResponse;
import com.moode.server.dto.response.SignupResponse;
import com.moode.server.security.AuthenticatedUser;

public interface AuthService {
    LoginResponse login(LoginRequest request);

    SignupResponse signup(SignupRequest request);

    MeResponse me(AuthenticatedUser user);
}
