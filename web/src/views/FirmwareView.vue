<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  changeFirmwareEnabled,
  deleteFirmware,
  publishFirmware,
  queryFirmwareReleases,
} from '@/services/firmware'
import type { FirmwareRelease } from '@/types/firmware'

const loading = ref(false)
const publishing = ref(false)
const publishVisible = ref(false)
const releases = ref<FirmwareRelease[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  boardType: '',
  enabled: undefined as boolean | undefined,
})
const publishForm = reactive({
  boardType: 'atk-dnesp32s3-box2-wifi',
  version: '',
  forceUpdate: false,
  enabled: true,
})
const selectedFile = ref<File | null>(null)

const columns = [
  { title: '板型', dataIndex: 'boardType', width: 240 },
  { title: '版本', dataIndex: 'version', width: 100 },
  { title: '大小', dataIndex: 'fileSize', width: 100 },
  { title: 'SHA-256', dataIndex: 'sha256', ellipsis: true },
  { title: '强制升级', dataIndex: 'forceUpdate', width: 100, align: 'center' },
  { title: '启用', dataIndex: 'enabled', width: 90, align: 'center' },
  { title: '发布时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', dataIndex: 'operation', width: 180, fixed: 'right', align: 'center' },
]

async function loadData() {
  loading.value = true
  try {
    const response = await queryFirmwareReleases({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      boardType: query.boardType || undefined,
      enabled: query.enabled,
    })
    releases.value = response.data.list
    total.value = response.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.pageNo = 1
  query.boardType = ''
  query.enabled = undefined
  void loadData()
}

function beforeUpload(file: File) {
  if (!file.name.toLowerCase().endsWith('.bin')) {
    message.error('只能上传 .bin 固件')
    return false
  }
  selectedFile.value = file
  return false
}

function removeFile() {
  selectedFile.value = null
}

async function handlePublish() {
  if (!publishForm.boardType || !publishForm.version || !selectedFile.value) {
    message.warning('请填写板型、版本并选择固件文件')
    return
  }
  publishing.value = true
  try {
    await publishFirmware({ ...publishForm, file: selectedFile.value })
    message.success('固件发布成功')
    publishVisible.value = false
    publishForm.version = ''
    selectedFile.value = null
    query.pageNo = 1
    await loadData()
  } finally {
    publishing.value = false
  }
}

async function handleEnabled(release: FirmwareRelease, enabled: boolean) {
  try {
    await changeFirmwareEnabled(release.releaseId, enabled)
    release.enabled = enabled
    message.success(enabled ? '已启用' : '已禁用')
  } catch {
    release.enabled = !enabled
  }
}

function handleDelete(release: FirmwareRelease) {
  if (release.enabled) {
    message.warning('请先禁用固件再删除')
    return
  }
  Modal.confirm({
    title: `删除 ${release.boardType} ${release.version}？`,
    content: '元数据提交删除后，本地固件文件也会被清理。',
    okType: 'danger',
    async onOk() {
      await deleteFirmware(release.releaseId)
      message.success('删除成功')
      await loadData()
    },
  })
}

function formatSize(bytes: number) {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(2)} MB`
    : `${(bytes / 1024).toFixed(1)} KB`
}

function handlePageChange(pageNo: number, pageSize: number) {
  query.pageNo = pageNo
  query.pageSize = pageSize
  void loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="firmware-page">
    <a-card :bordered="false">
      <div class="toolbar">
        <a-space wrap>
          <a-input
            v-model:value="query.boardType"
            placeholder="板型，例如 atk-dnesp32s3-box2-wifi"
            allow-clear
            class="board-input"
            @press-enter="query.pageNo = 1; loadData()"
          />
          <a-select v-model:value="query.enabled" placeholder="全部状态" allow-clear class="status-select">
            <a-select-option :value="true">已启用</a-select-option>
            <a-select-option :value="false">已禁用</a-select-option>
          </a-select>
          <a-button type="primary" @click="query.pageNo = 1; loadData()">查询</a-button>
          <a-button @click="resetQuery">重置</a-button>
        </a-space>
        <a-button v-permission="'system:firmware:api:create'" type="primary" @click="publishVisible = true">
          发布固件
        </a-button>
      </div>

      <a-alert
        class="ota-tip"
        type="info"
        show-icon
        message="OTA 只向 board.type 完全匹配且版本更高的设备下发；版本仅支持 1.6.0 这类数字点分格式。"
      />

      <a-table
        row-key="releaseId"
        :columns="columns"
        :data-source="releases"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1250 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'fileSize'">
            {{ formatSize(record.fileSize) }}
          </template>
          <template v-else-if="column.dataIndex === 'sha256'">
            <a-typography-text copyable :content="record.sha256">{{ record.sha256 }}</a-typography-text>
          </template>
          <template v-else-if="column.dataIndex === 'forceUpdate'">
            <a-tag :color="record.forceUpdate ? 'orange' : 'default'">
              {{ record.forceUpdate ? '是' : '否' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'enabled'">
            <a-switch
              v-model:checked="record.enabled"
              v-permission="'system:firmware:api:update'"
              @change="(checked: boolean) => handleEnabled(record, checked)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space>
              <a :href="record.downloadUrl">下载</a>
              <a-button
                v-permission="'system:firmware:api:delete'"
                type="link"
                danger
                size="small"
                @click="handleDelete(record)"
              >删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-pagination
        class="pagination"
        :current="query.pageNo"
        :page-size="query.pageSize"
        :total="total"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handlePageChange"
      />
    </a-card>

    <a-modal
      v-model:open="publishVisible"
      title="发布固件"
      :confirm-loading="publishing"
      @ok="handlePublish"
    >
      <a-form layout="vertical">
        <a-form-item label="板型" required>
          <a-input v-model:value="publishForm.boardType" />
        </a-form-item>
        <a-form-item label="版本" required extra="2 到 4 段数字，例如 1.6.0；不支持 v 前缀和 -rc 后缀。">
          <a-input v-model:value="publishForm.version" placeholder="1.6.0" />
        </a-form-item>
        <a-form-item label="固件文件" required>
          <a-upload
            :before-upload="beforeUpload"
            :file-list="selectedFile ? [{ uid: '-1', name: selectedFile.name, status: 'done' }] : []"
            :max-count="1"
            accept=".bin,application/octet-stream"
            @remove="removeFile"
          >
            <a-button>选择 .bin 文件</a-button>
          </a-upload>
        </a-form-item>
        <a-form-item label="强制升级">
          <a-switch v-model:checked="publishForm.forceUpdate" />
        </a-form-item>
        <a-form-item label="发布后立即启用">
          <a-switch v-model:checked="publishForm.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.firmware-page { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.board-input { width: 310px; }
.status-select { width: 120px; }
.ota-tip { margin-bottom: 16px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 20px; }
@media (max-width: 768px) {
  .firmware-page { padding: 12px; }
  .toolbar { align-items: stretch; flex-direction: column; }
  .board-input { width: 100%; }
}
</style>
