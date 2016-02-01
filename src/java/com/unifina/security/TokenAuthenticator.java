package com.unifina.security;

import com.unifina.domain.security.SecUser;
import com.unifina.service.PermissionService;
import com.unifina.service.UserService;

import javax.servlet.http.HttpServletRequest;

public class TokenAuthenticator {
	private final UserService userService;
	private boolean lastAuthenticationMalformed = false;

	public TokenAuthenticator(UserService userService) {
		this.userService = userService;
	}

	public SecUser authenticate(HttpServletRequest request) {
		String apiKey = parseAuthorizationHeader(request.getHeader("Authorization"));
		return userService.getUserByApiKey(apiKey);
	}

	public boolean lastAuthenticationMalformed() {
		return lastAuthenticationMalformed;
	}

	/**
	 * "Authorization: Token apiKey" => "apiKey"
	 */
	private String parseAuthorizationHeader(String s) {
		s = s == null ? null : s.trim();
		if (s != null && !s.isEmpty()) {
			String[] parts = s.split("\\s+");
			if (parts.length == 2 && parts[0].toLowerCase().equals("token")) {
				lastAuthenticationMalformed = false;
				return parts[1];
			} else {
				lastAuthenticationMalformed = true;
			}
 		}
		return null;
	}
}
