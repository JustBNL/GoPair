<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { TableProps } from 'ant-design-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { userApi } from '@/api/users'
import { formatTime } from '@/utils/format'
import type { User, UserQuery } from '@/types'

const loading    = ref(false)
const userList   = ref<User[]>([])
const pagination = reactive({ total: 0, current: 1, pageSize: 20 })
const searchKw  = ref('')
const statusFilter = ref<string | undefined>(undefined)

const drawerVisible  = ref(false)
const drawerLoading = ref(false)
const userDetail    = ref<{ user: User; roomCount: number; ownedRoomCount: number } | null>(null)

const confirmOpen   = ref(false)
const confirmTarget = ref<User | null>(null)
const confirmAction = ref<'disable' | 'enable'>('disable')
const confirmLoading = ref(false)

const migrateOpen    = ref(false)
const migrateLoading = ref(false)
const migrateTarget = ref<User | null>(null)
const migrateEmail   = ref('')

async function loadUsers() {
  loading.value = true
  try {
    const params: UserQuery = {
      pageNum: pagination.current,
      pageSize: pagination.pageSize,
      keyword: searchKw.value || undefined,
      status: statusFilter.value || undefined,
    }
    const res = await userApi.getPage(params)
    userList.value   = res.records as User[]
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

const columns = [
  { title: '用户ID',    dataIndex: 'userId',    key: 'userId',    width: 90 },
  { title: '昵称',      dataIndex: 'nickname',  key: 'nickname',  ellipsis: true },
  { title: '邮箱',      dataIndex: 'email',     key: 'email',     ellipsis: true },
  { title: '状态',      dataIndex: 'status',    key: 'status',    width: 90 },
  { title: '注册时间',  dataIndex: 'createTime',key: 'createTime',width: 170 },
  { title: '操作',      key: 'actions',        width: 120, fixed: 'right' },
]

async function handleView(userId: number) {
  drawerVisible.value  = true
  drawerLoading.value  = true
  userDetail.value     = null
  try {
    const res = await userApi.getDetail(userId)
    userDetail.value = res
  } catch {
    message.error('获取用户详情失败')
  } finally {
    drawerLoading.value = false
  }
}

function openConfirm(user: User, action: 'disable' | 'enable') {
  confirmTarget.value  = user
  confirmAction.value = action
  confirmOpen.value   = true
}

function openMigrate(user: User) {
  migrateTarget.value = user
  migrateEmail.value  = ''
  migrateOpen.value   = true
}

async function handleMigrate() {
  if (!migrateTarget.value || !migrateEmail.value.trim()) return
  migrateLoading.value = true
  try {
    await userApi.migrateEmail(migrateTarget.value.userId, migrateEmail.value.trim())
    message.success('账号迁移成功')
    migrateOpen.value = false
    drawerVisible.value = false
    loadUsers()
  } catch (e: unknown) {
    const err = e as { message?: string }
    message.error(err?.message || '迁移失败')
  } finally {
    migrateLoading.value = false
  }
}

async function handleConfirm() {
  if (!confirmTarget.value) return
  confirmLoading.value = true
  try {
    if (confirmAction.value === 'disable') {
      await userApi.disable(confirmTarget.value.userId)
      message.success('用户已停用')
    } else {
      await userApi.enable(confirmTarget.value.userId)
      message.success('用户已启用')
    }
    loadUsers()
  } catch (e: unknown) {
    const err = e as { message?: string }
    message.error(err?.message || '操作失败')
  } finally {
    confirmLoading.value = false
  }
}

const onPageChange: TableProps['onChange'] = (pag) => {
  pagination.current = pag.current as number
  pagination.pageSize = pag.pageSize as number
  loadUsers()
}

let searchTimer: ReturnType<typeof setTimeout>
function onSearch(value: string) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchKw.value = value
    pagination.current = 1
    loadUsers()
  }, 350)
}

loadUsers()
</script>

<template>
  <div class="user-manage-view">
    <PageHeader title="用户管理" description="查看和管理所有用户账号" />

    <div class="user-manage-view__toolbar">
      <a-input-search
        v-model:value="searchKw"
        aria-label="搜索昵称或邮箱"
        placeholder="搜索昵称或邮箱"
        class="user-manage-view__search"
        @search="onSearch"
        @change="() => { pagination.current = 1; loadUsers() }"
        style="width: 280px;"
      />
      <a-select
        v-model:value="statusFilter"
        placeholder="状态筛选"
        allow-clear
        style="width: 140px;"
        @change="() => { pagination.current = 1; loadUsers() }"
      >
        <a-select-option :value="'0'">正常</a-select-option>
        <a-select-option :value="'1'">停用</a-select-option>
        <a-select-option :value="'2'">已注销</a-select-option>
      </a-select>
    </div>

    <a-table
      :columns="columns"
      :data-source="userList"
      :loading="loading"
      :pagination="{ ...pagination, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` }"
      :scroll="{ x: 800 }"
      class="user-manage-view__table"
      @change="onPageChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'userId'">
          <span class="user-manage-view__mono">{{ record.userId }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <StatusBadge :status="record.status" type="user" />
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="user-manage-view__muted">{{ formatTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <div class="user-manage-view__actions">
            <a-button type="link" size="small" @click="handleView(record.userId)">详情</a-button>
            <a-button v-if="record.status === '0'" type="link" size="small" danger @click="openConfirm(record, 'disable')">停用</a-button>
            <a-button v-else-if="record.status === '1'" type="link" size="small" @click="openConfirm(record, 'enable')">启用</a-button>
          </div>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="drawerVisible" title="用户详情" :width="480" :loading="drawerLoading">
      <template v-if="userDetail">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="用户ID">{{ userDetail.user.userId }}</a-descriptions-item>
          <a-descriptions-item label="昵称">{{ userDetail.user.nickname }}</a-descriptions-item>
          <a-descriptions-item label="邮箱">
            {{ userDetail.user.email }}
            <a-button type="link" size="small" @click="openMigrate(userDetail.user)">账号迁移</a-button>
          </a-descriptions-item>
          <a-descriptions-item label="状态"><StatusBadge :status="userDetail.user.status" type="user" /></a-descriptions-item>
          <a-descriptions-item label="加入房间数">{{ userDetail.roomCount }}</a-descriptions-item>
          <a-descriptions-item label="创建房间数">{{ userDetail.ownedRoomCount }}</a-descriptions-item>
          <a-descriptions-item label="注册时间">{{ formatTime(userDetail.user.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="最后更新">{{ formatTime(userDetail.user.updateTime) }}</a-descriptions-item>
          <a-descriptions-item v-if="userDetail.user.remark" label="备注">{{ userDetail.user.remark }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>

    <ConfirmModal
      v-model:open="confirmOpen"
      :title="confirmAction === 'disable' ? '停用用户' : '启用用户'"
      :content="confirmAction === 'disable'
        ? `确定停用用户「${confirmTarget?.nickname}」？停用后该用户将无法登录。`
        : `确定启用用户「${confirmTarget?.nickname}」？启用后该用户可正常登录。`"
      :confirm-text="confirmAction === 'disable' ? '停用' : '启用'"
      :danger="confirmAction === 'disable'"
      :loading="confirmLoading"
      @confirm="handleConfirm"
    />

    <a-modal
      v-model:open="migrateOpen"
      title="账号迁移"
      :confirm-loading="migrateLoading"
      :mask-closable="false"
      @ok="handleMigrate"
    >
      <div style="margin-bottom: 12px;">
        将用户 <strong>{{ migrateTarget?.nickname }}</strong> 的邮箱变更为：
      </div>
      <a-input
        v-model:value="migrateEmail"
        placeholder="请输入新邮箱地址"
        size="large"
        @keyup.enter="handleMigrate"
      />
      <div style="margin-top: 8px; color: var(--color-text-muted); font-size: 12px;">
        迁移后该用户将使用新邮箱登录，旧邮箱可释放。
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.user-manage-view__toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.user-manage-view__table {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.user-manage-view__mono { font-family: var(--font-mono); font-size: 13px; color: var(--color-text-secondary); }
.user-manage-view__muted { font-size: 13px; color: var(--color-text-muted); }

.user-manage-view__actions { display: flex; gap: var(--space-2); align-items: center; }
</style>
