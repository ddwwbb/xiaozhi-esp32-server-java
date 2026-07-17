package com.xiaozhi.firmware;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import com.xiaozhi.common.annotation.AuditLog;
import com.xiaozhi.common.model.req.FirmwareReleasePageReq;
import com.xiaozhi.common.model.req.FirmwareReleaseStatusReq;
import com.xiaozhi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@Tag(name = "固件发布管理", description = "个人版本地固件发布、启停和设备下载")
public class FirmwareController {

    private final FirmwareAppService firmwareAppService;

    public FirmwareController(FirmwareAppService firmwareAppService) {
        this.firmwareAppService = firmwareAppService;
    }

    @GetMapping("/api/firmware/releases")
    @SaCheckPermission("system:firmware:api:list")
    @Operation(summary = "分页查询固件发布")
    public ApiResponse<?> list(@Valid FirmwareReleasePageReq req) {
        return ApiResponse.success(firmwareAppService.page(req));
    }

    @PostMapping(value = "/api/firmware/releases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("system:firmware:api:create")
    @AuditLog(module = "固件管理", operation = "发布固件")
    @Operation(summary = "发布本地固件")
    public ApiResponse<?> publish(
            @RequestParam("file") MultipartFile file,
            @RequestParam String boardType,
            @RequestParam String version,
            @RequestParam(defaultValue = "false") boolean forceUpdate,
            @RequestParam(defaultValue = "true") boolean enabled) {
        return ApiResponse.success(firmwareAppService.publish(file, boardType, version, forceUpdate, enabled));
    }

    @PatchMapping("/api/firmware/releases/{releaseId}/enabled")
    @SaCheckPermission("system:firmware:api:update")
    @AuditLog(module = "固件管理", operation = "更新固件状态")
    @Operation(summary = "启用或禁用固件")
    public ApiResponse<?> changeEnabled(@PathVariable Long releaseId,
                                        @Valid @RequestBody FirmwareReleaseStatusReq req) {
        return ApiResponse.success(firmwareAppService.changeEnabled(releaseId, req.enabled()));
    }

    @DeleteMapping("/api/firmware/releases/{releaseId}")
    @SaCheckPermission("system:firmware:api:delete")
    @AuditLog(module = "固件管理", operation = "删除固件")
    @Operation(summary = "删除已禁用固件")
    public ApiResponse<?> delete(@PathVariable Long releaseId) {
        firmwareAppService.delete(releaseId);
        return ApiResponse.success("删除成功");
    }

    @SaIgnore
    @GetMapping("/api/device/firmware/{releaseId}/download")
    @Operation(summary = "设备下载固件")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long releaseId) {
        FirmwareAppService.FirmwareDownload download = firmwareAppService.requireDownload(releaseId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(download.fileSize());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().getHeaderValue());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(download.path()));
    }
}
