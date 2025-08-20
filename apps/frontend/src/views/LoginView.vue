<template>
  <div class="login-view">
    <!-- 背景装饰 -->
    <div class="background-decoration">
      <div class="decoration-circle circle-1"></div>
      <div class="decoration-circle circle-2"></div>
      <div class="decoration-circle circle-3"></div>
    </div>

    <!-- 主要内容区域 -->
    <div class="login-content">
      <!-- 品牌区域 -->
      <div class="brand-section">
        <div class="brand-logo">
          <div class="logo-icon">🎮</div>
          <h1 class="brand-name">GoPair</h1>
        </div>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-icon">💬</span>
            <span class="feature-text">实时聊天交流</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🕹️</span>
            <span class="feature-text">多种游戏体验</span>
          </div>
        </div>
      </div>

      <!-- 登录表单区域 -->
      <div class="form-section">
        <div class="form-card">
          <LoginForm />
        </div>
      </div>
    </div>

    <!-- 页脚信息 -->
    <div class="page-footer">
      <p>&copy; 2024 GoPair. 只因从未离去</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import LoginForm from '@/components/LoginForm.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(() => {
  // 如果已经登录，重定向到主页面
  if (authStore.isLoggedIn) {
    router.push('/')
  }
})
</script>

<style scoped>
/* ==================== 基础布局样式 ==================== */

/* 登录页面主容器：全屏布局，渐变背景，flex垂直布局 */
.login-view {
  min-height: 100vh;
  position: relative;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  
  /* 页面进入动画 */
  animation: fadeIn 0.8s ease-out;
}

/* 登录内容容器：flex布局，水平居中，最大宽度限制 */
.login-content {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 40px 20px;
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

/* ==================== 背景装饰效果 ==================== */

/* 背景装饰容器：绝对定位，不影响布局，纯视觉效果 */
.background-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

/* 装饰圆圈基础样式：半透明白色，毛玻璃效果 */
.decoration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}

/* 装饰圆圈1：大圆，右上角，6秒浮动动画 */
.circle-1 {
  width: 300px;
  height: 300px;
  top: -150px;
  right: -150px;
  animation: float 6s ease-in-out infinite;
}

/* 装饰圆圈2：中圆，左下角，8秒反向浮动动画 */
.circle-2 {
  width: 200px;
  height: 200px;
  bottom: -100px;
  left: -100px;
  animation: float 8s ease-in-out infinite reverse;
}

/* 装饰圆圈3：小圆，左侧中部，10秒浮动动画 */
.circle-3 {
  width: 150px;
  height: 150px;
  top: 50%;
  left: 10%;
  animation: float 10s ease-in-out infinite;
}

/* ==================== 品牌展示区域 ==================== */

/* 品牌区域：左侧展示，flex自适应，白色文字 */
.brand-section {
  flex: 1;
  padding-right: 60px;
  color: white;
  
  /* 品牌区域进入动画 */
  animation: slideInLeft 0.8s ease-out;
}

/* 品牌Logo容器：水平布局，Logo + 标题 */
.brand-logo {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
}

/* Logo图标：游戏手柄，大尺寸 */
.logo-icon {
  font-size: 48px;
  margin-right: 16px;
}

/* 品牌名称：大字体，渐变文字效果，突出品牌 */
.brand-name {
  font-size: 48px;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(45deg, #fff, #e2e8f0);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 品牌标语：中等字体，半透明，说明产品定位 */
.brand-slogan {
  font-size: 24px;
  margin-bottom: 48px;
  opacity: 0.9;
  font-weight: 300;
}

/* 特性列表容器：垂直布局，展示产品特色 */
.brand-features {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 单个特性项：水平布局，图标 + 文字 */
.feature-item {
  display: flex;
  align-items: center;
  font-size: 18px;
  opacity: 0.8;
}

/* 特性图标：固定宽度，保持对齐 */
.feature-icon {
  font-size: 24px;
  margin-right: 16px;
  width: 40px;
}

/* 特性文字：正常字重 */
.feature-text {
  font-weight: 400;
}

/* ==================== 登录表单区域 ==================== */

/* 表单区域：右侧固定宽度，居中对齐 */
.form-section {
  flex: 0 0 480px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 登录卡片：半透明背景，毛玻璃效果，圆角阴影 */
.form-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 48px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  
  /* 登录卡片进入动画 */
  animation: slideInRight 0.8s ease-out;
}

/* 卡片悬停效果：轻微上浮，增强阴影 */
.form-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.15);
}

/* ==================== 页脚区域 ==================== */

/* 页脚：版权信息，半透明文字，底部居中 */
.page-footer {
  text-align: center;
  padding: 20px;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  position: relative;
  z-index: 1;
}

/* ==================== 动画定义 ==================== */

/* 页面淡入动画：透明度从0到1 */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 浮动动画：垂直移动 + 旋转效果，营造动态背景 */
@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(180deg);
  }
}

/* 左侧滑入动画：从左侧50px位置滑入 */
@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-50px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 右侧滑入动画：从右侧50px位置滑入 */
@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(50px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* ==================== 响应式布局 - 平板横屏 ==================== */

/* 平板横屏模式：保持桌面端的左右分栏布局，适当缩小尺寸 */
@media (min-width: 768px) and (max-width: 1024px) and (orientation: landscape) {
  .login-content {
    flex-direction: row;
    text-align: left;
    padding: 20px;
  }
  
  .brand-section {
    padding-right: 40px;
  }
  
  /* 品牌文字适度缩小 */
  .brand-name {
    font-size: 36px;
  }
  
  .brand-slogan {
    font-size: 20px;
    margin-bottom: 32px;
  }
  
  /* 特性列表保持垂直布局 */
  .brand-features {
    flex-direction: column;
    gap: 16px;
  }
  
  .feature-item {
    font-size: 16px;
  }
  
  /* 表单区域宽度适配平板 */
  .form-section {
    flex: 0 0 420px;
  }
  
  .form-card {
    padding: 36px;
    max-width: 400px;
  }
}

/* ==================== 响应式布局 - 平板竖屏 ==================== */

/* 平板竖屏模式：上下布局，隐藏特性列表，重点突出登录功能 */
@media (min-width: 768px) and (max-width: 1024px) and (orientation: portrait) {
  /* 整体页面垂直水平居中 */
  .login-view {
    justify-content: center;
    align-items: center;
    min-height: 100vh;
  }
  
  /* 内容区域上下布局，居中显示，并向上偏移以实现视觉居中 */
  .login-content {
    flex-direction: column;
    text-align: center;
    padding: 20px;
    justify-content: center;
    align-items: center;
    min-height: 0;
    flex: 0 0 auto;
    height: auto;
    transform: translateY(-5vh); /* 向上偏移5%视口高度 */
  }
  
  /* 品牌区域简化，减少间距 */
  .brand-section {
    padding-right: 0;
    margin-bottom: 32px;
    flex-shrink: 0;
  }
  
  .brand-logo {
    justify-content: center;
  }
  
  /* 品牌文字适中尺寸 */
  .brand-name {
    font-size: 36px;
  }
  
  .brand-slogan {
    font-size: 20px;
    margin-bottom: 0;
  }
  
  /* 隐藏特性列表，简化界面 */
  .brand-features {
    display: none;
  }
  
  .form-section {
    flex: 0 0 auto;
  }
  
  /* 平板竖屏专用：宽敞的登录表单 */
  .form-card {
    padding: 48px;
    width: 100%;
    margin: 0 auto;
  }
  
  /* 页脚固定定位到底部 */
  .page-footer {
    position: fixed;
    bottom: 20px;
    left: 0;
    right: 0;
    margin-top: 0;
  }
}

/* ==================== 响应式布局 - 手机端 ==================== */

/* 手机端模式：极简设计，只保留核心元素，优化触摸体验 */
@media (max-width: 767px) {
  /* 垂直居中布局 */
  .login-view {
    justify-content: center;
    align-items: center;
    min-height: 100vh;
  }
  
  /* 内容区域紧凑布局，并向上偏移以实现视觉居中 */
  .login-content {
    flex-direction: column;
    text-align: center;
    padding: 16px;
    justify-content: center;
    align-items: center;
    min-height: 0;
    flex: 0 0 auto;
    height: auto;
    transform: translateY(-5vh); /* 向上偏移5%视口高度 */
  }
  
  /* 品牌区域最小化 */
  .brand-section {
    padding-right: 0;
    margin-bottom: 24px;
    flex-shrink: 0;
  }
  
  .brand-logo {
    justify-content: center;
    margin-bottom: 16px;
  }
  
  /* 手机端图标和文字尺寸 */
  .logo-icon {
    font-size: 32px;
  }
  
  .brand-name {
    font-size: 24px;
  }
  
  .brand-slogan {
    font-size: 14px;
    margin-bottom: 0;
  }
  
  /* 完全隐藏特性列表，专注登录 */
  .brand-features {
    display: none;
  }
  
  .form-section {
    flex: 0 0 auto;
  }
  
  /* 手机端表单样式：圆角、间距优化 */
  .form-card {
    padding: 28px;
    border-radius: 16px;
    margin: 0 auto;
    max-width: 360px;
    width: 100%;
  }
  
  /* 降低背景装饰干扰，提升可读性 */
  .circle-1,
  .circle-2,
  .circle-3 {
    opacity: 0.3;
  }
  
  /* 手机端页脚：更小字体，适当间距 */
  .page-footer {
    font-size: 12px;
    padding: 16px;
    position: fixed;
    bottom: 10px;
    left: 0;
    right: 0;
    margin-top: 0;
  }
}
</style> 