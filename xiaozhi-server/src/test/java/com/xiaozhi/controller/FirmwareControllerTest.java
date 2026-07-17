package com.xiaozhi.controller;

import com.xiaozhi.firmware.FirmwareAppService;
import com.xiaozhi.firmware.FirmwareController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FirmwareControllerTest extends ControllerTestSupport {

    @Mock
    private FirmwareAppService firmwareAppService;

    @TempDir
    Path tempDir;

    @Test
    void downloadReturnsExactLengthAndBinaryBody() throws Exception {
        byte[] firmware = new byte[]{1, 2, 3, 4, 5};
        Path path = tempDir.resolve("firmware.bin");
        Files.write(path, firmware);
        when(firmwareAppService.requireDownload(9L)).thenReturn(
                new FirmwareAppService.FirmwareDownload(path, firmware.length,
                        "atk-dnesp32s3-box2-wifi-1.6.1.bin"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FirmwareController(firmwareAppService))
                .setMessageConverters(new ResourceHttpMessageConverter())
                .build();

        mockMvc.perform(get("/api/device/firmware/9/download"))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", firmware.length))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", containsString("1.6.1.bin")))
                .andExpect(content().bytes(firmware));
    }
}
