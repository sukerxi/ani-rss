<template>
  <div
      class="flex-center content login-shell">
    <div id="login-page" class="flex-center">
      <div id="form" class="login-card">
        <div class="login-brand">
          <img src="/icon.svg" height="64" width="64" alt="ANI-RSS"/>
        </div>
        <h2 class="title-h2">ANI-RSS</h2>
        <el-form @submit.prevent
                 @keyup.enter="login">
          <el-form-item>
            <el-input v-model.trim="user.username"
                      size="large"
                      placeholder="用户名" autocomplete="username">
              <template #prefix>
                <el-icon class="el-input__icon">
                  <User/>
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input v-model.trim="user.password" show-password
                      size="large"
                      placeholder="密码" autocomplete="current-password">
              <template #prefix>
                <el-icon class="el-input__icon">
                  <Key/>
                </el-icon>
              </template>
            </el-input>
          </el-form-item>
          <div class="login-options">
            <el-checkbox v-model:model-value="rememberThePassword.remember">记住密码</el-checkbox>
          </div>
          <el-button type="primary"
                     size="large"
                     class="login-submit"
                     @click="login"
                     :loading="loading">
            登录
          </el-button>
        </el-form>
      </div>
    </div>
    <div class="footer">
      <el-link type="default"
               href="https://docs.wushuo.top"
               target="_blank">
        ani-rss
      </el-link>
      &nbsp;
      <el-link type="default"
               href="https://github.com/wushuo894/ani-rss"
               target="_blank">
        github
      </el-link>
    </div>
  </div>
</template>

<script setup>
import {onMounted, ref} from "vue";
import * as http from "@/js/http.js";
import {Key} from "@element-plus/icons-vue";
import {ElMessage} from "element-plus";
import {authorization, rememberThePassword} from "@/js/global.js";

let loading = ref(false)

let user = ref({
  username: '',
  password: ''
})

/**
 * 登录
 */
let login = () => {
  let {username, password} = user.value;

  if (!password || !username) {
    ElMessage.error('请输入账号与密码')
    return
  }

  loading.value = true

  http.login(user.value)
      .then(res => {
        // 记住密码
        if (rememberThePassword.value.remember) {
          rememberThePassword.value.username = username
          rememberThePassword.value.password = password
        } else {
          rememberThePassword.value.username = ''
          rememberThePassword.value.password = ''
        }

        authorization.value = res.data
      })
      .finally(() => {
        loading.value = false
      })
}

/**
 * 测试是否处于白名单
 */
let test = () => {
  if (authorization.value) {
    return
  }
  http.testIpWhitelist()
      .then(res => {
        if (res.code === 200) {
          authorization.value = new Date().getTime() + '';
          return
        }
        authorization.value = ''
      })
}

onMounted(() => {
  test()
  let {remember, username, password} = rememberThePassword.value;
  if (remember && username && password) {
    user.value.username = username
    user.value.password = password
  }
})

</script>

<style scoped>
.content {
  width: 100%;
  height: 100%;
  flex-flow: column;
  justify-content: space-between;
  background:
          radial-gradient(720px 480px at 10% 4%, rgba(255, 170, 110, 0.50), transparent 60%),
          radial-gradient(780px 560px at 94% 8%, rgba(255, 110, 160, 0.38), transparent 62%),
          radial-gradient(900px 680px at 50% 112%, rgba(178, 130, 255, 0.26), transparent 60%),
          linear-gradient(165deg, #ffe9da 0%, #ffdce7 52%, #f2e0ff 100%);
}

#login-page {
  flex: 1;
  width: 100%;
  padding: 16px;
}

.login-card {
  width: min(340px, 100%);
  padding: 34px 30px 28px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 20px;
  background-color: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
  box-shadow: 0 28px 60px -24px rgba(214, 92, 100, 0.45);
  box-sizing: border-box;
}

.login-brand {
  text-align: center;
  line-height: 0;
}

.login-brand img {
  width: 64px;
  height: 64px;
  padding: 10px;
  box-sizing: border-box;
  border-radius: 18px;
  background: var(--brand-gradient);
  box-shadow: 0 12px 26px -10px var(--brand-glow);
}

.title-h2 {
  text-align: center;
  margin: 16px 0 24px;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.06em;
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.login-card :deep(.el-input) {
  width: 100%;
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.login-submit {
  width: 100%;
}

.footer {
  margin-bottom: 16px;
}
</style>

<style>
/* 深色登录页（非 scoped：需从 html.dark 祖先命中） */
html.dark .login-shell {
  background:
          radial-gradient(720px 480px at 10% 4%, rgba(255, 150, 90, 0.22), transparent 60%),
          radial-gradient(780px 560px at 94% 8%, rgba(255, 96, 146, 0.20), transparent 62%),
          radial-gradient(900px 680px at 50% 112%, rgba(120, 80, 180, 0.22), transparent 60%),
          linear-gradient(165deg, #2d1c21 0%, #281727 52%, #1d1525 100%);
}

html.dark .login-shell .login-card {
  border-color: rgba(255, 255, 255, 0.09);
  background-color: rgba(40, 28, 36, 0.66);
  box-shadow: 0 28px 60px -24px rgba(0, 0, 0, 0.65);
}
</style>
