Api.requireAdminOrRedirect();

const claims = Api.currentClaims();
document.getElementById("who-email").textContent = (claims && claims.email) || "";
document.getElementById("logout-btn").addEventListener("click", () => Api.logout());

/* =====================================================================
 * Change own password
 * ===================================================================== */

const passwordPanel = document.getElementById("password-panel");
const passwordForm = document.getElementById("password-form");
const passwordMsg = document.getElementById("password-msg");

document.getElementById("change-password-btn").addEventListener("click", () => {
  passwordForm.reset();
  passwordMsg.innerHTML = "";
  passwordPanel.classList.remove("hidden");
});
document.getElementById("cancel-password-btn").addEventListener("click", () => passwordPanel.classList.add("hidden"));

passwordForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const currentPassword = document.getElementById("current-password").value;
  const newPassword = document.getElementById("new-password").value;
  if (newPassword !== document.getElementById("confirm-password").value) {
    showMsg(passwordMsg, "The new passwords don't match.", "error");
    return;
  }
  const saveBtn = document.getElementById("save-password-btn");
  saveBtn.disabled = true;
  try {
    // Every other session is signed out server-side; the response is this panel's fresh token pair.
    const tokens = await Api.request("/api/users/" + claims.sub + "/password", {
      method: "POST",
      body: JSON.stringify({ currentPassword, newPassword }),
    });
    Api.setTokens(tokens.accessToken, tokens.refreshToken);
    passwordForm.reset();
    showMsg(passwordMsg, "Password changed. All other sessions were signed out.", "success");
  } catch (err) {
    showMsg(passwordMsg, err.message, "error");
  } finally {
    saveBtn.disabled = false;
  }
});

function showMsg(el, text, type) {
  if (!text) {
    el.innerHTML = "";
    return;
  }
  el.innerHTML = '<div class="msg ' + type + '"></div>';
  el.querySelector(".msg").textContent = text;
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str == null ? "" : str;
  return div.innerHTML;
}

/* =====================================================================
 * Navigation
 * ===================================================================== */

const tabButtons = document.querySelectorAll(".tab-btn");
const sections = {
  overview: document.getElementById("section-overview"),
  species: document.getElementById("section-species"),
  users: document.getElementById("section-users"),
  badges: document.getElementById("section-badges"),
  logs: document.getElementById("section-logs"),
  metrics: document.getElementById("section-metrics"),
};

function goToSection(name) {
  tabButtons.forEach((btn) => btn.classList.toggle("active", btn.dataset.section === name));
  Object.entries(sections).forEach(([key, el]) => el.classList.toggle("hidden", key !== name));

  if (name === "overview") loadOverview();
  if (name === "species") loadSpeciesList(document.getElementById("search-input").value.trim());
  if (name === "users") loadUsersList();
  if (name === "badges") loadBadgesList();
  if (name === "logs") loadLogsList();
  if (name === "metrics") loadMetrics();
}

tabButtons.forEach((btn) => btn.addEventListener("click", () => goToSection(btn.dataset.section)));

document.querySelectorAll("[data-goto]").forEach((el) => {
  el.addEventListener("click", () => goToSection(el.dataset.goto));
});

/* =====================================================================
 * Overview
 * ===================================================================== */

async function loadOverview() {
  document.getElementById("stat-species").textContent = "…";
  document.getElementById("stat-users").textContent = "…";
  document.getElementById("stat-badges").textContent = "…";
  document.getElementById("stat-logs").textContent = "…";
  try {
    const [species, users, badges, logs] = await Promise.all([
      Api.request("/api/species?size=1"),
      Api.request("/api/admin/users"),
      Api.request("/api/badges/catalog"),
      Api.request("/api/bird-logs"),
    ]);
    document.getElementById("stat-species").textContent = species.page.totalElements;
    document.getElementById("stat-users").textContent = users.length;
    document.getElementById("stat-badges").textContent = badges.length;
    document.getElementById("stat-logs").textContent = logs.length;
  } catch (err) {
    showMsg(document.getElementById("global-msg"), err.message, "error");
  }
}

/* =====================================================================
 * Species
 * ===================================================================== */

const listPanel = document.getElementById("list-panel");
const formPanel = document.getElementById("form-panel");
const imagesPanel = document.getElementById("images-panel");

const speciesListEl = document.getElementById("species-list");
const speciesListMsg = document.getElementById("species-list-msg");
const searchInput = document.getElementById("search-input");
const newSpeciesBtn = document.getElementById("new-species-btn");
const cancelFormBtn = document.getElementById("cancel-form-btn");
const formTitle = document.getElementById("form-title");
const formMsg = document.getElementById("form-msg");
const speciesForm = document.getElementById("species-form");
const deleteBtn = document.getElementById("delete-species-btn");

const imagesMsg = document.getElementById("images-msg");
const imageGrid = document.getElementById("image-grid");
const uploadBtn = document.getElementById("upload-btn");
const attachUrlBtn = document.getElementById("attach-url-btn");
const searchCandidatesBtn = document.getElementById("search-candidates-btn");
const candidateGrid = document.getElementById("candidate-grid");
const candidatesMsg = document.getElementById("candidates-msg");

const SPECIES_FIELDS = ["scientificName", "family", "order"];
// Translated fields are edited as separate EN/TR inputs (e.g. "commonName-en") but
// sent/received as a {"en": "...", "tr": "..."} map.
const SPECIES_TRANSLATED_FIELDS = [
  "commonName", "description", "lifespan", "diet", "habitat",
  "sizeDescription", "conservationStatus", "nativeRange",
];

function commonName(species) {
  return (species && species.commonName && (species.commonName.en || species.commonName.tr)) || "";
}

function badgeName(badge) {
  return (badge && badge.name && (badge.name.en || badge.name.tr)) || "";
}

/* Shared species list, cached across the badge form and the user-edit form so each
 * only fetches /api/species once per page load rather than duplicating the request. */
let speciesOptionsCache = null;

async function getSpeciesOptions() {
  if (!speciesOptionsCache) {
    const page = await Api.request("/api/species?size=500");
    speciesOptionsCache = page.content.slice().sort((a, b) => commonName(a).localeCompare(commonName(b)));
  }
  return speciesOptionsCache;
}

function populateSpeciesSelect(selectEl, options) {
  options.forEach((s) => {
    const opt = document.createElement("option");
    opt.value = s.id;
    opt.textContent = commonName(s);
    selectEl.appendChild(opt);
  });
}

function readTranslations(enFieldId, trFieldId) {
  const en = document.getElementById(enFieldId).value.trim();
  const tr = document.getElementById(trFieldId).value.trim();
  const translations = {};
  if (en) translations.en = en;
  if (tr) translations.tr = tr;
  return Object.keys(translations).length ? translations : null;
}

let currentSpeciesId = null;
let debounceTimer = null;

async function loadSpeciesList(search) {
  showMsg(speciesListMsg, "", "");
  speciesListEl.innerHTML = '<li class="muted">Loading…</li>';
  try {
    // size=500: the admin list shows the whole catalog at once rather than paginating,
    // unlike the app's own species-guide endpoint.
    const query = "?size=500" + (search ? "&search=" + encodeURIComponent(search) : "");
    const page = await Api.request("/api/species" + query);
    renderSpeciesList(page.content);
  } catch (err) {
    speciesListEl.innerHTML = "";
    showMsg(speciesListMsg, err.message, "error");
  }
}

function renderSpeciesList(species) {
  if (!species.length) {
    speciesListEl.innerHTML = '<li class="muted">No species found.</li>';
    return;
  }
  speciesListEl.innerHTML = "";
  species.forEach((s) => {
    const thumb = (s.images && s.images[0] && s.images[0].imageUrl) || "";
    const li = document.createElement("li");
    li.className = "species-row";
    li.innerHTML =
      '<img class="species-thumb" src="' + escapeHtml(thumb) + '" />' +
      '<div class="species-info">' +
      '<div class="common">' + escapeHtml(commonName(s)) + "</div>" +
      '<div class="scientific">' + escapeHtml(s.scientificName) + "</div>" +
      "</div>" +
      '<div class="row-actions"><button class="secondary edit-btn">Edit</button></div>';
    // A listener rather than an inline onerror= attribute: the CSP forbids inline handlers.
    li.querySelector(".species-thumb").addEventListener("error", (e) => { e.target.style.visibility = "hidden"; });
    li.querySelector(".edit-btn").addEventListener("click", () => openEditForm(s.id));
    speciesListEl.appendChild(li);
  });
}

searchInput.addEventListener("input", () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => loadSpeciesList(searchInput.value.trim()), 300);
});

function showSpeciesListView() {
  currentSpeciesId = null;
  formPanel.classList.add("hidden");
  imagesPanel.classList.add("hidden");
  listPanel.classList.remove("hidden");
  loadSpeciesList(searchInput.value.trim());
}

function showSpeciesFormView() {
  listPanel.classList.add("hidden");
  formPanel.classList.remove("hidden");
}

function fillSpeciesForm(species) {
  SPECIES_FIELDS.forEach((f) => {
    document.getElementById(f).value = (species && species[f]) || "";
  });
  SPECIES_TRANSLATED_FIELDS.forEach((f) => {
    const translations = (species && species[f]) || {};
    document.getElementById(f + "-en").value = translations.en || "";
    document.getElementById(f + "-tr").value = translations.tr || "";
  });
}

newSpeciesBtn.addEventListener("click", () => {
  currentSpeciesId = null;
  formTitle.textContent = "New species";
  deleteBtn.classList.add("hidden");
  showMsg(formMsg, "", "");
  fillSpeciesForm(null);
  imagesPanel.classList.add("hidden");
  showSpeciesFormView();
});

cancelFormBtn.addEventListener("click", showSpeciesListView);

async function openEditForm(id) {
  showMsg(speciesListMsg, "", "");
  try {
    const species = await Api.request("/api/species/" + id);
    currentSpeciesId = species.id;
    formTitle.textContent = "Edit species";
    deleteBtn.classList.remove("hidden");
    showMsg(formMsg, "", "");
    fillSpeciesForm(species);
    showSpeciesFormView();
    renderImages(species.images || []);
    imagesPanel.classList.remove("hidden");
    candidateGrid.innerHTML = "";
    showMsg(candidatesMsg, "", "");
  } catch (err) {
    showMsg(speciesListMsg, err.message, "error");
  }
}

function readSpeciesFormPayload() {
  const payload = {};
  SPECIES_FIELDS.forEach((f) => {
    const val = document.getElementById(f).value.trim();
    payload[f] = val === "" ? null : val;
  });
  SPECIES_TRANSLATED_FIELDS.forEach((f) => {
    payload[f] = readTranslations(f + "-en", f + "-tr");
  });
  return payload;
}

speciesForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  showMsg(formMsg, "", "");
  const saveBtn = document.getElementById("save-species-btn");
  saveBtn.disabled = true;
  try {
    const payload = readSpeciesFormPayload();
    let species;
    if (currentSpeciesId) {
      species = await Api.request("/api/species/" + currentSpeciesId, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
    } else {
      species = await Api.request("/api/species", {
        method: "POST",
        body: JSON.stringify(payload),
      });
    }
    currentSpeciesId = species.id;
    formTitle.textContent = "Edit species";
    deleteBtn.classList.remove("hidden");
    showMsg(formMsg, "Saved.", "success");
    renderImages(species.images || []);
    imagesPanel.classList.remove("hidden");
  } catch (err) {
    showMsg(formMsg, err.message, "error");
  } finally {
    saveBtn.disabled = false;
  }
});

deleteBtn.addEventListener("click", async () => {
  if (!currentSpeciesId) return;
  if (!confirm("Delete this species and all its reference images? This cannot be undone.")) return;
  try {
    await Api.request("/api/species/" + currentSpeciesId, { method: "DELETE" });
    showSpeciesListView();
  } catch (err) {
    showMsg(formMsg, err.message, "error");
  }
});

function renderImages(images) {
  if (!images.length) {
    imageGrid.innerHTML = '<p class="muted">No reference images yet.</p>';
    return;
  }
  imageGrid.innerHTML = "";
  images.forEach((img) => {
    const card = document.createElement("div");
    card.className = "image-card";
    card.innerHTML =
      '<img src="' + escapeHtml(img.imageUrl) + '" />' +
      '<div class="meta">' +
      '<div class="tags">' + escapeHtml(img.lifeStage) + " · " + escapeHtml(img.gender) + "</div>" +
      (img.caption ? '<div>' + escapeHtml(img.caption) + "</div>" : "") +
      (img.licenseCode
        ? '<div class="muted">License: ' + escapeHtml(img.licenseCode.toUpperCase()) + "</div>"
        : '<div class="muted">Own upload (no third-party license)</div>') +
      '<button class="danger remove-img-btn">Remove</button>' +
      "</div>";
    card.querySelector(".remove-img-btn").addEventListener("click", () => removeImage(img.id));
    imageGrid.appendChild(card);
  });
}

async function refreshImages() {
  const species = await Api.request("/api/species/" + currentSpeciesId);
  renderImages(species.images || []);
}

async function removeImage(imageId) {
  if (!confirm("Remove this image?")) return;
  showMsg(imagesMsg, "", "");
  try {
    await Api.request("/api/species/" + currentSpeciesId + "/images/" + imageId, { method: "DELETE" });
    await refreshImages();
  } catch (err) {
    showMsg(imagesMsg, err.message, "error");
  }
}

async function attachImage(imageUrl) {
  const lifeStage = document.getElementById("upload-lifeStage").value;
  const gender = document.getElementById("upload-gender").value;
  const caption = document.getElementById("upload-caption").value.trim();
  await Api.request("/api/species/" + currentSpeciesId + "/images", {
    method: "POST",
    body: JSON.stringify({ lifeStage, gender, imageUrl, caption: caption || null }),
  });
  await refreshImages();
}

uploadBtn.addEventListener("click", async () => {
  if (!currentSpeciesId) return;
  const fileInput = document.getElementById("upload-file");
  const file = fileInput.files[0];
  if (!file) {
    showMsg(imagesMsg, "Choose a file first.", "error");
    return;
  }
  showMsg(imagesMsg, "", "");
  uploadBtn.disabled = true;
  uploadBtn.textContent = "Uploading…";
  try {
    const formData = new FormData();
    formData.append("file", file);
    const uploadResult = await Api.request("/api/uploads/photo", {
      method: "POST",
      body: formData,
    });
    await attachImage(uploadResult.url);
    fileInput.value = "";
    document.getElementById("upload-caption").value = "";
    showMsg(imagesMsg, "Photo uploaded and attached.", "success");
  } catch (err) {
    showMsg(imagesMsg, err.message, "error");
  } finally {
    uploadBtn.disabled = false;
    uploadBtn.textContent = "Upload & attach";
  }
});

attachUrlBtn.addEventListener("click", async () => {
  if (!currentSpeciesId) return;
  const urlInput = document.getElementById("image-url-input");
  const url = urlInput.value.trim();
  if (!url) {
    showMsg(imagesMsg, "Enter an image URL first.", "error");
    return;
  }
  showMsg(imagesMsg, "", "");
  attachUrlBtn.disabled = true;
  try {
    await attachImage(url);
    urlInput.value = "";
    showMsg(imagesMsg, "Image attached.", "success");
  } catch (err) {
    showMsg(imagesMsg, err.message, "error");
  } finally {
    attachUrlBtn.disabled = false;
  }
});

searchCandidatesBtn.addEventListener("click", async () => {
  if (!currentSpeciesId) return;
  const lifeStage = document.getElementById("candidate-lifeStage").value;
  const gender = document.getElementById("candidate-gender").value;
  showMsg(candidatesMsg, "Searching…", "success");
  candidateGrid.innerHTML = "";
  searchCandidatesBtn.disabled = true;
  try {
    const query = "?lifeStage=" + encodeURIComponent(lifeStage) + "&gender=" + encodeURIComponent(gender);
    const candidates = await Api.request("/api/species/" + currentSpeciesId + "/photo-candidates" + query);
    showMsg(candidatesMsg, "", "");
    renderCandidates(candidates, lifeStage, gender);
  } catch (err) {
    showMsg(candidatesMsg, err.message, "error");
  } finally {
    searchCandidatesBtn.disabled = false;
  }
});

function renderCandidates(candidates, lifeStage, gender) {
  if (!candidates.length) {
    candidateGrid.innerHTML = '<p class="muted">No candidates found.</p>';
    return;
  }
  candidateGrid.innerHTML = "";
  candidates.forEach((c) => {
    const card = document.createElement("div");
    card.className = "candidate-card";
    card.innerHTML =
      '<a href="' + escapeHtml(c.observationUrl) + '" target="_blank" rel="noopener">' +
      '<img src="' + escapeHtml(c.photoUrl) + '" /></a>' +
      '<div class="meta">' + escapeHtml(c.licenseCode || "") + " · " + escapeHtml(c.attribution || "") + "</div>" +
      '<button class="secondary use-btn">Use this photo</button>';
    card.querySelector(".use-btn").addEventListener("click", async (e) => {
      e.target.disabled = true;
      e.target.textContent = "Attaching…";
      try {
        await Api.request("/api/species/" + currentSpeciesId + "/images", {
          method: "POST",
          body: JSON.stringify({
            lifeStage,
            gender,
            imageUrl: c.photoUrl,
            caption: c.attribution || null,
            // Kept so the app can credit the photographer - iNaturalist's CC licenses require it.
            licenseCode: c.licenseCode || null,
            attribution: c.attribution || null,
            sourceUrl: c.observationUrl || null,
          }),
        });
        await refreshImages();
        showMsg(imagesMsg, "Candidate attached.", "success");
      } catch (err) {
        showMsg(candidatesMsg, err.message, "error");
        e.target.disabled = false;
        e.target.textContent = "Use this photo";
      }
    });
    candidateGrid.appendChild(card);
  });
}

/* =====================================================================
 * Users
 * ===================================================================== */

const usersListMsg = document.getElementById("users-list-msg");
const usersTbody = document.getElementById("users-tbody");
const usersSearchInput = document.getElementById("users-search-input");
const usersListPanel = document.getElementById("users-list-panel");
const userFormPanel = document.getElementById("user-form-panel");
const userForm = document.getElementById("user-form");
const userFormMsg = document.getElementById("user-form-msg");
const cancelUserFormBtn = document.getElementById("cancel-user-form-btn");
const deleteUserBtn = document.getElementById("delete-user-btn");
const userFavoriteSpeciesSelect = document.getElementById("user-favoriteSpeciesId");
const userProfilePictureSelect = document.getElementById("user-profilePicture");
let userSpeciesOptionsLoaded = false;
let avatarKeys = null;

async function ensureAvatarOptionsLoaded() {
  if (avatarKeys) return;
  try {
    avatarKeys = (await Api.request("/api/avatars")).map((a) => a.key);
  } catch (err) {
    avatarKeys = [];
    showMsg(userFormMsg, "Failed to load avatar list: " + err.message, "error");
  }
}

// PUT /api/admin/users/{id} replaces profilePicture, so the select must always hold the
// user's current value - including a custom upload, which isn't one of the presets.
function populateProfilePictureSelect(current) {
  userProfilePictureSelect.innerHTML = '<option value="">None</option>';
  avatarKeys.forEach((key) => {
    const opt = document.createElement("option");
    opt.value = key;
    opt.textContent = key;
    userProfilePictureSelect.appendChild(opt);
  });
  if (current && !avatarKeys.includes(current)) {
    const opt = document.createElement("option");
    opt.value = current;
    opt.textContent = "Custom photo (" + current + ")";
    userProfilePictureSelect.appendChild(opt);
  }
  userProfilePictureSelect.value = current || "";
}

function renderProfilePicture(container, value) {
  if (!value) {
    container.textContent = "None";
    return;
  }
  if (value.startsWith("/uploads/")) {
    container.innerHTML = '<img alt="Profile photo" class="profile-photo">';
    container.querySelector("img").src = value;
    return;
  }
  container.textContent = "Preset avatar: " + value;
}

async function ensureUserSpeciesOptionsLoaded() {
  if (userSpeciesOptionsLoaded) return;
  try {
    populateSpeciesSelect(userFavoriteSpeciesSelect, await getSpeciesOptions());
    userSpeciesOptionsLoaded = true;
  } catch (err) {
    showMsg(userFormMsg, "Failed to load species list: " + err.message, "error");
  }
}

const userDetailPanel = document.getElementById("user-detail-panel");
const userDetailTitle = document.getElementById("user-detail-title");
const userDetailMsg = document.getElementById("user-detail-msg");
const userDetailBadgesEl = document.getElementById("user-detail-badges");
const userDetailLogsTbody = document.getElementById("user-detail-logs-tbody");
const cancelUserDetailBtn = document.getElementById("cancel-user-detail-btn");

let allUsers = [];
let currentUserId = null;
let usersDebounceTimer = null;

async function loadUsersList() {
  showMsg(usersListMsg, "", "");
  usersTbody.innerHTML = '<tr><td colspan="6" class="muted">Loading…</td></tr>';
  try {
    allUsers = await Api.request("/api/admin/users");
    renderUsersList(usersSearchInput.value.trim());
  } catch (err) {
    usersTbody.innerHTML = "";
    showMsg(usersListMsg, err.message, "error");
  }
}

function renderUsersList(filterText) {
  const filter = (filterText || "").toLowerCase();
  const filtered = !filter
    ? allUsers
    : allUsers.filter((u) =>
        (u.email || "").toLowerCase().includes(filter) || (u.username || "").toLowerCase().includes(filter)
      );

  if (!filtered.length) {
    usersTbody.innerHTML = '<tr><td colspan="6" class="muted">No users found.</td></tr>';
    return;
  }

  usersTbody.innerHTML = "";
  filtered.forEach((u) => {
    const fullName = [u.firstName, u.lastName].filter(Boolean).join(" ") || "—";
    const roleClass = u.role === "ADMIN" ? "badge-tag admin" : "badge-tag";
    const tr = document.createElement("tr");
    tr.innerHTML =
      "<td>" + escapeHtml(u.email) + "</td>" +
      "<td>" + escapeHtml(u.username) + "</td>" +
      "<td>" + escapeHtml(fullName) + "</td>" +
      '<td><span class="' + roleClass + '">' + escapeHtml(u.role) + "</span></td>" +
      "<td>" + (u.emailVerified ? "Yes" : "No") + "</td>" +
      '<td class="row-actions"><button class="secondary details-user-btn">Details</button>' +
      '<button class="secondary edit-user-btn">Edit</button></td>';
    tr.querySelector(".details-user-btn").addEventListener("click", () => openUserDetail(u));
    tr.querySelector(".edit-user-btn").addEventListener("click", () => openUserForm(u));
    usersTbody.appendChild(tr);
  });
}

usersSearchInput.addEventListener("input", () => {
  clearTimeout(usersDebounceTimer);
  usersDebounceTimer = setTimeout(() => renderUsersList(usersSearchInput.value.trim()), 200);
});

async function openUserForm(user) {
  currentUserId = user.id;
  showMsg(userFormMsg, "", "");
  document.getElementById("user-email").value = user.email;
  document.getElementById("user-firstName").value = user.firstName || "";
  document.getElementById("user-lastName").value = user.lastName || "";
  document.getElementById("user-role").value = user.role;
  document.getElementById("user-emailVerified").value = String(!!user.emailVerified);
  // The server refuses (409) an admin demoting or un-verifying themselves, since it would
  // lock this panel session out on its next request - so don't offer it here either.
  const editingSelf = !!claims && claims.sub === user.id;
  document.getElementById("user-role").disabled = editingSelf;
  document.getElementById("user-emailVerified").disabled = editingSelf;
  await ensureUserSpeciesOptionsLoaded();
  userFavoriteSpeciesSelect.value = user.favoriteSpeciesId || "";
  await ensureAvatarOptionsLoaded();
  populateProfilePictureSelect(user.profilePicture);
  usersListPanel.classList.add("hidden");
  userDetailPanel.classList.add("hidden");
  userFormPanel.classList.remove("hidden");
}

function showUsersListView() {
  currentUserId = null;
  userFormPanel.classList.add("hidden");
  userDetailPanel.classList.add("hidden");
  usersListPanel.classList.remove("hidden");
  loadUsersList();
}

cancelUserFormBtn.addEventListener("click", showUsersListView);
cancelUserDetailBtn.addEventListener("click", showUsersListView);

function formatDate(iso) {
  return iso ? new Date(iso).toLocaleString() : "—";
}

function renderUserDetailBadges(badges) {
  if (!badges.length) {
    userDetailBadgesEl.innerHTML = '<li class="muted">No badges defined yet.</li>';
    return;
  }
  userDetailBadgesEl.innerHTML = "";
  badges.forEach((b) => {
    const li = document.createElement("li");
    li.className = "species-row";
    const status = b.earned
      ? '<span class="badge-tag admin">Earned ' + escapeHtml(formatDate(b.earnedAt)) + "</span>"
      : '<span class="badge-tag">' + b.progress + " / " + b.targetValue + "</span>";
    li.innerHTML =
      '<div class="species-info"><div class="common">' + escapeHtml(b.badgeName) + "</div></div>" +
      '<div class="row-actions">' + status + "</div>";
    userDetailBadgesEl.appendChild(li);
  });
}

function renderUserDetailLogs(logs) {
  if (!logs.length) {
    userDetailLogsTbody.innerHTML = '<tr><td colspan="7" class="muted">No bird logs yet.</td></tr>';
    return;
  }
  userDetailLogsTbody.innerHTML = "";
  logs.forEach((log) => {
    const tr = document.createElement("tr");
    tr.innerHTML =
      "<td>" + escapeHtml(log.speciesCommonName || "Unknown") + "</td>" +
      "<td>" + escapeHtml(log.lifeStage) + "</td>" +
      "<td>" + escapeHtml(log.gender) + "</td>" +
      "<td>" + (log.pet ? "Yes" : "No") + "</td>" +
      "<td>" + escapeHtml(formatDate(log.observedAt)) + "</td>" +
      "<td>" + escapeHtml(formatDate(log.createdAt)) + "</td>" +
      "<td>" + escapeHtml(log.note || "") + "</td>";
    userDetailLogsTbody.appendChild(tr);
  });
}

async function openUserDetail(user) {
  currentUserId = user.id;
  userDetailTitle.textContent = (user.email || "") + " — details";
  showMsg(userDetailMsg, "", "");
  document.getElementById("user-detail-favorite-species").textContent = user.favoriteSpeciesName || "None set";
  renderProfilePicture(document.getElementById("user-detail-profile-picture"), user.profilePicture);
  userDetailBadgesEl.innerHTML = '<li class="muted">Loading…</li>';
  userDetailLogsTbody.innerHTML = '<tr><td colspan="7" class="muted">Loading…</td></tr>';
  usersListPanel.classList.add("hidden");
  userDetailPanel.classList.remove("hidden");
  try {
    const [logs, badges] = await Promise.all([
      Api.request("/api/bird-logs/user/" + user.id),
      Api.request("/api/badges/user/" + user.id),
    ]);
    renderUserDetailLogs(logs);
    renderUserDetailBadges(badges);
  } catch (err) {
    showMsg(userDetailMsg, err.message, "error");
  }
}

userForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!currentUserId) return;
  showMsg(userFormMsg, "", "");
  const saveBtn = document.getElementById("save-user-btn");
  saveBtn.disabled = true;
  try {
    const payload = {
      firstName: document.getElementById("user-firstName").value.trim() || null,
      lastName: document.getElementById("user-lastName").value.trim() || null,
      role: document.getElementById("user-role").value,
      emailVerified: document.getElementById("user-emailVerified").value === "true",
      favoriteSpeciesId: userFavoriteSpeciesSelect.value || null,
      profilePicture: userProfilePictureSelect.value || null,
    };
    await Api.request("/api/admin/users/" + currentUserId, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    showMsg(userFormMsg, "Saved.", "success");
  } catch (err) {
    showMsg(userFormMsg, err.message, "error");
  } finally {
    saveBtn.disabled = false;
  }
});

document.getElementById("send-reset-code-btn").addEventListener("click", async () => {
  if (!currentUserId) return;
  const email = document.getElementById("user-email").value;
  if (!confirm("Email a 6-digit password reset code to " + email + "? It's valid for 15 minutes.")) return;
  try {
    await Api.request("/api/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) });
    showMsg(userFormMsg, "Reset code sent to " + email + " (at most one per minute - a repeat within 60s sends nothing).", "success");
  } catch (err) {
    showMsg(userFormMsg, err.message, "error");
  }
});

deleteUserBtn.addEventListener("click", async () => {
  if (!currentUserId) return;
  if (claims && claims.sub === currentUserId) {
    showMsg(userFormMsg, "You cannot delete your own account.", "error");
    return;
  }
  if (!confirm("Permanently delete this user and ALL of their data - bird logs, badge progress, " +
      "settings, sessions and uploaded photos? They are signed out immediately. This cannot be undone.")) return;
  try {
    await Api.request("/api/admin/users/" + currentUserId, { method: "DELETE" });
    showUsersListView();
  } catch (err) {
    showMsg(userFormMsg, err.message, "error");
  }
});

/* =====================================================================
 * Badges
 * ===================================================================== */

const badgesListMsg = document.getElementById("badges-list-msg");
const badgesListEl = document.getElementById("badges-list");
const badgesListPanel = document.getElementById("badges-list-panel");
const newBadgeBtn = document.getElementById("new-badge-btn");
const badgeFormPanel = document.getElementById("badge-form-panel");
const badgeFormTitle = document.getElementById("badge-form-title");
const badgeFormMsg = document.getElementById("badge-form-msg");
const badgeForm = document.getElementById("badge-form");
const cancelBadgeFormBtn = document.getElementById("cancel-badge-form-btn");
const deleteBadgeBtn = document.getElementById("delete-badge-btn");
const radiusFieldWrap = document.getElementById("radius-field-wrap");
const speciesFieldWrap = document.getElementById("species-field-wrap");
const badgeSpeciesSelect = document.getElementById("badge-speciesId");
const badgeCriteriaTypeSelect = document.getElementById("badge-criteriaType");

let currentBadgeId = null;
let badgeSpeciesOptionsLoaded = false;

const RADIUS_CRITERIA_TYPES = ["SPECIES_IN_RADIUS", "SIGHTINGS_IN_RADIUS"];

function toggleRadiusField() {
  radiusFieldWrap.classList.toggle("hidden", !RADIUS_CRITERIA_TYPES.includes(badgeCriteriaTypeSelect.value));
  speciesFieldWrap.classList.toggle("hidden", badgeCriteriaTypeSelect.value !== "SPECIES_LOGS");
}
badgeCriteriaTypeSelect.addEventListener("change", toggleRadiusField);

async function ensureBadgeSpeciesOptionsLoaded() {
  if (badgeSpeciesOptionsLoaded) return;
  try {
    populateSpeciesSelect(badgeSpeciesSelect, await getSpeciesOptions());
    badgeSpeciesOptionsLoaded = true;
  } catch (err) {
    showMsg(badgeFormMsg, "Failed to load species list: " + err.message, "error");
  }
}

async function loadBadgesList() {
  showMsg(badgesListMsg, "", "");
  badgesListEl.innerHTML = '<li class="muted">Loading…</li>';
  try {
    const badges = await Api.request("/api/badges/catalog");
    renderBadgesList(badges);
  } catch (err) {
    badgesListEl.innerHTML = "";
    showMsg(badgesListMsg, err.message, "error");
  }
}

function renderBadgesList(badges) {
  if (!badges.length) {
    badgesListEl.innerHTML = '<li class="muted">No badges yet.</li>';
    return;
  }
  badgesListEl.innerHTML = "";
  badges.forEach((b) => {
    const li = document.createElement("li");
    li.className = "species-row";
    li.innerHTML =
      '<div class="species-info">' +
      '<div class="common">' + escapeHtml(badgeName(b)) + (b.tier ? " · " + escapeHtml(b.tier) : "") + "</div>" +
      '<div class="scientific">' + escapeHtml(b.criteriaType) + " ≥ " + escapeHtml(String(b.criteriaValue)) + "</div>" +
      "</div>" +
      '<div class="row-actions"><button class="secondary edit-badge-btn">Edit</button></div>';
    li.querySelector(".edit-badge-btn").addEventListener("click", () => openBadgeForm(b));
    badgesListEl.appendChild(li);
  });
}

function showBadgesListView() {
  currentBadgeId = null;
  badgeFormPanel.classList.add("hidden");
  badgesListPanel.classList.remove("hidden");
  loadBadgesList();
}

function showBadgeFormView() {
  badgesListPanel.classList.add("hidden");
  badgeFormPanel.classList.remove("hidden");
}

newBadgeBtn.addEventListener("click", () => {
  currentBadgeId = null;
  badgeFormTitle.textContent = "New badge";
  deleteBadgeBtn.classList.add("hidden");
  showMsg(badgeFormMsg, "", "");
  badgeForm.reset();
  toggleRadiusField();
  ensureBadgeSpeciesOptionsLoaded();
  showBadgeFormView();
});

cancelBadgeFormBtn.addEventListener("click", showBadgesListView);

async function openBadgeForm(badge) {
  currentBadgeId = badge.id;
  badgeFormTitle.textContent = "Edit badge";
  deleteBadgeBtn.classList.remove("hidden");
  showMsg(badgeFormMsg, "", "");
  document.getElementById("badge-name-en").value = (badge.name && badge.name.en) || "";
  document.getElementById("badge-name-tr").value = (badge.name && badge.name.tr) || "";
  document.getElementById("badge-description-en").value = (badge.description && badge.description.en) || "";
  document.getElementById("badge-description-tr").value = (badge.description && badge.description.tr) || "";
  document.getElementById("badge-icon").value = badge.icon || "";
  document.getElementById("badge-tier").value = badge.tier || "";
  document.getElementById("badge-criteriaType").value = badge.criteriaType;
  document.getElementById("badge-criteriaValue").value = badge.criteriaValue;
  document.getElementById("badge-radiusMeters").value =
    (badge.criteriaMetadata && badge.criteriaMetadata.radiusMeters) || "";
  await ensureBadgeSpeciesOptionsLoaded();
  badgeSpeciesSelect.value = (badge.criteriaMetadata && badge.criteriaMetadata.speciesId) || "";
  toggleRadiusField();
  showBadgeFormView();
}

function readBadgeFormPayload() {
  const criteriaType = document.getElementById("badge-criteriaType").value;
  const criteriaValue = parseInt(document.getElementById("badge-criteriaValue").value, 10);
  const tier = document.getElementById("badge-tier").value || null;
  let criteriaMetadata = null;
  if (RADIUS_CRITERIA_TYPES.includes(criteriaType)) {
    const radius = parseFloat(document.getElementById("badge-radiusMeters").value);
    if (!isNaN(radius)) criteriaMetadata = { radiusMeters: radius };
  } else if (criteriaType === "SPECIES_LOGS") {
    const speciesId = badgeSpeciesSelect.value;
    if (speciesId) criteriaMetadata = { speciesId };
  }
  return {
    name: readTranslations("badge-name-en", "badge-name-tr"),
    description: readTranslations("badge-description-en", "badge-description-tr"),
    icon: document.getElementById("badge-icon").value.trim() || null,
    criteriaType,
    criteriaValue,
    criteriaMetadata,
    tier,
  };
}

badgeForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  showMsg(badgeFormMsg, "", "");
  const saveBtn = document.getElementById("save-badge-btn");
  saveBtn.disabled = true;
  try {
    const payload = readBadgeFormPayload();
    if (currentBadgeId) {
      await Api.request("/api/badges/" + currentBadgeId, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
    } else {
      const created = await Api.request("/api/badges", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      currentBadgeId = created.id;
      badgeFormTitle.textContent = "Edit badge";
      deleteBadgeBtn.classList.remove("hidden");
    }
    showMsg(badgeFormMsg, "Saved.", "success");
  } catch (err) {
    showMsg(badgeFormMsg, err.message, "error");
  } finally {
    saveBtn.disabled = false;
  }
});

deleteBadgeBtn.addEventListener("click", async () => {
  if (!currentBadgeId) return;
  if (!confirm("Delete this badge? Any user progress toward it is removed too.")) return;
  try {
    await Api.request("/api/badges/" + currentBadgeId, { method: "DELETE" });
    showBadgesListView();
  } catch (err) {
    showMsg(badgeFormMsg, err.message, "error");
  }
});

/* =====================================================================
 * Logs (all users, admin-only)
 * ===================================================================== */

const logsListMsg = document.getElementById("logs-list-msg");
const logsTbody = document.getElementById("logs-tbody");
const logsSearchInput = document.getElementById("logs-search-input");

let allLogs = [];
let logsUsersById = {};
let logsDebounceTimer = null;

async function loadLogsList() {
  showMsg(logsListMsg, "", "");
  logsTbody.innerHTML = '<tr><td colspan="8" class="muted">Loading…</td></tr>';
  try {
    const [logs, users] = await Promise.all([
      Api.request("/api/bird-logs"),
      Api.request("/api/admin/users"),
    ]);
    allLogs = logs;
    logsUsersById = {};
    users.forEach((u) => { logsUsersById[u.id] = u; });
    renderLogsList(logsSearchInput.value.trim());
  } catch (err) {
    logsTbody.innerHTML = "";
    showMsg(logsListMsg, err.message, "error");
  }
}

function renderLogsList(filterText) {
  const filter = (filterText || "").toLowerCase();
  const filtered = !filter
    ? allLogs
    : allLogs.filter((log) => {
        const user = logsUsersById[log.userId];
        return (
          (user && (user.email || "").toLowerCase().includes(filter)) ||
          (log.speciesCommonName || "").toLowerCase().includes(filter)
        );
      });

  if (!filtered.length) {
    logsTbody.innerHTML = '<tr><td colspan="8" class="muted">No bird logs found.</td></tr>';
    return;
  }

  logsTbody.innerHTML = "";
  filtered.forEach((log) => {
    const user = logsUsersById[log.userId];
    const tr = document.createElement("tr");
    tr.innerHTML =
      "<td>" + escapeHtml((user && user.email) || log.userId) + "</td>" +
      "<td>" + escapeHtml(log.speciesCommonName || "Unknown") + "</td>" +
      "<td>" + escapeHtml(log.lifeStage) + "</td>" +
      "<td>" + escapeHtml(log.gender) + "</td>" +
      "<td>" + (log.pet ? "Yes" : "No") + "</td>" +
      "<td>" + escapeHtml(formatDate(log.observedAt)) + "</td>" +
      "<td>" + escapeHtml(formatDate(log.createdAt)) + "</td>" +
      "<td>" + escapeHtml(log.note || "") + "</td>";
    logsTbody.appendChild(tr);
  });
}

logsSearchInput.addEventListener("input", () => {
  clearTimeout(logsDebounceTimer);
  logsDebounceTimer = setTimeout(() => renderLogsList(logsSearchInput.value.trim()), 200);
});

/* =====================================================================
 * Metrics
 * ===================================================================== */

const metricsMsg = document.getElementById("metrics-msg");
const metricsCalculatedAt = document.getElementById("metrics-calculated-at");
const reloadMetricsBtn = document.getElementById("reload-metrics-btn");
const metricsFavoriteSpeciesEl = document.getElementById("metrics-favorite-species");
const metricsBadgeCompletionsEl = document.getElementById("metrics-badge-completions");
const metricsTopRegionsEl = document.getElementById("metrics-top-regions");
const metricsLocaleUsageEl = document.getElementById("metrics-locale-usage");

// Region labels are "{lat}, {lng}" coordinate-grid cells (~11km per side), computed
// server-side from latitude/longitude - not the free-text locationName field, which
// users can fill with anything ("home", "konum1") and so can't be grouped meaningfully.
const LOCALE_LABELS = { en: "English", tr: "Turkish", unset: "Not set" };

function renderBarChart(container, items, labelFn, valueFn) {
  if (!items.length) {
    container.innerHTML = '<p class="muted">No data yet.</p>';
    return;
  }
  const maxValue = Math.max(...items.map(valueFn), 1);
  container.innerHTML = "";
  items.forEach((item) => {
    const value = valueFn(item);
    const label = labelFn(item);
    const pct = Math.max(Math.round((value / maxValue) * 100), 2);
    const row = document.createElement("div");
    row.className = "bar-row";
    row.innerHTML =
      '<div class="bar-label" title="' + escapeHtml(label) + '">' + escapeHtml(label) + "</div>" +
      '<div class="bar-track"><div class="bar-fill"></div></div>' +
      '<div class="bar-value">' + value + "</div>";
    // Set through the DOM, not a style="" attribute, which the CSP blocks.
    row.querySelector(".bar-fill").style.width = pct + "%";
    container.appendChild(row);
  });
}

async function loadMetrics() {
  showMsg(metricsMsg, "", "");
  metricsCalculatedAt.textContent = "Calculating…";
  try {
    const metrics = await Api.request("/api/admin/metrics");
    metricsCalculatedAt.textContent =
      "Calculated " + formatDate(metrics.calculatedAt) + " · " + metrics.totalUsers + " total users";
    renderBarChart(metricsFavoriteSpeciesEl, metrics.favoriteSpecies,
      (m) => m.speciesName || "Unknown species", (m) => m.userCount);
    renderBarChart(metricsBadgeCompletionsEl, metrics.badgeCompletions,
      (m) => m.badgeName, (m) => m.earnedCount);
    renderBarChart(metricsTopRegionsEl, metrics.topRegions,
      (m) => m.region, (m) => m.logCount);
    renderBarChart(metricsLocaleUsageEl, metrics.localeUsage,
      (m) => LOCALE_LABELS[m.locale] || m.locale.toUpperCase(), (m) => m.userCount);
  } catch (err) {
    metricsCalculatedAt.textContent = "";
    showMsg(metricsMsg, err.message, "error");
  }
}

reloadMetricsBtn.addEventListener("click", loadMetrics);

/* =====================================================================
 * Init
 * ===================================================================== */

goToSection("overview");
