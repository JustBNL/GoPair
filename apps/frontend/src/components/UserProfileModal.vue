<template>
  <a-modal
    :open="visible"
    title="个人资料设置"
    :footer="null"
    :width="480"
    @cancel="handleClose"
    class="profile-modal"
  >
    <div class="profile-content">
      <!-- 头像区域 -->
      <div class="avatar-section">
        <a-upload
          :show-upload-list="false"
          accept="image/jpeg,image/png,image/gif,image/webp"
          :before-upload="beforeAvatarUpload"
          :custom-request="handleAvatarUpload"
        >
          <div class="avatar-wrapper">
            <a-spin :spinning="avatarUploading">
              <img v-if="avatarPreview" :src="avatarPreview" class="avatar-img" alt="avatar" />
              <div v-else class="avatar-placeholder">{{ nicknameInitial }}</div>
            </a-spin>
            <div class="avatar-overlay">
              <span class="avatar-overlay-text">更换头像</span>
            </div>
          </div>
        </a-upload>
        <div class="avatar-tips">
          <p class="avatar-tip-title">点击头像更换</p>
          <p class="avatar-tip-desc">支持 JPG / PNG / GIF / WebP</p>
          <p class="avatar-tip-desc">文件大小不超过 5MB</p>
        </div>
      </div>

      <a-divider />

      <!-- 表单区域 -->
      <a-form :model="form" layout="vertical" class="profile-form">
        <!-- 昵称 -->
        <a-form-item label="昵称" v-bind="validateInfos.nickname">
          <a-input
            v-model:value="form.nickname"
            placeholder="请输入昵称（1-20个字符）"
            :maxlength="20"
            show-count
          />
        </a-form-item>

        <!-- 邮箱（只读展示） -->
        <a-form-item label="邮箱">
          <div class="email-readonly">{{ form.email || '-' }}</div>
        </a-form-item>

        <!-- 修改密码（折叠） -->
        <div class="password-section">
          <div class="password-toggle" @click="showPassword = !showPassword">
            <span>修改密码</span>
            <DownOutlined :class="['toggle-icon', { rotated: showPassword }]" />
          </div>

          <div v-if="showPassword" class="password-fields">
            <a-form-item label="当前密码" v-bind="validateInfos.currentPassword">
              <a-input-password
                v-model:value="form.currentPassword"
                placeholder="请输入当前密码"
                :maxlength="50"
              />
            </a-form-item>
            <a-form-item label="新密码" v-bind="validateInfos.password">
              <a-input-password
                v-model:value="form.password"
                placeholder="请输入新密码（6-50个字符）"
                :maxlength="50"
              />
            </a-form-item>
            <a-form-item label="确认密码" v-bind="validateInfos.confirmPassword">
              <a-input-password
                v-model:value="form.confirmPassword"
                placeholder="请再次输入新密码"
                :maxlength="50"
              />
            </a-form-item>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="form-actions">
          <a-button @click="handleClose">取消</a-button>
          <a-button
            type="primary"
            :loading="loading"
            @click="handleSubmit"
            class="save-btn"
          >
            保存修改
          </a-button>
        </div>
      </a-form>

      <!-- 注销账号区域 -->
      <a-divider />
      <div class="cancel-account-section">
        <div class="cancel-account-info">
          <p class="cancel-account-title">注销账号</p>
          <p class="cancel-account-desc">注销后账号将永久停用，原邮箱可重新注册，请谨慎操作。</p>
        </div>
        <a-button
          danger
          :loading="cancelLoading"
          @click="showCancelConfirm = true"
          class="cancel-account-btn"
        >
          注销账号
        </a-button>
      </div>
    </div>
  </a-modal>

  <!-- 注销确认弹窗 -->
  <a-modal
    :open="showCancelConfirm"
    title="确认注销账号"
    ok-text="确认注销"
    cancel-text="我再想想"
    ok-type="danger"
    :confirm-loading="cancelLoading"
    @ok="handleCancelAccount"
    @cancel="showCancelConfirm = false"
    :width="400"
  >
    <div class="cancel-confirm-content">
      <p>注销后将发生以下情况：</p>
      <ul>
        <li>账号立即失效，无法再次登录</li>
        <li>原邮箱将被释放，可用于重新注册</li>
        <li>历史数据将被保留但不可访问</li>
      </ul>
      <p class="cancel-confirm-warning">此操作<strong>不可撤销</strong>，请确认您已了解上述风险。</p>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Form, message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { FileAPI } from '@/api/file'
import { useRouter } from 'vue-router'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const showPassword = ref(false)
const avatarUploading = ref(false)
const cancelLoading = ref(false)
const showCancelConfirm = ref(false)

const form = ref({
  nickname: '',
  email: '',
  avatar: '',
  currentPassword: '',
  password: '',
  confirmPassword: ''
})

const avatarPreview = ref('')

// 头像首字母
const nicknameInitial = computed(() => {
  const name = form.value.nickname || authStore.currentNickname
  return name.charAt(0).toUpperCase()
})

// 监听弹框打开，填充当前用户信息
watch(
  () => props.visible,
  (val) => {
    if (val) {
      form.value.nickname = authStore.user?.nickname || ''
      form.value.email = authStore.user?.email || ''
      form.value.avatar = authStore.user?.avatar || ''
      form.value.currentPassword = ''
      form.value.password = ''
      form.value.confirmPassword = ''
      avatarPreview.value = authStore.user?.avatar || ''
      showPassword.value = false
    }
  }
)

// 上传前校验
function beforeAvatarUpload(file: File): boolean {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    message.error('仅支持 JPG / PNG / GIF / WebP 格式的图片')
    return false
  }
  const maxSize = 5 * 1024 * 1024
  if (file.size > maxSize) {
    message.error('头像文件不能超过 5MB')
    return false
  }
  return true
}

// 自定义上传头像
async function handleAvatarUpload({ file }: { file: File }) {
  avatarUploading.value = true
  try {
    const res = await FileAPI.uploadAvatar(file)
    form.value.avatar = res.data
    avatarPreview.value = res.data
    message.success('头像上传成功')
  } catch {
    message.error('头像上传失败，请重试')
  } finally {
    avatarUploading.value = false
  }
}

// 表单验证规则
const { validate, validateInfos } = Form.useForm(form, {
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称长度为 1-20 个字符', trigger: 'blur' }
  ],
  currentPassword: [
    {
      validator: (_: any, value: string) => {
        if (form.value.password && !value) {
          return Promise.reject('修改密码时必须输入当前密码')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ],
  password: [
    {
      validator: (_: any, value: string) => {
        if (!value) return Promise.resolve()
        if (value.length < 6 || value.length > 50) {
          return Promise.reject('密码长度为 6-50 个字符')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator: (_: any, value: string) => {
        if (!form.value.password) return Promise.resolve()
        if (value !== form.value.password) {
          return Promise.reject('两次密码输入不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
})

async function handleSubmit() {
  await validate()

  const payload: Record<string, string> = {}
  if (form.value.nickname) payload.nickname = form.value.nickname
  if (form.value.avatar !== undefined) payload.avatar = form.value.avatar
  if (form.value.password) {
    payload.password = form.value.password
    payload.currentPassword = form.value.currentPassword
  }

  if (Object.keys(payload).length === 0) {
    message.info('没有检测到任何修改')
    handleClose()
    return
  }

  loading.value = true
  try {
    await authStore.updateProfile(payload)
    handleClose()
  } finally {
    loading.value = false
  }
}

// 执行注销账号
async function handleCancelAccount() {
  cancelLoading.value = true
  try {
    await authStore.cancelAccount()
    showCancelConfirm.value = false
    emit('update:visible', false)
    // 注销成功后跳转到登录页
    router.push('/login')
  } catch {
    // 错误已在 store 层通过 message 提示，此处无需重复
  } finally {
    cancelLoading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<style scoped>
.profile-content {
  padding: 8px 0;
}

/* 头像区域 */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 4px;
}

.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
}

.avatar-img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--brand-primary);
  display: block;
}

.avatar-placeholder {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--brand-primary);
  color: var(--text-on-primary);
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--brand-primary);
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: var(--overlay-dark-45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay-text {
  color: var(--text-on-primary);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  line-height: 1.3;
  pointer-events: none;
}

.avatar-tips {
  flex: 1;
}

.avatar-tip-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.avatar-tip-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.6;
}

/* 密码折叠区 */
.password-section {
  margin-bottom: 16px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}

.password-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  background: var(--surface-bg);
  font-size: 14px;
  color: var(--text-primary);
  user-select: none;
  transition: background 0.2s;
}

.password-toggle:hover {
  background: var(--border-light);
}

.toggle-icon {
  transition: transform 0.25s;
  font-size: 12px;
  color: var(--text-muted);
}

.toggle-icon.rotated {
  transform: rotate(180deg);
}

.password-fields {
  padding: 16px 16px 0;
}

/* 操作按钮 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}

.save-btn {
  background: var(--brand-primary);
  border: none;
}

.save-btn:hover {
  background: var(--brand-primary-hover);
}

/* 注销账号区域 */
.cancel-account-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0 8px;
}

.cancel-account-info {
  flex: 1;
}

.cancel-account-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.cancel-account-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.5;
}

.cancel-account-btn {
  flex-shrink: 0;
}

/* 邮箱只读文本 */
.email-readonly {
  color: var(--text-secondary);
  font-size: 14px;
  padding: 0 4px;
}

/* 注销确认弹窗内容 */
.cancel-confirm-content p {
  color: var(--text-primary);
  margin: 0 0 8px;
}

.cancel-confirm-content ul {
  margin: 0 0 12px;
  padding-left: 20px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 2;
}

.cancel-confirm-warning {
  color: var(--color-error);
  font-size: 13px;
  margin: 0 !important;
}
</style>
