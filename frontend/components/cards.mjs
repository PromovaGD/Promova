import { iconSvg } from "./icons.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function sectionHeading(title, copy) {
  return `
    <div class="section-heading">
      <h2 class="section-title">${escapeHtml(title)}</h2>
      <p class="section-lead">${escapeHtml(copy)}</p>
    </div>
  `;
}

export function cardGrid(items, columns = "three") {
  return `<div class="card-grid ${columns}">${items.join("")}</div>`;
}

export function infoCard(icon, title, copy, className = "info-card") {
  return `
    <article class="${className}">
      <div class="card-icon">${iconSvg(icon)}</div>
      <h3 class="card-title">${escapeHtml(title)}</h3>
      <p class="card-copy">${escapeHtml(copy)}</p>
    </article>
  `;
}

export function stepCard(number, title, copy) {
  return `
    <article class="step-card">
      <div class="step-number">${number}</div>
      <h3 class="card-title">${escapeHtml(title)}</h3>
      <p class="card-copy">${escapeHtml(copy)}</p>
    </article>
  `;
}

export function previewFeedItem(dotClass, title, copy, level) {
  return `
    <div class="preview-item">
      <span class="preview-dot ${dotClass}"></span>
      <div>
        <strong>${escapeHtml(title)}</strong>
        <p>${escapeHtml(copy)}</p>
      </div>
      <span class="tag-pill ${dotClass}">${escapeHtml(level)}</span>
    </div>
  `;
}

export function integrationCard(icon, title, copy) {
  return `
    <article class="integration-card">
      <div class="card-icon">${iconSvg(icon)}</div>
      <div>
        <h3>${escapeHtml(title)}</h3>
        <p>${escapeHtml(copy)}</p>
      </div>
    </article>
  `;
}

export function sourceCard(evidence, status) {
  return `
    <div class="auto-capture-card">
      <div class="auto-capture-icon">${iconSvg("plug")}</div>
      <div>
        <span class="capture-label">Origem sincronizada</span>
        <strong>${escapeHtml(evidence.source)}</strong>
        <p>${escapeHtml(evidence.sourceMeta)}</p>
      </div>
      <span class="sync-status">${escapeHtml(status)}</span>
    </div>
  `;
}
