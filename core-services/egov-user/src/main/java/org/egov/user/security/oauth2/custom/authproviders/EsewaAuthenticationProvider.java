package org.egov.user.security.oauth2.custom.authproviders;

import static java.util.Objects.isNull;
import static org.egov.user.config.UserServiceConstants.IP_HEADER_NAME;
import static org.springframework.util.StringUtils.isEmpty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.ServiceCallException;
import org.egov.user.repository.builder.RestCallRepository;
import org.egov.user.config.UserServiceConstants;
import org.egov.user.domain.exception.DuplicateUserNameException;
import org.egov.user.domain.exception.UserNotFoundException;
import org.egov.user.domain.model.SecureUser;
import org.egov.user.domain.model.User;
import org.egov.user.domain.model.enums.UserType;
import org.egov.user.domain.service.UserService;
import org.egov.user.domain.service.utils.EncryptionDecryptionUtil;
import org.egov.user.web.contract.auth.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component("esewaAuthenticationProvider")
@Slf4j
public class EsewaAuthenticationProvider implements CustomThirdPartyAuthenticationProvider {

	private UserService userService;

	@Autowired
	private EncryptionDecryptionUtil encryptionDecryptionUtil;
	
	@Autowired
	private RestCallRepository restCallRepository;

	@Value("${citizen.login.password.otp.enabled}")
	private boolean citizenLoginPasswordOtpEnabled;
	
	@Value("${egov.hrms.sso.mseva.decryption.key}")
	private String ssoDecryptionKey;
	
	@Value("${egov.hrms.eseva.api.host}")
	private String hrmsEsewaApiHost;
	
	@Value("${egov.hrms.eseva.api.endpoint}")
	private String hrmsEsewaApiEndpoint;
	
	@Value("${egov.hrms.eseva.api.request.object}")
	private String hrmsEsewaApiReqObj;
	
	@Value("${egov.hrms.eseva.api.response.object.response}")
	private String hrmsEsewaApiObjResp;

	@Value("${employee.login.password.otp.enabled}")
	private boolean employeeLoginPasswordOtpEnabled;

	@Value("${citizen.login.password.otp.fixed.value}")
	private String fixedOTPPassword;

	@Value("${citizen.login.password.otp.fixed.enabled}")
	private boolean fixedOTPEnabled;

	@Autowired
	private HttpServletRequest request;
	
	@Autowired
	private UserServiceConstants userServiceConstants;

	public EsewaAuthenticationProvider(UserService userService) {
		this.userService = userService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		String userName = authentication.getName();
		String password = authentication.getCredentials().toString();

		final LinkedHashMap<String, String> details = (LinkedHashMap<String, String>) authentication.getDetails();

		String tenantId = details.get("tenantId");
		String userType = details.get("userType");

		if (isEmpty(tenantId)) {
			throw new OAuth2Exception("TenantId is mandatory");
		}
		if (isEmpty(userType) || isNull(UserType.fromValue(userType))) {
			throw new OAuth2Exception("User Type is mandatory and has to be a valid type");
		}

		User user;
		RequestInfo requestInfo;
		try {
			user = userService.getUniqueUser(userName, tenantId, UserType.fromValue(userType));
			/* decrypt here otp service and final response need decrypted data */
			Set<org.egov.user.domain.model.Role> domain_roles = user.getRoles();
			List<org.egov.common.contract.request.Role> contract_roles = new ArrayList<>();
			for (org.egov.user.domain.model.Role role : domain_roles) {
				contract_roles.add(org.egov.common.contract.request.Role.builder().code(role.getCode())
						.name(role.getName()).build());
			}

			org.egov.common.contract.request.User userInfo = org.egov.common.contract.request.User.builder()
					.uuid(user.getUuid()).type(user.getType() != null ? user.getType().name() : null)
					.roles(contract_roles).build();
			requestInfo = RequestInfo.builder().userInfo(userInfo).build();
			user = encryptionDecryptionUtil.decryptObject(user, "UserListSelf", User.class, requestInfo);

		} catch (UserNotFoundException e) {
			log.error("User not found", e);
			throw new OAuth2Exception("Invalid login credentials");
		} catch (DuplicateUserNameException e) {
			log.error("Fatal error, user conflict, more than one user found", e);
			throw new OAuth2Exception("Invalid login credentials");

		}

		if (user.getActive() == null || !user.getActive()) {
			throw new OAuth2Exception("Please activate your account");
		}

		// If account is locked, perform lazy unlock if eligible

		if (user.getAccountLocked() != null && user.getAccountLocked()) {

			if (userService.isAccountUnlockAble(user)) {
				user = unlockAccount(user, requestInfo);
			} else
				throw new OAuth2Exception("Account locked");
		}

		boolean isCitizen = false;
		if (user.getType() != null && user.getType().equals(UserType.CITIZEN))
			isCitizen = true;

		boolean isPasswordMatched;
		if (isCitizen) {
			if (fixedOTPEnabled && !fixedOTPPassword.equals("") && fixedOTPPassword.equals(password)) {
				// for automation allow fixing otp validation to a fixed otp
				isPasswordMatched = true;
			} else {
				isPasswordMatched = isPasswordMatch(citizenLoginPasswordOtpEnabled, password, user, authentication);
			}
		} else {
			isPasswordMatched = isPasswordMatch(employeeLoginPasswordOtpEnabled, password, user, authentication);
		}

		if (isPasswordMatched) {

			/*
			 * We assume that there will be only one type. If it is multiple then we have
			 * change below code Separate by comma or other and iterate
			 */
			List<GrantedAuthority> grantedAuths = new ArrayList<>();
			grantedAuths.add(new SimpleGrantedAuthority("ROLE_" + user.getType()));
			final SecureUser secureUser = new SecureUser(getUser(user));
			userService.resetFailedLoginAttempts(user);
			return new UsernamePasswordAuthenticationToken(secureUser, password, grantedAuths);
		} else {
			// Handle failed login attempt
			// Fetch Real IP after being forwarded by reverse proxy
			userService.handleFailedLogin(user, request.getHeader(IP_HEADER_NAME), requestInfo);

			throw new OAuth2Exception("Invalid login credentials");
		}

	}

	private boolean isPasswordMatch(Boolean isOtpBased, String password, User user, Authentication authentication) {
		byte[] staticKey = Base64.getDecoder().decode(ssoDecryptionKey);
		
		final byte[] StaticIv = new byte[16];
//		String inputData = authenticateUserInputRequest.getTokenName()+":" + user.getUserName();
		
		String inputData = password + ":" + user.getUserName(); //passw instead of token 
		String encdata = encryptData(inputData,staticKey,StaticIv);
		
		boolean blval = ReadValuesFromApi(encdata);
		return blval;
	}
	
	private boolean ReadValuesFromApi(String encdata) {
		boolean bl = false;
		int respValue = 1;
		StringBuilder uri = new StringBuilder();
	    uri.append(hrmsEsewaApiHost).append(hrmsEsewaApiEndpoint); // Construct URL for HRMS Eseva call

	    Map<String, Object> apiRequest = new HashMap<>();
	    apiRequest.put(hrmsEsewaApiReqObj, encdata); //hrmsEsevaApiRequestObject
	    
	    try {
	    	Map<String, Object> responseMap = (Map<String, Object>) restCallRepository.fetchResult(uri, apiRequest);
	        
	        if(responseMap != null) {
	        	Object responseObj = responseMap.get(hrmsEsewaApiObjResp); //hrmsEsevaApiResponseObjectResp
	        	respValue = Integer.parseInt(responseObj.toString());
	        	if (respValue == 1) {
	        		bl = true;
                }
	        }
	    }
	    catch(Exception e) {
	    	log.error("An error occurred: ", e);
	    	bl = false;
	    	return bl;
	    }

		return bl;
	}

	public static String encryptData(String plainText, byte[] key, byte[] iv) {
        try {
            byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainBytes);

            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
        	log.error("An error occurred: ", e);
            return null; 
        }
    }

	@SuppressWarnings("unchecked")
	private String getTenantId(Authentication authentication) {
		final LinkedHashMap<String, String> details = (LinkedHashMap<String, String>) authentication.getDetails();

		log.debug("details------->" + details);
		log.debug("tenantId in CustomAuthenticationProvider------->" + details.get("tenantId"));

		final String tenantId = details.get("tenantId");
		if (isEmpty(tenantId)) {
			throw new OAuth2Exception("TenantId is mandatory");
		}
		return tenantId;
	}

	private org.egov.user.web.contract.auth.User getUser(User user) {
		org.egov.user.web.contract.auth.User authUser = org.egov.user.web.contract.auth.User.builder().id(user.getId())
				.userName(user.getUserName()).uuid(user.getUuid()).name(user.getName())
				.mobileNumber(user.getMobileNumber()).emailId(user.getEmailId()).locale(user.getLocale())
				.active(user.getActive()).type(user.getType().name()).roles(toAuthRole(user.getRoles()))
				.tenantId(user.getTenantId()).build();

		if (user.getPermanentAddress() != null)
			authUser.setPermanentCity(user.getPermanentAddress().getCity());

		return authUser;
	}

	private Set<Role> toAuthRole(Set<org.egov.user.domain.model.Role> domainRoles) {
		if (domainRoles == null)
			return new HashSet<>();
		return domainRoles.stream().map(org.egov.user.web.contract.auth.Role::new).collect(Collectors.toSet());
	}

	@Override
	public boolean supports(final Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);

	}

	/**
	 * Unlock account and disable existing failed login attempts for the user
	 *
	 * @param user to be unlocked
	 * @return Updated user
	 */
	private User unlockAccount(User user, RequestInfo requestInfo) {
		User userToBeUpdated = user.toBuilder().accountLocked(false).password(null).build();

		User updatedUser = userService.updateWithoutOtpValidation(userToBeUpdated, requestInfo);
		userService.resetFailedLoginAttempts(userToBeUpdated);

		return updatedUser;
	}

}
