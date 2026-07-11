package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.dto.OrderStatusUpdatedEvent;
import com.ecommerce.notificationservice.exception.NotificationException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    private SmsService smsService;

    @BeforeEach
    void setUp() {
        smsService = new SmsService("+15005550001");
    }

    private OrderStatusUpdatedEvent sampleEvent() {
        return new OrderStatusUpdatedEvent(
                "order-uuid-1234-5678", "user-001", "PAID", "SHIPPED");
    }

    @Test
    void sendOrderStatusSms_success() {
        MessageCreator mockCreator = mock(MessageCreator.class);
        Message mockMessage = mock(Message.class);
        when(mockMessage.getSid()).thenReturn("SM123");
        when(mockCreator.create()).thenReturn(mockMessage);

        try (MockedStatic<Message> messageMock = mockStatic(Message.class)) {
            messageMock.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            assertThatNoException().isThrownBy(() -> smsService.sendOrderStatusSms(sampleEvent()));
            verify(mockCreator).create();
        }
    }

    @Test
    void sendOrderStatusSms_apiException_throwsNotificationException() {
        MessageCreator mockCreator = mock(MessageCreator.class);
        when(mockCreator.create()).thenThrow(new ApiException("Invalid credentials"));

        try (MockedStatic<Message> messageMock = mockStatic(Message.class)) {
            messageMock.when(() -> Message.creator(
                    any(PhoneNumber.class),
                    any(PhoneNumber.class),
                    anyString()
            )).thenReturn(mockCreator);

            assertThatThrownBy(() -> smsService.sendOrderStatusSms(sampleEvent()))
                    .isInstanceOf(NotificationException.class)
                    .hasMessageContaining("SMS delivery failed");
        }
    }
}
