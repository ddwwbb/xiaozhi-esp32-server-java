import { http } from './request'
import api from './api'
import type { FirmwarePublishForm, FirmwareRelease, FirmwareReleaseQuery } from '@/types/firmware'

export function queryFirmwareReleases(params: FirmwareReleaseQuery) {
  return http.getPage<FirmwareRelease>(api.firmware.releases, params)
}

export function publishFirmware(form: FirmwarePublishForm) {
  const data = new FormData()
  data.append('file', form.file)
  data.append('boardType', form.boardType)
  data.append('version', form.version)
  data.append('forceUpdate', String(form.forceUpdate))
  data.append('enabled', String(form.enabled))
  return http.postMultipart<FirmwareRelease>(api.firmware.releases, data, { timeout: 120000 })
}

export function changeFirmwareEnabled(releaseId: number, enabled: boolean) {
  return http.patch<FirmwareRelease>(`${api.firmware.releases}/${releaseId}/enabled`, { enabled })
}

export function deleteFirmware(releaseId: number) {
  return http.delete(`${api.firmware.releases}/${releaseId}`)
}
