package com.bubble.bubbleai.service.impl;

import com.bubble.bubbleai.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplSessionTest {

    @Test
    void optionalLoginLookupNeverCreatesSession() {
        UserServiceImpl service = new UserServiceImpl();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        assertNull(service.getLoginUserPermitNull(request));

        verify(request).getSession(false);
        verify(request, never()).getSession(true);
        verify(request, never()).getSession();
    }

    @Test
    void requiredLoginLookupNeverCreatesSessionForAnonymousRequest() {
        UserServiceImpl service = new UserServiceImpl();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getLoginUser(request));

        verify(request).getSession(false);
        verify(request, never()).getSession(true);
        verify(request, never()).getSession();
    }
}
