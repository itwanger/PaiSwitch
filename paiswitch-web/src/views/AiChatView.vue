<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useProviderStore } from '@/stores/provider'
import type { NaturalLanguageResponse } from '@/types'

const providerStore = useProviderStore()

const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const inputText = ref('')
const loading = ref(false)
const sessionId = ref('')
const messagesContainer = ref<HTMLElement | null>(null)

async function sendMessage() {
  if (!inputText.value.trim() || loading.value) return

  const userMessage = inputText.value.trim()
  messages.value.push({ role: 'user', content: userMessage })
  inputText.value = ''
  loading.value = true

  try {
    const response: NaturalLanguageResponse = await providerStore.naturalLanguageSwitch(
      userMessage,
      sessionId.value || undefined
    )

    sessionId.value = response.sessionId
    messages.value.push({ role: 'assistant', content: response.aiResponse })

    // If switch was triggered, refresh config
    if (response.switchTriggered && response.switchResult?.success) {
      await providerStore.fetchConfig()
    }
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误。请稍后重试。'
    })
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function clearChat() {
  messages.value = []
  sessionId.value = ''
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-2xl font-bold text-gray-900">AI 助手</h2>
      <button
        @click="clearChat"
        class="text-sm text-gray-500 hover:text-gray-700"
      >
        清空对话
      </button>
    </div>

    <div class="bg-white rounded-xl shadow-sm flex flex-col" style="height: calc(100vh - 220px)">
      <!-- Messages -->
      <div
        ref="messagesContainer"
        class="flex-1 overflow-y-auto p-4 space-y-4"
      >
        <div v-if="messages.length === 0" class="text-center py-12 text-gray-500">
          <p class="mb-2">👋 你好！我是 PaiSwitch AI 助手</p>
          <p class="text-sm">你可以说：</p>
          <ul class="text-sm mt-2 space-y-1">
            <li>• "切换到 DeepSeek"</li>
            <li>• "帮我换成智谱 AI"</li>
            <li>• "用 OpenRouter"</li>
          </ul>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="flex"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[70%] px-4 py-2 rounded-lg"
            :class="msg.role === 'user'
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 text-gray-900'"
          >
            {{ msg.content }}
          </div>
        </div>

        <div v-if="loading" class="flex justify-start">
          <div class="bg-gray-100 px-4 py-2 rounded-lg text-gray-500">
            思考中...
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="border-t border-gray-100 p-4">
        <form @submit.prevent="sendMessage" class="flex gap-3">
          <input
            v-model="inputText"
            type="text"
            class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            placeholder="输入消息，如：切换到 DeepSeek"
            :disabled="loading"
          />
          <button
            type="submit"
            :disabled="loading || !inputText.trim()"
            class="px-6 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-50 transition-colors"
          >
            发送
          </button>
        </form>
      </div>
    </div>
  </div>
</template>
