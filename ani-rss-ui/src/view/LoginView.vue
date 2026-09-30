<template>
  <div
      class="flex-center content">
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
}

#login-page {
  flex: 1;
  width: 100%;
  padding: 16px;
}

.login-card {
  width: min(320px, 100%);
  padding: 32px 28px 28px;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 16px;
  background-color: var(--el-bg-color);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  box-sizing: border-box;
}

.login-brand {
  text-align: center;
  line-height: 0;
}

.login-brand img {
  border-radius: 14px;
}

.title-h2 {
  text-align: center;
  margin: 14px 0 22px;
  letter-spacing: 0.02em;
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
