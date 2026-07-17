package com.xiaozhi.firmware.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.firmware.dal.mysql.dataobject.FirmwareReleaseDO;
import com.xiaozhi.firmware.dal.mysql.mapper.FirmwareReleaseMapper;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import com.xiaozhi.firmware.domain.FirmwareVersion;
import com.xiaozhi.firmware.domain.repository.FirmwareReleaseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Optional;

@Repository
public class FirmwareReleaseRepositoryImpl implements FirmwareReleaseRepository {

    private static final int MAX_OTA_CANDIDATES = 200;

    private final FirmwareReleaseMapper mapper;
    private final FirmwareReleaseConverter converter;

    public FirmwareReleaseRepositoryImpl(FirmwareReleaseMapper mapper, FirmwareReleaseConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<FirmwareRelease> findById(Long releaseId) {
        return releaseId == null
                ? Optional.empty()
                : Optional.ofNullable(converter.toDomain(mapper.selectById(releaseId)));
    }

    @Override
    public Optional<FirmwareRelease> findByBoardTypeAndVersion(String boardType, String version) {
        FirmwareReleaseDO result = mapper.selectOne(new LambdaQueryWrapper<FirmwareReleaseDO>()
                .eq(FirmwareReleaseDO::getBoardType, boardType)
                .eq(FirmwareReleaseDO::getVersion, version)
                .last("LIMIT 1"));
        return Optional.ofNullable(converter.toDomain(result));
    }

    @Override
    public Optional<FirmwareRelease> findUpdate(String boardType, String currentVersion) {
        if (!StringUtils.hasText(boardType) || !FirmwareVersion.isValid(currentVersion)) {
            return Optional.empty();
        }
        FirmwareVersion current = FirmwareVersion.parse(currentVersion);
        return mapper.selectList(new LambdaQueryWrapper<FirmwareReleaseDO>()
                        .eq(FirmwareReleaseDO::getBoardType, boardType)
                        .eq(FirmwareReleaseDO::getEnabled, true)
                        .orderByDesc(FirmwareReleaseDO::getCreateTime)
                        .last("LIMIT " + MAX_OTA_CANDIDATES)).stream()
                .map(converter::toDomain)
                .filter(release -> FirmwareVersion.parse(release.getVersion()).compareTo(current) > 0)
                .max(Comparator.comparing(release -> FirmwareVersion.parse(release.getVersion())));
    }

    @Override
    public PageResp<FirmwareRelease> page(int pageNo, int pageSize, String boardType, Boolean enabled) {
        int boundedPageSize = Math.min(pageSize, 100);
        LambdaQueryWrapper<FirmwareReleaseDO> query = new LambdaQueryWrapper<FirmwareReleaseDO>()
                .like(StringUtils.hasText(boardType), FirmwareReleaseDO::getBoardType, boardType)
                .eq(enabled != null, FirmwareReleaseDO::getEnabled, enabled)
                .orderByDesc(FirmwareReleaseDO::getCreateTime);
        IPage<FirmwareReleaseDO> result = mapper.selectPage(new Page<>(pageNo, boundedPageSize), query);
        return new PageResp<>(result.getRecords().stream().map(converter::toDomain).toList(),
                result.getTotal(), Math.toIntExact(result.getCurrent()), Math.toIntExact(result.getSize()));
    }

    @Override
    public void save(FirmwareRelease release) {
        FirmwareReleaseDO dataObject = converter.toDataObject(release);
        if (release.getReleaseId() == null) {
            mapper.insert(dataObject);
            release.assignId(dataObject.getReleaseId());
        } else {
            mapper.updateById(dataObject);
        }
    }

    @Override
    public void delete(Long releaseId) {
        mapper.deleteById(releaseId);
    }
}
