import type { PageQueryParams } from './api'

export interface FirmwareRelease {
  releaseId: number
  boardType: string
  version: string
  fileSize: number
  sha256: string
  forceUpdate: boolean
  enabled: boolean
  downloadUrl: string
  createTime?: string
  updateTime?: string
}

export interface FirmwareReleaseQuery extends PageQueryParams {
  boardType?: string
  enabled?: boolean
}

export interface FirmwarePublishForm {
  file: File
  boardType: string
  version: string
  forceUpdate: boolean
  enabled: boolean
}
