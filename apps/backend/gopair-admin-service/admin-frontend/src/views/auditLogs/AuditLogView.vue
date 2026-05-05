<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { auditLogApi } from '@/api/auditLogs'
import { formatTime } from '@/utils/format'
import type { AuditLog, AuditQuery } from '@/types'

const loading    = ref(false)
const logList   = ref<AuditLog[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })

const filterOperation  = ref<string | undefined>()
const filterTargetType = ref<string | undefined>()

const operationOptions = [
  { value: 'USER_DISABLE', label: '停用用户' },
  { value: 'USER_ENABLE',  label: '启用用户' },
  { value: 'ROOM_CLOSE',   label: '关闭房间' },
  { value: 'FILE_DELETE',  label: '删除文件' },
]

const targetTypeOptions = [
  { value: 'USER', label: '用户' },
  { value: 'ROOM', label: '房间' },
  { value: 'FILE', label: '文件' },
]

async function loadLogs() {
  loading.value = true
  try {
    const params: AuditQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      operation:  filterOperation.value,
      targetType: filterTargetType.value,
    }
    const res = await auditLogApi.getPage(params)
    logList.value    = res.records as AuditLog[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: 'ID',        dataIndex: 'id',             key: 'id',             width: 80 },
  { title: '管理员',    dataIndex: 'adminUsername', key: 'adminUsername', width: 120 },
  { title: '操作类型',  dataIndex: 'operation',       key: 'operation',      width: 130 },
  { title: '目标类型',  dataIndex: 'targetType',      key: 'targetType',     width: 100 },
  { title: '目标ID',   dataIndex: 'targetId',        key: 'targetId',       width: 90 },
  { title: 'IP地址',    dataIndex: 'ipAddress',       key: 'ipAddress',      width: 130 },
  { title: '操作时间',  dataIndex: 'createTime',      key: 'createTime',     width: 170 },
]

const operationLabel = (op: string) => operationOptions.find(o => o.value === op)?.label || op
const targetTypeLabel = (t: string) => targetTypeOptions.find(o => o.value === t)?.label || t

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadLogs()
}

loadLogs()
</script>

<template>
  <div class="audit-log-view">
    <PageHeader title="审计日志" description="查看管理员操作记录" />

    <div class="audit-log-view__toolbar">
      <a-select
        v-model:value="filterOperation"
        placeholder="操作类型"
        allow-clear
        style="width: 140px;"
        @change="() => { pagination.current = 1; loadLogs() }"
      >
        <a-select-option v-for="op in operationOptions" :key="op.value" :value="op.value">{{ op.label }}</a-select-option>
      </a-select>
      <a-select
        v-model:value="filterTargetType"
        placeholder="目标类型"
        allow-clear
        style="width: 120px;"
        @change="() => { pagination.current = 1; loadLogs() }"
      >
        <a-select-option v-for="t in targetTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
      </a-select>
    </div>

    <a-table
      :columns="columns"
      :data-source="logList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 800 }"
      class="audit-log-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'id'">
          <span class="audit-log-view__mono">{{ record.id }}</span>
        </template>
        <template v-else-if="column.key === 'operation'">
          <a-tag>{{ operationLabel(record.operation) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'targetType'">
          <span>{{ targetTypeLabel(record.targetType) }}</span>
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="audit-log-view__muted">{{ formatTime(record.createTime) }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.audit-log-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.audit-log-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.audit-log-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.audit-log-view__muted { font-size: 13px; color: var(--color-text-muted); }
</style>
