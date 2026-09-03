/**
 * HTML 安全转义工具（前端公共 util，Day 89 重构抽取）
 *
 * <p>Industrial AI Hub 所有 AI 自由文本（AI 日报 report、异常描述、诊断摘要、issues、回答、RAG 引文片段、
 * 告警 AI 摘要等）和后端返回的自由文本 error message 一律经本工具转义后再渲染。
 * 配合 Vue `{{ }}` 插值构成「双保险」——即便后续把某处改成 `v-html`（如要支持 markdown），
 * 仍然需要 DOMPurify 白名单，不能直接用未转义字符串。</p>
 *
 * <h3>覆盖字符（5 个，OWASP XSS Prevention Cheat Sheet 最小集）</h3>
 * <ul>
 *   <li>{@code &} → {@code &amp;}</li>
 *   <li>{@code <} → {@code &lt;}</li>
 *   <li>{@code >} → {@code &gt;}</li>
 *   <li>{@code "} → {@code &quot;}</li>
 *   <li>{@code '} → {@code &#39;}</li>
 * </ul>
 *
 * <h3>使用说明</h3>
 * <pre>{@code
 * import { escapeHtml, escapeText, safeJoin } from '@/utils/escapeHtml.js'
 *
 * // 通用：自由文本 → 纯文本（Vue {{ }} 渲染）
 * {{ escapeHtml(aiAnswer) }}
 *
 * // 别名 escapeText 存在是为了兼容 Day 87 之前的 4 个页面旧代码里 function 名
 * // 语义完全相同
 * {{ escapeText(it.description) }}
 *
 * // 把字符串片段数组（无 null 无 undefined）安全 join
 * <p :innerHTML="safeJoin(issues, '<br/>')"></p>
 * }</pre>
 *
 * @author AI 助手 + hula0710
 * @since Day 89（Phase 4 AI 模块重构，消去 4 页面重复定义）
 */

const ENTITY_MAP = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

/**
 * HTML 安全转义。对 null / undefined 返回空串，对其他类型先 String()，然后替换 5 个实体。
 *
 * @param {*} str 待转义的任意值（通常是 string，可能来自 AI 的非受控输出）
 * @returns {string} 转义后的纯文本（可安全放进 {{ }} 或 attribute）
 */
export function escapeHtml(str) {
  if (str == null) return ''
  return String(str).replace(/[&<>"']/g, ch => ENTITY_MAP[ch])
}

/**
 * {@link escapeHtml} 的别名。保留是为了：
 * ① 让 Day 87 4 个 AI 页面里原本写 `{{ escapeText(x) }}` 的模板在"替换 import 即可"的最小改动下工作；
 * ② 语义上提醒开发者「这是文本转义，不是 sanitize（不会移除 tag）」。
 *
 * @param {*} str
 * @returns {string}
 */
export function escapeText(str) {
  return escapeHtml(str)
}

/**
 * 安全地把片段数组使用指定分隔符连接起来：每个片段单独 escapeHtml，再拼分隔符（分隔符不转义，允许 <br/> 类标签）。
 *
 * <p>返回值可以直接用 {@code v-html} 渲染（前提是分隔符是你可控的 tag，比如项目硬编码的 {@code '<br/>'}）。</p>
 *
 * @param {Array<string|number|null|undefined>} parts 片段数组，null/undefined 自动跳过
 * @param {string} separator 分隔符（默认 ', '）
 * @returns {string} 每个片段安全转义后用 separator 连接的字符串
 */
export function safeJoin(parts, separator = ', ') {
  if (!Array.isArray(parts)) return ''
  return parts
    .filter(p => p != null && p !== '')
    .map(p => escapeHtml(p))
    .join(separator)
}

export default escapeHtml
