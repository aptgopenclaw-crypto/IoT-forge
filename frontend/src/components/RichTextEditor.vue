<script setup lang="ts">
/**
 * 富文本編輯器（Tiptap + StarterKit + Link）
 *
 * 安全性：使用者輸入的 HTML 仍會送到後端由 OWASP HTML Sanitizer 二次清洗，
 * 前端不依賴客戶端 sanitize。本元件僅控制 UI 可選格式。
 */
import { onBeforeUnmount, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const editor = useEditor({
  content: props.modelValue || '',
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3, 4] },
    }),
    Link.configure({
      openOnClick: false,
      autolink: true,
      HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
    }),
  ],
  onUpdate({ editor }) {
    emit('update:modelValue', editor.getHTML())
  },
})

// 外部 v-model 變更時同步內容（如 reset / edit 載入）
watch(
  () => props.modelValue,
  (value) => {
    const ed = editor.value
    if (!ed) return
    if (value !== ed.getHTML()) {
      ed.commands.setContent(value || '', { emitUpdate: false })
    }
  },
)

watch(
  () => props.disabled,
  (value) => {
    editor.value?.setEditable(!value)
  },
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

function toggleBold() { editor.value?.chain().focus().toggleBold().run() }
function toggleItalic() { editor.value?.chain().focus().toggleItalic().run() }
function toggleUnderline() { editor.value?.chain().focus().toggleStrike().run() }
function toggleBulletList() { editor.value?.chain().focus().toggleBulletList().run() }
function toggleOrderedList() { editor.value?.chain().focus().toggleOrderedList().run() }
function toggleBlockquote() { editor.value?.chain().focus().toggleBlockquote().run() }
function setHeading(level: 1 | 2 | 3 | 4) {
  editor.value?.chain().focus().toggleHeading({ level }).run()
}
function setParagraph() { editor.value?.chain().focus().setParagraph().run() }

function applyLink() {
  const ed = editor.value
  if (!ed) return
  const previous = ed.getAttributes('link').href as string | undefined
  const url = window.prompt('輸入連結網址 (留空為移除)', previous ?? 'https://')
  if (url === null) return
  if (url === '') {
    ed.chain().focus().extendMarkRange('link').unsetLink().run()
    return
  }
  ed.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
}

function clearFormat() {
  editor.value?.chain().focus().clearNodes().unsetAllMarks().run()
}
</script>

<template>
  <div class="rich-text-editor" :class="{ 'is-disabled': disabled }">
    <div v-if="editor && !disabled" class="toolbar">
      <button type="button" :class="{ active: editor.isActive('bold') }" @click="toggleBold" title="粗體">
        <strong>B</strong>
      </button>
      <button type="button" :class="{ active: editor.isActive('italic') }" @click="toggleItalic" title="斜體">
        <em>I</em>
      </button>
      <button type="button" :class="{ active: editor.isActive('strike') }" @click="toggleUnderline" title="刪除線">
        <s>S</s>
      </button>
      <span class="sep" />
      <button type="button" :class="{ active: editor.isActive('paragraph') }" @click="setParagraph" title="段落">P</button>
      <button type="button" :class="{ active: editor.isActive('heading', { level: 2 }) }" @click="setHeading(2)" title="H2">H2</button>
      <button type="button" :class="{ active: editor.isActive('heading', { level: 3 }) }" @click="setHeading(3)" title="H3">H3</button>
      <span class="sep" />
      <button type="button" :class="{ active: editor.isActive('bulletList') }" @click="toggleBulletList" title="項目符號">•</button>
      <button type="button" :class="{ active: editor.isActive('orderedList') }" @click="toggleOrderedList" title="編號">1.</button>
      <button type="button" :class="{ active: editor.isActive('blockquote') }" @click="toggleBlockquote" title="引用">”</button>
      <span class="sep" />
      <button type="button" :class="{ active: editor.isActive('link') }" @click="applyLink" title="連結">🔗</button>
      <button type="button" @click="clearFormat" title="清除格式">✕</button>
    </div>
    <editor-content :editor="editor" class="editor-content" />
  </div>
</template>

<style scoped>
.rich-text-editor {
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 6px;
  background: var(--el-fill-color-blank, #fff);
  overflow: hidden;
}

.rich-text-editor.is-disabled {
  background: var(--el-fill-color-light, #f5f7fa);
  opacity: 0.85;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 4px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  background: var(--el-fill-color-light, #fafafa);
}

.toolbar button {
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1;
  color: var(--el-text-color-regular, #606266);
}

.toolbar button:hover {
  background: var(--el-fill-color, #f5f7fa);
}

.toolbar button.active {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
  border-color: var(--el-color-primary-light-7, #c6e2ff);
}

.toolbar .sep {
  width: 1px;
  background: var(--el-border-color-lighter, #ebeef5);
  margin: 0 4px;
}

.editor-content {
  padding: 10px 14px;
  min-height: 200px;
  max-height: 480px;
  overflow-y: auto;
  font-size: 14px;
  line-height: 1.6;
}

:deep(.ProseMirror) {
  outline: none;
  min-height: 180px;
}

:deep(.ProseMirror p) {
  margin: 0 0 8px;
}

:deep(.ProseMirror h1),
:deep(.ProseMirror h2),
:deep(.ProseMirror h3),
:deep(.ProseMirror h4) {
  margin: 12px 0 8px;
  font-weight: 600;
}

:deep(.ProseMirror ul),
:deep(.ProseMirror ol) {
  padding-left: 24px;
  margin: 0 0 8px;
}

:deep(.ProseMirror blockquote) {
  border-left: 3px solid var(--el-color-primary-light-5, #a0cfff);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--el-text-color-secondary, #909399);
}

:deep(.ProseMirror a) {
  color: var(--el-color-primary, #409eff);
  text-decoration: underline;
}
</style>
