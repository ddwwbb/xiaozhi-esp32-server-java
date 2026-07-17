package com.xiaozhi.personal.service;

import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.common.model.bo.DeviceBO;
import com.xiaozhi.device.service.DeviceService;
import com.xiaozhi.personal.event.ReminderDueEvent;
import com.xiaozhi.personal.model.PersonalModels.Reminder;
import com.xiaozhi.personal.model.PersonalModels.ReminderDelivery;
import com.xiaozhi.personal.model.PersonalModels.PendingReminderDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final int DUE_BATCH_SIZE = 50;

    private final PersonalFeatureMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final DeviceService deviceService;

    @Value("${personal.reminder.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    public Reminder create(Integer userId, String deviceId, String title, String content, String timezone,
                           LocalDateTime firstTriggerLocal, String recurrenceType, String weekdays,
                           String deliveryPolicy) {
        if (userId == null || !StringUtils.hasText(deviceId) || !StringUtils.hasText(title)
                || firstTriggerLocal == null) {
            throw new IllegalArgumentException("用户、设备、标题和首次提醒时间不能为空");
        }
        DeviceBO device = deviceService.getBO(deviceId.trim());
        if (device == null || !userId.equals(device.getUserId())) {
            throw new IllegalArgumentException("设备不存在或不属于当前用户");
        }
        ZoneId zone = parseZone(timezone);
        String recurrence = normalizeRecurrence(recurrenceType);
        if ("WEEKLY".equals(recurrence)) {
            parseWeekdays(weekdays);
        }
        Instant firstInstant = firstTriggerLocal.atZone(zone).toInstant();
        if (!firstInstant.isAfter(clock.instant())) {
            throw new IllegalArgumentException("首次提醒时间必须晚于当前时间");
        }
        Reminder reminder = new Reminder();
        reminder.setUserId(userId);
        reminder.setDeviceId(deviceId.trim());
        reminder.setTitle(title.trim());
        reminder.setContent(StringUtils.hasText(content) ? content.trim() : title.trim());
        reminder.setTimezone(zone.getId());
        reminder.setLocalTime(firstTriggerLocal.toLocalTime().withNano(0));
        reminder.setRecurrenceType(recurrence);
        reminder.setWeekdays(normalizeWeekdays(weekdays));
        reminder.setNextTriggerAt(toUtc(firstInstant));
        reminder.setDeliveryPolicy(normalizeDeliveryPolicy(deliveryPolicy));
        mapper.insertReminder(reminder);
        return reminder;
    }

    public List<Reminder> list(Integer userId) {
        return mapper.listReminders(userId);
    }

    public int delete(Integer userId, Long reminderId) {
        return mapper.deleteReminder(reminderId, userId);
    }

    @Scheduled(fixedDelayString = "${personal.reminder.poll-interval-ms:1000}")
    public void dispatchDueReminders() {
        if (!schedulerEnabled) return;
        LocalDateTime nowUtc = toUtc(clock.instant());
        for (Reminder reminder : mapper.findDueReminders(nowUtc, DUE_BATCH_SIZE)) {
            ReminderDelivery delivery = new ReminderDelivery();
            delivery.setReminderId(reminder.getReminderId());
            delivery.setScheduledAt(reminder.getNextTriggerAt());
            int inserted = mapper.insertReminderDelivery(delivery);

            Instant next = nextOccurrence(reminder);
            String nextStatus = next == null ? "COMPLETED" : "ACTIVE";
            LocalDateTime nextUtc = next == null ? reminder.getNextTriggerAt() : toUtc(next);
            if (mapper.advanceReminder(reminder.getReminderId(), reminder.getVersion(), nextUtc, nextStatus) == 0) {
                continue;
            }
            if (inserted > 0) {
                eventPublisher.publishEvent(new ReminderDueEvent(delivery.getDeliveryId(), reminder.getReminderId(),
                        reminder.getUserId(), reminder.getDeviceId(), reminder.getTitle(), reminder.getContent(),
                        reminder.getDeliveryPolicy()));
            }
        }
    }

    public void markDelivery(Long deliveryId, String status, String errorMessage) {
        ReminderDelivery delivery = new ReminderDelivery();
        delivery.setDeliveryId(deliveryId);
        delivery.setStatus(status);
        delivery.setSentAt("SENT".equals(status) ? toUtc(clock.instant()) : null);
        delivery.setErrorMessage(errorMessage);
        mapper.updateReminderDelivery(delivery);
    }

    public void dispatchPendingForDevice(String deviceId) {
        for (PendingReminderDelivery pending : mapper.findPendingDeliveries(deviceId, 20)) {
            eventPublisher.publishEvent(new ReminderDueEvent(pending.getDeliveryId(), pending.getReminderId(),
                    pending.getUserId(), pending.getDeviceId(), pending.getTitle(), pending.getContent(),
                    pending.getDeliveryPolicy()));
        }
    }

    private Instant nextOccurrence(Reminder reminder) {
        ZoneId zone = parseZone(reminder.getTimezone());
        ZonedDateTime current = reminder.getNextTriggerAt().toInstant(ZoneOffset.UTC).atZone(zone);
        return switch (reminder.getRecurrenceType()) {
            case "ONCE" -> null;
            case "DAILY" -> current.plusDays(1).with(reminder.getLocalTime()).toInstant();
            case "WEEKLY" -> nextWeekly(current, reminder.getLocalTime(), parseWeekdays(reminder.getWeekdays())).toInstant();
            default -> throw new IllegalStateException("不支持的重复类型: " + reminder.getRecurrenceType());
        };
    }

    private ZonedDateTime nextWeekly(ZonedDateTime current, LocalTime time, Set<DayOfWeek> weekdays) {
        for (int offset = 1; offset <= 7; offset++) {
            ZonedDateTime candidate = current.plusDays(offset).with(time);
            if (weekdays.contains(candidate.getDayOfWeek())) {
                return candidate;
            }
        }
        throw new IllegalStateException("每周提醒没有有效星期");
    }

    private ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(StringUtils.hasText(timezone) ? timezone : "Asia/Shanghai");
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("无效 IANA 时区: " + timezone, e);
        }
    }

    private String normalizeRecurrence(String recurrenceType) {
        String value = StringUtils.hasText(recurrenceType) ? recurrenceType.toUpperCase() : "ONCE";
        if (!Set.of("ONCE", "DAILY", "WEEKLY").contains(value)) {
            throw new IllegalArgumentException("重复类型仅支持 ONCE、DAILY、WEEKLY");
        }
        return value;
    }

    private String normalizeDeliveryPolicy(String deliveryPolicy) {
        String value = StringUtils.hasText(deliveryPolicy) ? deliveryPolicy.toUpperCase() : "NEXT_CONNECT";
        if (!Set.of("NEXT_CONNECT", "ONLINE_ONLY").contains(value)) {
            throw new IllegalArgumentException("投递策略仅支持 NEXT_CONNECT、ONLINE_ONLY");
        }
        return value;
    }

    private Set<DayOfWeek> parseWeekdays(String weekdays) {
        if (!StringUtils.hasText(weekdays)) {
            throw new IllegalArgumentException("WEEKLY 提醒必须指定 weekdays，格式 1,2,...,7");
        }
        try {
            return Arrays.stream(weekdays.split(","))
                    .map(String::trim).map(Integer::parseInt).map(DayOfWeek::of).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("weekdays 必须为 1-7 的逗号分隔列表", e);
        }
    }

    private String normalizeWeekdays(String weekdays) {
        if (!StringUtils.hasText(weekdays)) {
            return null;
        }
        return parseWeekdays(weekdays).stream().sorted().map(d -> String.valueOf(d.getValue()))
                .collect(Collectors.joining(","));
    }

    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
