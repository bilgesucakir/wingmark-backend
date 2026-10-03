/* Login page logic. Kept out of login.html so the Content-Security-Policy can forbid inline scripts. */

if (Api.isAdminLoggedIn()) {
  window.location.href = "index.html";
}

const form = document.getElementById("login-form");
const msg = document.getElementById("msg");
const submitBtn = document.getElementById("submit-btn");

function showError(text) {
  msg.innerHTML = '<div class="msg error"></div>';
  msg.querySelector(".msg").textContent = text;
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  msg.innerHTML = "";
  submitBtn.disabled = true;
  submitBtn.textContent = "Signing in…";
  try {
    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value;
    await Api.login(email, password);
    window.location.href = "index.html";
  } catch (err) {
    showError(err.message || "Login failed.");
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = "Sign in";
  }
});
