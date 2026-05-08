export function iconSvg(name) {
  const base =
    'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"';

  switch (name) {
    case "doc":
      return `<svg ${base}><path d="M7 3h7l4 4v14H7z"/><path d="M14 3v5h5"/><path d="M9 12h6"/><path d="M9 16h6"/></svg>`;
    case "shield":
      return `<svg ${base}><path d="M12 3 19 6v5c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V6z"/><path d="M9 12l2 2 4-4"/></svg>`;
    case "users":
      return `<svg ${base}><path d="M17 20v-1c0-1.7-1.3-3-3-3H7c-1.7 0-3 1.3-3 3v1"/><path d="M13 16h4c1.7 0 3 1.3 3 3v1"/><circle cx="9" cy="8" r="3"/><path d="M17 6a3 3 0 1 1 0 6"/></svg>`;
    case "chart":
      return `<svg ${base}><path d="M4 19V5"/><path d="M4 19h16"/><path d="M8 15v-4"/><path d="M12 15V8"/><path d="M16 15v-7"/></svg>`;
    case "flow":
      return `<svg ${base}><circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M8 16c3-3 5-5 8-8"/><path d="M12 6H6v6"/></svg>`;
    case "plug":
      return `<svg ${base}><path d="M9 3v5"/><path d="M15 3v5"/><path d="M8 8h8v4a4 4 0 0 1-4 4h0a4 4 0 0 1-4-4z"/><path d="M12 16v5"/></svg>`;
    case "trend":
      return `<svg ${base}><path d="M4 16 9 11l4 4 7-7"/><path d="M14 8h6v6"/></svg>`;
    case "calendar":
      return `<svg ${base}><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4"/><path d="M16 3v4"/><path d="M3 11h18"/></svg>`;
    case "message":
      return `<svg ${base}><path d="M21 15a3 3 0 0 1-3 3H8l-5 3V6a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3z"/></svg>`;
    case "network":
      return `<svg ${base}><circle cx="5" cy="12" r="2"/><circle cx="12" cy="5" r="2"/><circle cx="19" cy="12" r="2"/><path d="M7 11 10 8"/><path d="M14 8 17 11"/><path d="M7 13 10 16"/><path d="M14 16 17 13"/></svg>`;
    case "github":
      return `<svg ${base}><path d="M9 19c-4 1.2-4-2-5-2"/><path d="M15 19v-3.2c0-1 .4-1.8 1-2.4"/><path d="M7 8c0 4 2 6 5 6s5-2 5-6c0-1-.3-2-.9-2.8.1-.4.2-.9.2-1.4 0-1.2-.5-2.1-1.3-2.8-1.2 0-2.2.4-3 1.2A8.1 8.1 0 0 0 9 2c-.8.7-1.3 1.6-1.3 2.8 0 .5.1 1 .2 1.4C7.3 6 7 7 7 8z"/></svg>`;
    default:
      return `<svg ${base}><circle cx="12" cy="12" r="9"/></svg>`;
  }
}
