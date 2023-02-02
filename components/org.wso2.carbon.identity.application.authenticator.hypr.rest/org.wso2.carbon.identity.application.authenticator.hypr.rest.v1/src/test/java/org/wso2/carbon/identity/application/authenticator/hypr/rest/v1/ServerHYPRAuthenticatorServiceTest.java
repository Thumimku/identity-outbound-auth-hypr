/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authenticator.hypr.rest.v1;

import org.apache.commons.lang.StringUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.authenticator.hypr.common.constants.HyprAuthenticatorConstants;
import org.wso2.carbon.identity.application.authenticator.hypr.common.exception.HYPRAuthnFailedException;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.State;
import org.wso2.carbon.identity.application.authenticator.hypr.common.model.StateResponse;
import org.wso2.carbon.identity.application.authenticator.hypr.common.web.HYPRAuthorizationAPIClient;
import org.wso2.carbon.identity.application.authenticator.hypr.rest.common.error.APIError;
import org.wso2.carbon.identity.application.authenticator.hypr.rest.v1.core.ServerHYPRAuthenticatorService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ServerHYPRAuthenticatorServiceTest {

    private static final String apiToken = "testApiToken";
    private static final String baseUrl = "https://wso2.hypr.com";
    private static final String sessionDataKey = "testSessionKey";
    private static final String requestId = "testRequestId";
    private static final String username = "testUser";
    private static Map<String, String> hyprConfigurations;

    private ServerHYPRAuthenticatorService serverHYPRAuthenticatorService;

    private MockedStatic<HYPRAuthorizationAPIClient> mockedHyprAuthorizationAPIClient;
    private MockedStatic<FrameworkUtils> mockedFrameworkUtils;
    @Mock
    private AuthenticationContext context;

    @BeforeClass
    public void setUp() {
        serverHYPRAuthenticatorService = new ServerHYPRAuthenticatorService();
        mockedHyprAuthorizationAPIClient = mockStatic(HYPRAuthorizationAPIClient.class);
        mockedFrameworkUtils = mockStatic(FrameworkUtils.class);

        hyprConfigurations = new HashMap<>();
        hyprConfigurations.put(HyprAuthenticatorConstants.HYPR.BASE_URL, baseUrl);
        hyprConfigurations.put(HyprAuthenticatorConstants.HYPR.HYPR_API_TOKEN, apiToken);
    }

    @BeforeMethod
    public void methodSetUp() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterClass
    public void close() {
        mockedHyprAuthorizationAPIClient.close();
        mockedFrameworkUtils.close();
    }

    @Test(description = "Test case for handling invalid sessionKey which doesn't have an authentication context.")
    public void testHandleInvalidSessionKey() {

        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(null);

        try {
            serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);
        } catch (APIError e) {
            assertEquals(e.getCode(),
                    HyprAuthenticatorConstants.ErrorMessages.CLIENT_ERROR_INVALID_SESSION_KEY.getCode());
        }

    }

    @DataProvider(name = "hyprConfigurationProviders")
    public Object[][] getHyprConfigurationProviders() {

        return new String[][]{
                {baseUrl, null},
                {null, apiToken},
                {null, null}
        };
    }

    @Test(dataProvider = "hyprConfigurationProviders", description = "Test case for handling missing HYPR " +
            "configurations in the extracted authentication context for the provided session key.")
    public void testHandleInvalidHyprConfigurations(String baseUrl, String apiToken) {

        if (StringUtils.isNotBlank(baseUrl)) {
            when(context.getProperty(HyprAuthenticatorConstants.HYPR.BASE_URL)).thenReturn(baseUrl);
        }
        if (StringUtils.isNotBlank(apiToken)) {
            when(context.getProperty(HyprAuthenticatorConstants.HYPR.HYPR_API_TOKEN)).thenReturn(apiToken);
        }

        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        try {
            serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);
        } catch (APIError e) {
            assertEquals(e.getCode(), HyprAuthenticatorConstants
                    .ErrorMessages.SERVER_ERROR_INVALID_AUTHENTICATOR_CONFIGURATIONS.getCode());
        }
    }

    @DataProvider(name = "hyprAuthenticationPropertiesProviders")
    public Object[][] getHyprAuthenticationPropertiesProviders() {

        return new String[][]{
                {"COMPLETED", null},
                {"FAILED", null},
                {"CANCELED", null},
                {null, requestId},
                {null, null}
        };
    }

    private void mockAuthenticationContext(AuthenticationContext mockAuthenticationContext) {

        when(mockAuthenticationContext.getAuthenticatorProperties()).thenReturn(hyprConfigurations);
    }

    @Test(dataProvider = "hyprAuthenticationPropertiesProviders", description = "Test case for handling missing HYPR " +
            "authentication properties (set via the HYPR authenticator when initiating the authentication flow) in " +
            "the extracted authentication context for the provided session key.")
    public void testHandleInvalidHyprAuthenticationProperties(String authStatus, String requestId) {

        mockAuthenticationContext(context);

        if (StringUtils.isNotBlank(authStatus)) {
            when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS)).thenReturn(authStatus);
        }
        if (StringUtils.isNotBlank(requestId)) {
            when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID)).thenReturn(requestId);
        }

        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        try {
            serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);
        } catch (APIError e) {
            Assert.assertEquals(e.getCode(), HyprAuthenticatorConstants
                    .ErrorMessages.SERVER_ERROR_INVALID_AUTHENTICATION_PROPERTIES.getCode());
        }
    }

    @DataProvider(name = "hyprTerminatingAuthStatusProviders")
    public Object[][] getHyprTerminatingAuthStatusProviders() {

        return new String[][]{
                {"COMPLETED"},
                {"FAILED"},
                {"CANCELED"},
        };
    }

    @Test(dataProvider = "hyprTerminatingAuthStatusProviders", description = "Test case for handling authentication status " +
            "property extracted from the authentication context already being assigned with a terminating status " +
            "(i.e. 'COMPLETED', 'FAILED', 'CANCELED'), avoid making API call to the HYPR server.")
    public void testHandleExistingAuthenticationStatusWithTerminatingStatus(String authStatus) {

        mockAuthenticationContext(context);
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS)).thenReturn(authStatus);
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID)).thenReturn(requestId);
        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        StatusResponse statusResponse = new StatusResponse();
        statusResponse.setStatus(StatusResponse.StatusEnum.fromValue(authStatus));
        statusResponse.setSessionKey(sessionDataKey);

        Assert.assertEquals(serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey), statusResponse);
    }

    @DataProvider(name = "hyprAuthStatusProviders")
    public Object[][] getHyprAuthStatusProviders() {

        return new String[][]{
                {"INITIATED"},
                {"INITIATED_RESPONSE"},
                {"COMPLETED"},
                {"FAILED"},
                {"CANCELED"},
        };
    }

    @Test(dataProvider = "hyprAuthStatusProviders", description = "Test case for handling successful authentication " +
            "status retrieving from HYPR server upon providing a valid session key.")
    public void testHandleSuccessfulAuthenticationStatusRetrieving(String retrievedAuthStatus) {

        mockAuthenticationContext(context);
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS)).thenReturn("PENDING");
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID)).thenReturn(requestId);
        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        List<State> states = new ArrayList<>();
        states.add(new State("REQUEST_SENT", ""));
        states.add(new State(retrievedAuthStatus, ""));

        StateResponse stateResponse = new StateResponse(requestId, username, states);

        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getAuthenticationStatus(baseUrl, apiToken, requestId))
                .thenReturn(stateResponse);

        StatusResponse statusResponse = serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);

        StatusResponse.StatusEnum currentAuthenticationState = StatusResponse.StatusEnum.REQUEST_SENT;

        switch (statusResponse.getStatus().value()) {
            case "INITIATED":
                currentAuthenticationState = StatusResponse.StatusEnum.INITIATED;
                break;
            case "INITIATED_RESPONSE":
                currentAuthenticationState = StatusResponse.StatusEnum.INITIATED_RESPONSE;
                break;
            case "COMPLETED":
                currentAuthenticationState = StatusResponse.StatusEnum.COMPLETED;
                break;
            case "FAILED":
                currentAuthenticationState = StatusResponse.StatusEnum.FAILED;
                break;
            case "CANCELED":
                currentAuthenticationState = StatusResponse.StatusEnum.CANCELED;
                break;
        }
        Assert.assertEquals(statusResponse.getSessionKey(), sessionDataKey);
        Assert.assertEquals(statusResponse.getStatus(), currentAuthenticationState);
    }

    @Test(description = "Test case for handling invalid or expired HYPR API token extracted from the " +
            "HYPR configurations.")
    public void testHandleInvalidAPIToken () {

        mockAuthenticationContext(context);
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS)).thenReturn("PENDING");
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID)).thenReturn(requestId);
        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        HyprAuthenticatorConstants.ErrorMessages errorMessage =
                HyprAuthenticatorConstants.ErrorMessages.HYPR_ENDPOINT_API_TOKEN_INVALID_FAILURE;


        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getAuthenticationStatus(baseUrl, apiToken, requestId))
                .thenThrow(new HYPRAuthnFailedException(errorMessage.getCode(), errorMessage.getMessage()));

        try {
            serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);
        } catch (APIError e) {
            Assert.assertEquals(e.getCode(), HyprAuthenticatorConstants
                    .ErrorMessages.HYPR_ENDPOINT_API_TOKEN_INVALID_FAILURE.getCode());
        }
    }

    @Test(description = "Test case for handling invalid authentication properties (i.e : requestId).")
    public void testHandleInvalidAuthenticationProperties () {

        mockAuthenticationContext(context);
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_STATUS)).thenReturn("PENDING");
        when(context.getProperty(HyprAuthenticatorConstants.HYPR.AUTH_REQUEST_ID)).thenReturn(requestId);
        when(FrameworkUtils.getAuthenticationContextFromCache(sessionDataKey)).thenReturn(context);

        HyprAuthenticatorConstants.ErrorMessages errorMessage =
                HyprAuthenticatorConstants.ErrorMessages.SERVER_ERROR_INVALID_AUTHENTICATION_PROPERTIES;


        mockedHyprAuthorizationAPIClient
                .when(() -> HYPRAuthorizationAPIClient.getAuthenticationStatus(baseUrl, apiToken, requestId))
                .thenThrow(new HYPRAuthnFailedException(errorMessage.getCode(), errorMessage.getMessage()));

        try {
            serverHYPRAuthenticatorService.getAuthenticationStatus(sessionDataKey);
        } catch (APIError e) {
            Assert.assertEquals(e.getCode(), HyprAuthenticatorConstants
                    .ErrorMessages.SERVER_ERROR_INVALID_AUTHENTICATION_PROPERTIES.getCode());
        }
    }
}
