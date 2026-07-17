package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.personal.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateReminderFunction implements ToolsGlobalRegistry.GlobalFunction {

    private static final String TOOL_NAME = "create_reminder";
    private final SessionManager sessionManager;
    private final ReminderService reminderService;

    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        ChatSession session = sessionManager.getSession(toolSession.getSessionId());
        if (session == null || session.getDevice() == null) return null;
        return FunctionToolCallback.builder(TOOL_NAME, (Map<String, String> args, ToolContext context) -> {
                    LocalDateTime firstAt = LocalDateTime.parse(args.get("firstTriggerLocal"));
                    reminderService.create(session.getDevice().getUserId(), session.getDevice().getDeviceId(),
                            args.get("title"), args.get("content"), args.getOrDefault("timezone", "Asia/Shanghai"),
                            firstAt, args.getOrDefault("recurrenceType", "ONCE"), args.get("weekdays"),
                            args.getOrDefault("deliveryPolicy", "NEXT_CONNECT"));
                    return "提醒已创建，首次时间：" + firstAt + " " + args.getOrDefault("timezone", "Asia/Shanghai");
                })
                .description("创建闹钟或提醒。时间含糊时必须先询问用户，得到精确到分钟的本地日期时间后才能调用。")
                .inputSchema("""
                    {"type":"object","properties":{
                      "title":{"type":"string"},"content":{"type":"string"},
                      "firstTriggerLocal":{"type":"string","description":"ISO本地时间，如2026-07-18T08:30:00"},
                      "timezone":{"type":"string","description":"IANA时区，如Asia/Shanghai"},
                      "recurrenceType":{"type":"string","enum":["ONCE","DAILY","WEEKLY"]},
                      "weekdays":{"type":"string","description":"WEEKLY时用1-7逗号分隔，1为周一"},
                      "deliveryPolicy":{"type":"string","enum":["ONLINE_ONLY","NEXT_CONNECT"]}
                    },"required":["title","firstTriggerLocal","timezone"]}
                    """)
                .inputType(Map.class)
                .toolMetadata(new XiaozhiToolMetadata(true))
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }

    @Override public String getToolName() { return TOOL_NAME; }
    @Override public String getToolDescription() { return "创建闹钟和主动提醒"; }
}
