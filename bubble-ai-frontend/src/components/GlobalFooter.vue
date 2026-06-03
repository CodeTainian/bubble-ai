<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        <a
          href="https://github.com/CodeTainian/bubble-ai"
          target="_blank"
          rel="noopener noreferrer"
          class="author-link"
        >
          一站式AI零代码生成解决方案
        </a>
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
  margin: 0;
  color: #666;
  font-size: 14px;
}
</style>
