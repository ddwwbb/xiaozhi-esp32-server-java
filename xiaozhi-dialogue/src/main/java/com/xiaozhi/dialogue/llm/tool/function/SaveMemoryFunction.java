package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.ai.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.ai.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.ai.tool.ToolsGlobalRegistry;
import com.xiaozhi.ai.tool.session.ToolSession;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.personal.service.LongTermMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SaveMemoryFunction implements ToolsGlobalRegistry.GlobalFunction {

    private static final String TOOL_NAME = "save_memory";
    private final SessionManager sessionManager;
    private final LongTermMemoryService memoryService;

    @Override
    public ToolCallback getFunctionCallTool(ToolSession toolSession) {
        ChatSession session = sessionManager.getSession(toolSession.getSessionId());
        if (session == null || session.getDevice() == null) return null;
        return FunctionToolCallback.builder(TOOL_NAME, (Map<String, String> args, ToolContext context) -> {
                    memoryService.save(session.getDevice().getUserId(), session.getDevice().getRoleId(),
                            args.get("namespace"), args.get("key"), args.get("value"), BigDecimal.ONE, null, null);
                    return "已记住：" + args.get("key");
                })
                .description("仅当用户明确说记住、保存偏好或纠正长期资料时调用。不要从普通闲聊擅自推断长期记忆。")
                .inputSchema("""
                    {"type":"object","properties":{
                      "namespace":{"type":"string","description":"profile/preference/relationship/constraint"},
                      "key":{"type":"string","description":"稳定、简短的事实键"},
                      "value":{"type":"string","description":"用户明确要求保存的事实"}
                    },"required":["key","value"]}
                    """)
                .inputType(Map.class)
                .toolMetadata(new XiaozhiToolMetadata(true))
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }

    @Override public String getToolName() { return TOOL_NAME; }
    @Override public String getToolDescription() { return "保存用户明确要求记住的长期记忆"; }
}
