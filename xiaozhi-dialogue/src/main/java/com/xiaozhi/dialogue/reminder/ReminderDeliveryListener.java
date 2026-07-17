package com.xiaozhi.dialogue.reminder;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.message.MessageSender;
import com.xiaozhi.event.ChatSessionOpenedEvent;
import com.xiaozhi.personal.event.ReminderDueEvent;
import com.xiaozhi.personal.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderDeliveryListener {

    private final SessionManager sessionManager;
    private final MessageSender messageSender;
    private final ReminderService reminderService;

    @EventListener
    public void onReminderDue(ReminderDueEvent event) {
        ChatSession session = sessionManager.getSessionByDeviceId(event.deviceId());
        if (session == null || !session.isOpen()) {
            if ("ONLINE_ONLY".equals(event.deliveryPolicy())) {
                reminderService.markDelivery(event.deliveryId(), "MISSED", "设备不在线");
            }
            return;
        }
        if (session.getDevice() == null || !event.userId().equals(session.getDevice().getUserId())) {
            reminderService.markDelivery(event.deliveryId(), "FAILED", "设备归属校验失败");
            return;
        }
        try {
            messageSender.sendAlert(session, event.title(), event.content(), "neutral");
            reminderService.markDelivery(event.deliveryId(), "SENT", null);
        } catch (RuntimeException e) {
            log.error("[主动提醒] 发送失败，deliveryId={}, deviceId={}", event.deliveryId(), event.deviceId(), e);
            reminderService.markDelivery(event.deliveryId(), "FAILED", e.getMessage());
        }
    }

    @EventListener
    public void onSessionOpened(ChatSessionOpenedEvent event) {
        reminderService.dispatchPendingForDevice(event.getDeviceId());
    }
}
