<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useProviderStore } from '@/stores/provider'
import type { NaturalLanguageResponse, ConversationMessage, ConversationHistoryResponse } from '@/types'

const providerStore = useProviderStore()

type UiMessage = { role: 'user' | 'assistant'; content: string }

const STORAGE_SESSION_KEY = 'paiswitch_ai_session_id'
const STORAGE_MESSAGES_KEY = 'paiswitch_ai_messages'
const STORAGE_CLEARED_FLAG_KEY = 'paiswitch_ai_cleared'

const messages = ref<UiMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const sessionId = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const quickPrompts = [
  '切换到 DeepSeek',
  '帮我换成智谱 AI',
  '用 OpenRouter'
]

onMounted(async () => {
  const restored = restoreFromLocalStorage()
  const isCleared = localStorage.getItem(STORAGE_CLEARED_FLAG_KEY) === '1'
  if (!restored && !isCleared) {
    await loadLatestConversation()
  }
  await nextTick()
  scrollToBottom()
})

function restoreFromLocalStorage(): boolean {
  try {
    const storedSessionId = localStorage.getItem(STORAGE_SESSION_KEY)
    const storedMessages = localStorage.getItem(STORAGE_MESSAGES_KEY)
    if (!storedMessages) {
      return false
    }
    const parsedMessages = JSON.parse(storedMessages) as UiMessage[]
    if (!Array.isArray(parsedMessages) || parsedMessages.length === 0) {
      return false
    }
    messages.value = parsedMessages
    sessionId.value = storedSessionId || ''
    return true
  } catch {
    return false
  }
}

async function loadLatestConversation() {
  try {
    const history: ConversationHistoryResponse = await providerStore.getLatestConversation()
    sessionId.value = history.sessionId || ''
    messages.value = (history.messages || []).map((msg: ConversationMessage) => ({
      role: msg.role,
      content: msg.content
    }))
    persistChatState()
    if (messages.value.length > 0) {
      localStorage.removeItem(STORAGE_CLEARED_FLAG_KEY)
    }
  } catch {
    // ignore restore errors to keep chat usable
  }
}

function persistChatState() {
  try {
    if (sessionId.value) {
      localStorage.setItem(STORAGE_SESSION_KEY, sessionId.value)
    } else {
      localStorage.removeItem(STORAGE_SESSION_KEY)
    }

    if (messages.value.length > 0) {
      localStorage.setItem(STORAGE_MESSAGES_KEY, JSON.stringify(messages.value))
    } else {
      localStorage.removeItem(STORAGE_MESSAGES_KEY)
    }
  } catch {
    // ignore persistence errors
  }
}

async function sendMessage() {
  if (!inputText.value.trim() || loading.value) return

  localStorage.removeItem(STORAGE_CLEARED_FLAG_KEY)
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
    persistChatState()

    // If switch was triggered, refresh config
    if (response.switchTriggered && response.switchResult?.success) {
      await providerStore.fetchConfig()
    }
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误。请稍后重试。'
    })
    persistChatState()
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

async function sendQuickPrompt(prompt: string) {
  if (loading.value) return
  inputText.value = prompt
  await sendMessage()
}

function scrollToBottom() {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

function clearChat() {
  messages.value = []
  sessionId.value = ''
  persistChatState()
  localStorage.setItem(STORAGE_CLEARED_FLAG_KEY, '1')
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
          <p class="text-sm mb-3">试试快捷指令：</p>
          <div class="flex flex-wrap justify-center gap-2">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              type="button"
              @click="sendQuickPrompt(prompt)"
              :disabled="loading"
              class="px-3 py-1.5 text-sm rounded-full border border-primary-200 text-primary-700 bg-primary-50 hover:bg-primary-100 disabled:opacity-50 transition-colors"
            >
              {{ prompt }}
            </button>
          </div>
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
