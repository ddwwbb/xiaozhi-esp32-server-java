package com.xiaozhi.personal.event;

public record ReminderDueEvent(Long deliveryId, Long reminderId, Integer userId, String deviceId,
                               String title, String content, String deliveryPolicy) {
}
