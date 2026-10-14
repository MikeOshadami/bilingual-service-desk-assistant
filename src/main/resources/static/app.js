const I18N = {
  en: {
    eyebrow: "Unofficial demo · runs on local models", title: "Service Desk Assistant",
    tabAsk: "Ask a question", tabSubmit: "Submit a request",
    askLabel: "Ask about driver's licences, photo ID or vehicle registration", askButton: "Ask",
    submitLabel: "Paste an email or letter, or upload a document", upload: "Upload PDF, Word or text",
    submitButton: "Process request", working: "Working…", sources: "Sources", grounded: "Cited from sources",
    uncited: "No citations: verify", notFound: "Not found in sources", blocked: "Blocked by input screening",
    caseCreated: "Case created", review: "Needs human review", auto: "Ready for agent", draft: "Draft reply for the agent",
    fields: "Extracted fields", reasons: "Review reasons", needText: "Paste text or choose a file.",
    examples: ["What ID do I need for a new licence?", "I just moved to New Brunswick. How long can I use my old licence?", "How do I register a vehicle?"],
    footer: "Portfolio project. Not affiliated with or endorsed by the Government of New Brunswick. Answers come only from published public pages and may be out of date: always confirm with Service New Brunswick."
  },
  fr: {
    eyebrow: "Démo non officielle · modèles locaux", title: "Assistant du service à la clientèle",
    tabAsk: "Poser une question", tabSubmit: "Soumettre une demande",
    askLabel: "Questions sur le permis de conduire, la carte d'identité ou l'immatriculation", askButton: "Demander",
    submitLabel: "Collez un courriel ou une lettre, ou téléversez un document", upload: "Téléverser un PDF, Word ou texte",
    submitButton: "Traiter la demande", working: "Traitement…", sources: "Sources", grounded: "Cité à partir des sources",
    uncited: "Aucune citation : à vérifier", notFound: "Introuvable dans les sources", blocked: "Bloqué par le filtrage",
    caseCreated: "Dossier créé", review: "Révision humaine requise", auto: "Prêt pour l'agent", draft: "Réponse proposée à l'agent",
    fields: "Champs extraits", reasons: "Motifs de révision", needText: "Collez du texte ou choisissez un fichier.",
    examples: ["Quelles pièces d'identité faut-il pour un permis?", "Je viens d'arriver au N.-B. Combien de temps puis-je utiliser mon ancien permis?", "Comment immatriculer un véhicule?"],
    footer: "Projet de portfolio. Non affilié au gouvernement du Nouveau-Brunswick. Les réponses proviennent uniquement de pages publiques et peuvent être désuètes : confirmez toujours auprès de Service Nouveau-Brunswick."
  }
};

let lang = "en";
const $ = (s) => document.querySelector(s);
const t = (k) => I18N[lang][k];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function applyLang(next) {
  lang = next;
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  document.querySelectorAll("[data-lang]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === lang)));
  $("#examples").innerHTML = t("examples").map((q) => `<button type="button">${esc(q)}</button>`).join("");
  try { localStorage.setItem("lang", lang); } catch (_) { /* storage unavailable */ }
}

document.querySelectorAll("[data-lang]").forEach((b) => b.addEventListener("click", () => applyLang(b.dataset.lang)));
$("#examples").addEventListener("click", (e) => { if (e.target.tagName === "BUTTON") { $("#question").value = e.target.textContent; $("#question").focus(); } });

document.querySelectorAll('[role="tab"]').forEach((tab) => tab.addEventListener("click", () => {
  document.querySelectorAll('[role="tab"]').forEach((x) => x.setAttribute("aria-selected", String(x === tab)));
  document.querySelectorAll('[role="tabpanel"]').forEach((p) => { p.hidden = p.id !== tab.getAttribute("aria-controls"); });
}));

async function call(url, options) {
  const res = await fetch(url, options);
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.detail || body.title || `HTTP ${res.status}`);
  return body;
}

function sourcesHtml(sources) {
  if (!sources?.length) return "";
  return `<h2>${t("sources")}</h2><ol class="sources">${sources.map((s) => `
    <li class="${s.cited ? "cited" : ""}"><strong>[${s.n}] ${esc(s.title)}</strong> · ${esc(s.lang)} · ${s.score}
      ${s.url && s.url.startsWith("http") ? ` · <a href="${esc(s.url)}" target="_blank" rel="noopener">link</a>` : ""}
      <p class="excerpt">${esc(s.excerpt)}</p></li>`).join("")}</ol>`;
}

$("#ask-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const btn = e.submitter; btn.disabled = true; btn.textContent = t("working");
  const out = $("#ask-result");
  try {
    const r = await call("/api/ask", { method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: $("#question").value, lang }) });
    const badge = r.status === "BLOCKED" ? `<span class="badge bad">${t("blocked")}</span>`
      : r.status === "NO_ANSWER" ? `<span class="badge warn">${t("notFound")}</span>`
      : r.grounded ? `<span class="badge ok">${t("grounded")}</span>` : `<span class="badge warn">${t("uncited")}</span>`;
    out.innerHTML = `<div class="card"><div class="badges">${badge}<span class="badge muted">${r.latencyMs} ms</span></div>
      <div class="answer">${esc(r.answer)}</div>${sourcesHtml(r.sources)}</div>`;
  } catch (err) {
    out.innerHTML = `<div class="card error">${esc(err.message)}</div>`;
  } finally { btn.disabled = false; btn.textContent = t("askButton"); }
});

$("#submit-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const out = $("#submit-result");
  const file = $("#request-file").files[0];
  const text = $("#request-text").value.trim();
  if (!file && !text) { out.innerHTML = `<div class="card error">${t("needText")}</div>`; return; }
  const btn = e.submitter; btn.disabled = true; btn.textContent = t("working");
  try {
    let r;
    if (file) {
      const form = new FormData(); form.append("file", file); form.append("channel", "web-upload");
      r = await call("/api/requests", { method: "POST", body: form });
    } else {
      r = await call("/api/requests", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, channel: "web" }) });
    }
    const x = r.extraction;
    const row = (k, v) => `<dt>${esc(k)}</dt><dd>${esc(Array.isArray(v) ? v.join(", ") || "-" : v ?? "-")}</dd>`;
    out.innerHTML = `<div class="card">
      <div class="badges"><span class="badge ok">${t("caseCreated")}: ${esc(r.caseId)}</span>
        <span class="badge muted">${esc(r.queue)} · ${esc(r.priority)}</span>
        ${r.needsHumanReview ? `<span class="badge warn">${t("review")}</span>` : `<span class="badge ok">${t("auto")}</span>`}
        <span class="badge muted">${r.latencyMs} ms</span></div>
      ${r.reviewReasons?.length ? `<h2>${t("reasons")}</h2><ul>${r.reviewReasons.map((x) => `<li>${esc(x)}</li>`).join("")}</ul>` : ""}
      <h2>${t("fields")}</h2><dl class="fields">
        ${row("Type", x.requestType)}${row("Language", x.language)}${row("Name", x.applicantName)}
        ${row("Email", x.email)}${row("Phone", x.phone)}${row("References", x.referenceNumbers)}
        ${row("Question", x.mainQuestion)}${row("Urgency", x.urgency)}${row("Confidence", x.confidence)}${row("Summary", x.summary)}
        ${row("Missing", x.missingInformation)}</dl>
      ${r.draftReply ? `<h2>${t("draft")}</h2><div class="answer">${esc(r.draftReply)}</div>${sourcesHtml(r.draftSources)}` : ""}
    </div>`;
  } catch (err) {
    out.innerHTML = `<div class="card error">${esc(err.message)}</div>`;
  } finally { btn.disabled = false; btn.textContent = t("submitButton"); }
});

let saved = "en";
try { saved = localStorage.getItem("lang") || (navigator.language || "en").slice(0, 2); } catch (_) { /* ignore */ }
applyLang(saved === "fr" ? "fr" : "en");
