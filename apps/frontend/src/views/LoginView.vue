<template>
  <div class="login-view">
    <!-- 无障碍跳转链接：绝对定位跳到根元素顶部，视觉上不受影响 -->
    <a href="#main-content" class="skip-link">跳转到登录</a>

    <!-- 主要内容区域 -->
    <div class="login-content" id="main-content" role="main">
      <!-- 主题切换按钮：固定在右上角 -->
      <div class="theme-toggle-wrapper">
        <ThemeToggle />
      </div>

      <!-- 移动端居中容器 -->
      <div class="mobile-center-container">
        <!-- 品牌区域 -->
        <div class="brand-section">
          <div class="brand-logo">
            <BrandLogo class="logo-icon" :size="48" aria-hidden="true" />
            <h1 class="brand-name">GoPair</h1>
          </div>
        </div>

        <!-- 登录表单区域 -->
        <div class="form-section">
          <div class="form-card">
            <LoginForm />
          </div>
        </div>
      </div>

      <!-- 桌面端和平板端保持原有结构 -->
      <div class="desktop-tablet-container">
        <!-- 品牌区域 -->
        <div class="brand-section">
          <div class="brand-logo">
            <BrandLogo class="logo-icon" :size="48" aria-hidden="true" />
            <span class="brand-subtitle">基于微服务架构的在线聊天室系统</span>
          </div>
        </div>

        <!-- 登录表单区域 -->
        <div class="form-section">
          <div class="form-card">
            <LoginForm />
          </div>
        </div>
      </div>
    </div>

    <!-- 页脚信息 -->
    <div class="page-footer">
      <p>&copy; 2026 基于微服务架构的在线聊天室系统的设计与实现.陈义鑫</p>
    </div>
  </div>
</template>
  
  <script setup lang="ts">
import LoginForm from '@/components/LoginForm.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
</script>
  
  <style scoped>

/* ==================== 无障碍跳转链接 ==================== */
.skip-link {
  position: absolute;
  top: -100%;
  left: 16px;
  z-index: 9999;
  padding: 8px 16px;
  background: var(--brand-primary);
  color: var(--text-on-primary);
  border-radius: 0 0 8px 8px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: top 0.2s;
}
.skip-link:focus {
  top: 0;
}

/* ==================== 基础布局样式 ==================== */
  
/* 登录页面主容器：全屏布局，背景图片 + 暗色遮罩，flex布局 */
.login-view {
  position: relative;
  min-height: 100vh;
  background-image: url('/bg-main.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 背景遮罩：统一使用 --bg-overlay，浅色白色遮罩保留背景图可见，深色深色遮罩实现暗色沉浸 */
.login-view::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-overlay);
  z-index: 0;
}

/* 登录内容容器：flex布局，水平居中，最大宽度限制，置于遮罩层上方 */
.login-content {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 50px 80px;
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  min-height: 0;
}

/* 主题切换按钮：固定在右上角 */
.theme-toggle-wrapper {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 2;
}
  
/* ==================== 品牌展示区域 ==================== */

/* 品牌区域：左侧展示，flex自适应 */
.brand-section {
  flex: 1;
  padding-right: 60px;
  color: var(--text-on-header);

  /* 品牌区域进入动画 */
  animation: slideInLeft 0.8s ease-out;
}

/* 品牌Logo容器：横向布局，Logo + 副标题 */
.brand-logo {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 24px;
}

/* Logo图标：六边形品牌图形 */
.logo-icon {
  margin-right: 12px;
  flex-shrink: 0;
}

/* 品牌名称：大字体，渐变文字效果，突出品牌 */
.brand-name {
  font-size: 48px;
  font-weight: 700;
  margin: 0;
  color: var(--text-on-header);
}

/* 品牌副标题：描述文字 */
.brand-subtitle {
  font-size: 48px;
  font-weight: 400;
  color: var(--text-on-header);
  letter-spacing: 1px;
  margin-left: 12px;
  margin-top: 0;
}
  
  /* ==================== 登录表单区域 ==================== */
  
  /* 表单区域：右侧固定宽度，居中对齐 */
.form-section {
  flex: 0 0 560px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 登录卡片：半透明背景，毛玻璃效果，圆角阴影 */
.form-card {
  background: var(--surface-card);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 48px;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-default);
  width: 480px;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  /* 登录卡片进入动画 */
  animation: slideInRight 0.8s ease-out;
}

/* 卡片悬停效果：轻微上浮，增强阴影 */
.form-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 30px 80px var(--shadow-color-md);
}
  
  /* ==================== 页脚区域 ==================== */
  
  /* 页脚：版权信息，半透明文字，底部居中 */
.page-footer {
  text-align: center;
  padding: 20px;
  color: var(--text-on-header-muted);
  font-size: 14px;
  position: relative;
  z-index: 1;
}
  
/* ==================== 动画定义 ==================== */

@keyframes slideInLeft {
  from { opacity: 0; transform: translateX(-50px); }
  to   { opacity: 1; transform: translateX(0); }
}

@keyframes slideInRight {
  from { opacity: 0; transform: translateX(50px); }
  to   { opacity: 1; transform: translateX(0); }
}

/* ==================== 两段式响应式布局 ==================== */

.mobile-center-container {
  display: none;
}

.desktop-tablet-container {
  display: flex;
  align-items: center;
  width: 100%;
}

/* 精简布局适配：上下结构，适合竖屏或小面积设备 (当屏幕宽度小于 1024px 或 宽高比小于 6:5 )*/
@media not ((min-aspect-ratio: 6/5) and (min-width: 1024px)) {
  .login-content {
    padding: 30px 30px;
  }
  
  /* 隐藏宽屏布局容器 */
  .desktop-tablet-container {
    display: none;
  }
  
  /* 显示精简布局容器：上下结构 */
  .mobile-center-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    max-width: 400px;
    margin: 0 auto;
  }
  
  .brand-section {
    padding-right: 0;
    margin-bottom: 24px;
    text-align: center;
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .brand-logo {
    justify-content: center;
    margin-bottom: 20px;
    flex-direction: column;
    align-items: center;
  }

  .brand-subtitle {
    text-align: center;
    font-size: 14px;
    margin-left: 0;
  }

  .logo-icon {
    margin-right: 0;
  }

  .brand-name {
    font-size: 32px;
  }
  
  .form-section {
    flex: 0 0 auto;
    width: 100%;
    max-width: 480px;
  }
  
  .form-card {
    padding: 20px;
    width: 100%;
  }
  
  .page-footer {
    font-size: 14px;
    padding: 20px;
  }
  
}
  </style> 