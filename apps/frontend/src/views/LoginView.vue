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
        <!-- 移动端居中容器 -->
        <div class="mobile-center-container">
          <!-- 品牌区域 -->
          <div class="brand-section">
            <div class="brand-logo">
              <div class="logo-icon">🎮</div>
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
      </div>
  
      <!-- 页脚信息 -->
      <div class="page-footer">
        <p>&copy; 2024 GoPair. 只因从未离去</p>
      </div>
    </div>
  </template>
  
  <script setup lang="ts">
import LoginForm from '@/components/LoginForm.vue'
</script>
  
  <style scoped>

/* ==================== 基础布局样式 ==================== */
  
/* 登录页面主容器：全屏布局，渐变背景，flex布局 */
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
  padding: 50px 80px;
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  min-height: 0;
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
  flex: 0 0 560px;
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
  width: 480px;
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
  
/* ==================== 两段式响应式布局 ==================== */

/* 默认显示宽屏布局结构，隐藏精简布局容器 */
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
  }
  
  .brand-logo {
    justify-content: center;
    margin-bottom: 20px;
  }
  
  .logo-icon {
    font-size: 40px;
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