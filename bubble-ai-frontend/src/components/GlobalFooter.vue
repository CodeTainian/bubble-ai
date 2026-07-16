<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
          class="footer-link"
        >
          晋ICP备2026009094号-1
        </a>
        <span class="divider">｜</span>
        <span>站长：CodeTianian</span>
        <span class="divider">｜</span>
        <a class="footer-link" href="mailto:sxtianian@outlook.com">邮箱：sxtianian@outlook.com</a>
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  border-top: 1px solid rgba(255, 255, 255, .46);
  background: rgba(255, 255, 255, .72);
  backdrop-filter: blur(12px);
  text-align: center;
  padding: 20px;
}

.copyright {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
  margin: 0;
  color: #666;
  font-size: 14px;
}

.footer-link {
  color: inherit;
}

.footer-link:hover {
  color: #1677ff;
}

@media (max-width: 640px) {
  .copyright {
    line-height: 1.8;
  }

  .divider {
    display: none;
  }

  .copyright > * {
    flex-basis: 100%;
  }
}
</style>
